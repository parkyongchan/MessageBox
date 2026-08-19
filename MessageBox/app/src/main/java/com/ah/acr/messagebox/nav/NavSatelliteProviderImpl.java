package com.ah.acr.messagebox.nav;

import android.util.Log;

import com.ah.acr.messagebox.nav.db.NavPlan;
import com.ah.acr.messagebox.nav.db.NavRoute;
import com.ah.acr.messagebox.nav.db.NavSegment;
import com.ah.acr.messagebox.nav.db.NavWeather;
import com.ah.acr.messagebox.nav.db.NavWeatherDay;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * NAV 위성(3순위) 폴백 구현체.
 *
 * 오프라인 + 캐시 부족 시 NavPlanner.run()이 이 구현체를 (백그라운드 스레드에서) 동기 호출한다.
 * 흐름:
 *   1) key 생성 후 latch 등록
 *   2) MO 발사기(Sender)로 ~N: 위성 송신 요청 (memo=key:mode:olat:olon:dlat:dlon[:cruiseKn])
 *   3) latch.await(타임아웃)로 위성 왕복 블로킹 대기
 *   4) MainActivity의 NWX 수신 분기가 onNwxReceived(key, body)를 부르면 latch 해제
 *   5) 저장된 NWX 본문을 파싱해 NavPlan으로 조립 후 반환
 *
 * MT 포맷(서버 NavSessionService 직렬화):
 *   NWX:<sid>|MODE|DUR|DIST|KEY|N|S0:lat,lon,L/M,dist|F7:date,tmax,tmin,rain,wmax,wcode;...|S1:...
 *   (marine 구간은 F7 각 날짜에 7번째 열 wave 추가)
 */
public class NavSatelliteProviderImpl implements NavSatelliteProvider {

    private static final String TAG = "NAV-SAT";

    /** 위성 왕복 최대 대기(ms). 이리듐 왕복 + 서버 OSRM/Open-Meteo 조회 여유. */
    private static final long AWAIT_TIMEOUT_MS = 180_000L; // 3분

    /** ~N: MO를 실제 위성으로 쏘는 발사기. MainActivity가 구현해 주입. */
    public interface Sender {
        /** memo = key:mode:olat:olon:dlat:dlon[:cruiseKn] 를 ~N:1 title로 위성 발사. */
        void sendNavRequest(String key, String memo);
    }

    private final Sender sender;

    // key -> latch (대기 해제용)
    private final ConcurrentHashMap<String, CountDownLatch> latches = new ConcurrentHashMap<>();
    // key -> 수신한 NWX 원문
    private final ConcurrentHashMap<String, String> results = new ConcurrentHashMap<>();

    public NavSatelliteProviderImpl(Sender sender) {
        this.sender = sender;
    }

    @Override
    public NavPlan requestPlanViaSatellite(NavPlanner.RouteInput in) throws Exception {
        // 1) key 생성 (서버가 NWX의 KEY 필드에 그대로 반향해 매칭)
        String key = "nav-" + (System.currentTimeMillis() / 1000L) + "-" + (int)(Math.random() * 1000);

        // 2) memo 조립: key:mode:olat:olon:dlat:dlon[:cruiseKn]
        StringBuilder memo = new StringBuilder();
        memo.append(key).append(":")
            .append(in.mode).append(":")
            .append(fmt(in.originLat)).append(":").append(fmt(in.originLon)).append(":")
            .append(fmt(in.destLat)).append(":").append(fmt(in.destLon));
        if (in.cruiseSpeedKn != null && in.cruiseSpeedKn > 0) {
            memo.append(":").append(fmt(in.cruiseSpeedKn));
        }

        CountDownLatch latch = new CountDownLatch(1);
        latches.put(key, latch);

        Log.i(TAG, "requestPlanViaSatellite: key=" + key + " memo=" + memo);

        // 3) 위성 발사
        if (sender == null) throw new IllegalStateException("NAV 위성 발사기(Sender) 미설정");
        sender.sendNavRequest(key, memo.toString());

        // 4) 위성 왕복 대기
        boolean got = latch.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        latches.remove(key);

        String nwx = results.remove(key);
        if (!got || nwx == null) {
            Log.w(TAG, "위성 응답 타임아웃/누락: key=" + key);
            return null; // NavPlanner가 "위성 응답이 비어 있습니다" 에러 처리
        }

        Log.i(TAG, "위성 응답 수신: key=" + key + " len=" + nwx.length());

        // 5) NWX 파싱 -> NavPlan
        return parseNwx(nwx, in);
    }

    /** MainActivity의 NWX 수신 분기에서 호출. body는 조립본문(NWX:로 시작). */
    public void onNwxReceived(String key, String body) {
        if (key == null) {
            // key를 못 넘기는 경우: 본문에서 KEY 필드 추출 시도
            key = extractKeyFromBody(body);
        }
        if (key == null) {
            Log.w(TAG, "onNwxReceived: key 추출 실패, body=" + safeHead(body));
            return;
        }
        results.put(key, body);
        CountDownLatch latch = latches.get(key);
        if (latch != null) {
            latch.countDown();
            Log.i(TAG, "onNwxReceived: latch 해제 key=" + key);
        } else {
            Log.w(TAG, "onNwxReceived: 대기 중 latch 없음(타임아웃 후 도착?) key=" + key);
        }
    }

