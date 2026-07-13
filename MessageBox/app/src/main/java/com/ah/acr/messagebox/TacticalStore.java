package com.ah.acr.messagebox;

import java.util.ArrayList;
import java.util.List;

/**
 * 수신한 전술 데이터 보관소 (메모리).
 * 메인 지도(MapTabFragment)가 읽어서 마커/라인/메저를 표시한다.
 *
 * 발신자 구분: 프로토콜 5.3 — 마커는 "발신자IMEI + id"로 관리.
 * 빈 FROM = 관제센터/서버발 (v1.4).
 *
 * 트랙: 같은 식별자(발신자+type+unit+id)의 마커가 여러 번 수신되면
 *       위치 변화를 선으로 연결 (수신측 렌더링, 프로토콜 4.5).
 */
public class TacticalStore {

    // 수신한 전술 항목 1건 (한 번의 ~L:G: 전송 = 1건)
    public static class Entry {
        public String codeNum;                 // 수신 경로(발신 연락처 코드)
        public TacticalParser.TacticalData data;
        public long recvAt;                    // 수신 시각(ms)
        public String payload;                 // 원본 payload (상세 화면용)
    }

    private static final List<Entry> sEntries = new ArrayList<>();

    public static synchronized void add(String codeNum, TacticalParser.TacticalData data) {
        add(codeNum, data, null);
    }
    public static synchronized void add(String codeNum, TacticalParser.TacticalData data, String payload) {
        if (data != null && data.sessionId >= 0) {
            java.util.Iterator<Entry> it = sEntries.iterator();
            while (it.hasNext()) { Entry prev = it.next(); if (prev.data != null && prev.data.sessionId == data.sessionId) it.remove(); }
        }
        Entry e = new Entry();
        e.codeNum = codeNum;
        e.data = data;
        e.payload = payload;
        e.recvAt = System.currentTimeMillis();
        sEntries.add(e);
    }

    /** [SID] 같은 세션(SID)의 생존 표적 Entry는 최신 1건만 남김 (재전송 중복 제거). */
    private static void dedupBySession() {
        java.util.HashMap<Long, Entry> latest = new java.util.HashMap<>();
        for (Entry e : sEntries) {
            if (e.data != null && e.data.sessionId >= 0) {
                Entry prev = latest.get(e.data.sessionId);
                if (prev == null || e.recvAt >= prev.recvAt) latest.put(e.data.sessionId, e);
            }
        }
        java.util.Iterator<Entry> it = sEntries.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.data != null && e.data.sessionId >= 0 && latest.get(e.data.sessionId) != e) it.remove();
        }
    }

    public static synchronized List<Entry> getAll() {
        return new ArrayList<>(sEntries);
    }

    public static synchronized void clear() {
        sEntries.clear();
    }

    // ── DB 연동 (영속화) ──────────────────────────────
    public static void addAndPersist(android.content.Context ctx, String codeNum,
                                     String payload, boolean isSend) {
        try {
            com.ah.acr.messagebox.database.TacticalRecvEntity ent =
                    new com.ah.acr.messagebox.database.TacticalRecvEntity();
            ent.setCodeNum(codeNum);
            ent.setPayload(payload);
            ent.setSend(isSend);
            ent.setRecvAt(System.currentTimeMillis());
            TacticalParser.TacticalData td = TacticalParser.parse(payload);
            ent.setFromImei(td.fromImei);
            com.ah.acr.messagebox.database.MsgRoomDatabase.Companion
                    .getDatabase(ctx).tacticalRecvDao().insert(ent);
            add(codeNum, td, payload);
        } catch (Exception e) {
            android.util.Log.e("TACTICAL-STORE", "DB 저장 실패", e);
        }
    }

    public static synchronized void loadFromDb(android.content.Context ctx) {
        try {
            java.util.List<com.ah.acr.messagebox.database.TacticalRecvEntity> rows =
                    com.ah.acr.messagebox.database.MsgRoomDatabase.Companion
                            .getDatabase(ctx).tacticalRecvDao().getAllSync();
            sEntries.clear();
            for (com.ah.acr.messagebox.database.TacticalRecvEntity r : rows) {
                TacticalParser.TacticalData td = TacticalParser.parse(r.getPayload());
                Entry e = new Entry();
                e.codeNum = r.getCodeNum();
                e.data = td;
                e.payload = r.getPayload();
                e.recvAt = r.getRecvAt();
                sEntries.add(e);
            }
            dedupBySession();
            android.util.Log.d("TACTICAL-STORE", "DB 로드: " + sEntries.size() + "건");
        } catch (Exception e) {
            android.util.Log.e("TACTICAL-STORE", "DB 로드 실패", e);
        }
    }

    public static void clearAll(android.content.Context ctx) {
        try {
            com.ah.acr.messagebox.database.MsgRoomDatabase.Companion
                    .getDatabase(ctx).tacticalRecvDao().deleteAll();
        } catch (Exception e) {
            android.util.Log.e("TACTICAL-STORE", "DB 삭제 실패", e);
        }
        clear();
    }

    public static synchronized int size() {
        return sEntries.size();
    }

    /**
     * 모든 수신 마커를 평탄화 (지도 표시용).
     * 발신자별 구분 키 = (fromImei) + 식별자.
     */
    public static synchronized List<FlatMarker> getAllMarkers() {
        List<FlatMarker> out = new ArrayList<>();
        for (Entry e : sEntries) {
            if (e.data == null) continue;
            for (TacticalParser.TMarker m : e.data.markers) {
                FlatMarker fm = new FlatMarker();
                fm.fromImei = e.data.fromImei;
                fm.codeNum = e.codeNum;
                fm.id = m.id;
                fm.type = m.type;
                fm.unit = m.unit;
                fm.place = m.place;
                fm.lat = m.lat;
                fm.lon = m.lon;
                fm.ts = e.data.ts;
                fm.recvAt = e.recvAt;
                fm.note = e.data.note;
                fm.ident = TacticalParser.makeIdentifier(m.type, m.unit, m.place, m.id);
                out.add(fm);
            }
        }
        return out;
    }

    public static class FlatMarker {
        public String fromImei;   // 빈 값 = 관제센터발
        public String codeNum;
        public int id, type, unit, place;
        public double lat, lon;
        public long ts, recvAt;
        public String note;
        public String ident;      // HD1 등
    }
}