    // ---- NWX 파싱 ----

    private NavPlan parseNwx(String body, NavPlanner.RouteInput in) {
        // NWX:<sid>|MODE|DUR|DIST|KEY|N|S0:...|F7:...|S1:...|F7:...
        String[] parts = body.split("\\|");
        if (parts.length < 6) {
            Log.w(TAG, "NWX 필드 부족: " + safeHead(body));
            return null;
        }

        NavPlan plan = new NavPlan();
        NavRoute route = new NavRoute();
        route.mode = in.mode;
        route.originLat = in.originLat; route.originLon = in.originLon;
        route.destLat = in.destLat;     route.destLon = in.destLon;
        route.destName = in.destName;
        route.cruiseSpeed = in.cruiseSpeedKn;
        route.geometry = in.geometry;
        route.sourceApi = "satellite";
        route.fetchedAt = System.currentTimeMillis();
        route.status = "PLANNED";

        // parts[0]="NWX:<sid>", [1]=MODE, [2]=DUR, [3]=DIST, [4]=KEY, [5]=N
        try { route.totalDurationS = Long.parseLong(parts[2].trim()); } catch (Exception ignore) {}
        try { route.totalDistanceM = Double.parseDouble(parts[3].trim()); } catch (Exception ignore) {}
        plan.route = route;

        boolean marineDefault = "VESSEL".equals(in.mode);

        // 나머지 S<i>: / F7: 를 순서대로 처리. S가 나오면 새 segment 시작, 직후 F7이 그 segment 날씨.
        NavPlan.SegmentBundle cur = null;
        int seqCounter = 0;
        for (int i = 6; i < parts.length; i++) {
            String p = parts[i];
            int c = p.indexOf(':');
            if (c <= 0) continue;
            String tag = p.substring(0, c);
            String val = p.substring(c + 1);

            if (tag.startsWith("S")) {
                // S<i>:lat,lon,L/M,dist
                cur = new NavPlan.SegmentBundle();
                NavSegment seg = new NavSegment();
                String[] f = val.split(",");
                seg.seq = seqCounter++;
                if (f.length >= 1) seg.repLat = parseD(f[0]);
                if (f.length >= 2) seg.repLon = parseD(f[1]);
                int marineFlag = marineDefault ? 1 : 0;
                if (f.length >= 3) marineFlag = "M".equalsIgnoreCase(f[2].trim()) ? 1 : 0;
                if (f.length >= 4) seg.distFromStartM = parseD(f[3]);
                seg.etaOffsetS = (route.totalDistanceM > 0)
                        ? Math.round(route.totalDurationS * (seg.distFromStartM / route.totalDistanceM))
                        : 0L;
                seg.label = labelFor(seg.distFromStartM);
                cur.segment = seg;

                NavWeather w = new NavWeather();
                w.marine = marineFlag;
                w.fetchedAt = System.currentTimeMillis();
                cur.weather = w;
                cur.weatherDays = new ArrayList<>();
                plan.segments.add(cur);
            } else if ("F7".equals(tag) && cur != null) {
                // date,tmax,tmin,rain,wmax,wcode[,wave];date,...
                List<NavWeatherDay> days = new ArrayList<>();
                StringBuilder jsonDaily = new StringBuilder();
                for (String dseg : val.split(";")) {
                    String[] f = dseg.split(",");
                    if (f.length < 5) continue;
                    NavWeatherDay d = new NavWeatherDay();
                    d.date = f[0];
                    d.tMax = parseD(f[1]);
                    d.tMin = parseD(f[2]);
                    d.precipMm = parseD(f[3]);
                    d.windMax = parseD(f[4]);
                    if (f.length >= 6) { try { d.weatherCode = (int) parseD(f[5]); } catch (Exception ig) {} }
                    if (f.length >= 7) d.waveMax = parseD(f[6]); // marine wave
                    days.add(d);
                    if (jsonDaily.length() > 0) jsonDaily.append(";");
                    jsonDaily.append(dseg);
                }
                cur.weatherDays = days;
                if (cur.weather != null) cur.weather.jsonDaily = "F7:" + jsonDaily; // 캐시 재파싱 호환용
            }
        }

        if (plan.segments.isEmpty()) {
            Log.w(TAG, "NWX 파싱: 구간 0개");
            return null;
        }
        Log.i(TAG, "NWX 파싱 완료: segments=" + plan.segments.size());
        return plan;
    }

    private static String extractKeyFromBody(String body) {
        if (body == null) return null;
        String[] parts = body.split("\\|");
        // KEY는 parts[4]
        if (parts.length >= 5) {
            String k = parts[4].trim();
            if (!k.isEmpty()) return k;
        }
        return null;
    }

    // ---- helpers ----
    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.6f", v);
    }
    private static double parseD(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }
    private static String labelFor(double distFromStartM) {
        if (distFromStartM <= 0) return "Start";
        long km = Math.round(distFromStartM / 1000d);
        return km + " km";
    }
    private static String safeHead(String s) {
        if (s == null) return "null";
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }
}