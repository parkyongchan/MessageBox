package com.ah.acr.messagebox;

import java.util.ArrayList;
import java.util.List;

/**
 * 전술 데이터 파서 (Tactical Data Protocol v1.4)
 * 페이로드(FROM/TS/M/L/R/N)를 파싱하여 구조체로 변환.
 * 직렬화(TacticalMapActivity.serializeTactical)의 역방향.
 */
public class TacticalParser {

    // 파싱된 전술 데이터 묶음
    public static class TacticalData {
        public String fromImei = "";   // 빈 값이면 관제센터/서버발 (v1.4)
        public long ts = 0;            // Unix epoch 초
        public List<TMarker> markers = new ArrayList<>();
        public List<TLine> lines = new ArrayList<>();
        public List<TMeasure> measures = new ArrayList<>();
        public String note = "";       // N: 메모 (지시사항)
    }

    public static class TMarker {
        public int id, type, unit, place;
        public double lat, lon;
        public String cat = "M";          // "M"=전술, "S"=생존
        public int survType = -1;
        public int survDisaster = -1;
        public boolean auto = false;   // MSA(서버 자동 표적)=true
    }

    public static class TLine {
        public int id;
        public List<double[]> points = new ArrayList<>();   // [lat, lon]
    }

    public static class TMeasure {
        public int id;
        public List<double[]> points = new ArrayList<>();   // 2점 [lat, lon]
    }

    /**
     * 페이로드 파싱.
     * 형식: FROM:..;TS:..;M:..;L:..;R:..;N:메모(맨 마지막, 이후 끝까지 전부 메모)
     */
    public static TacticalData parse(String payload) {
        TacticalData out = new TacticalData();
        if (payload == null || payload.isEmpty()) return out;

        // N: 메모는 맨 마지막 — N: 위치를 먼저 찾아 분리 (이후 ; 로 안 쪼갬)
        String body = payload;
        int nIdx = findNoteIndex(payload);
        if (nIdx >= 0) {
            out.note = payload.substring(nIdx + 2);   // "N:" 다음부터 끝까지
            body = payload.substring(0, nIdx);
            // N: 앞의 세미콜론 제거
            if (body.endsWith(";")) body = body.substring(0, body.length() - 1);
        }

        String[] segs = body.split(";");
        for (String seg : segs) {
            if (seg == null || seg.isEmpty()) continue;
            try {
                if (seg.startsWith("FROM:")) {
                    out.fromImei = seg.substring(5).trim();
                } else if (seg.startsWith("TS:")) {
                    out.ts = Long.parseLong(seg.substring(3).trim());
                } else if (seg.startsWith("MSA:")) {
                    parseSurvival(seg.substring(4), out, true);
                } else if (seg.startsWith("MS:")) {
                    parseSurvival(seg.substring(3), out, false);
                } else if (seg.startsWith("M:")) {
                    parseMarker(seg.substring(2), out);
                } else if (seg.startsWith("L:")) {
                    parseLine(seg.substring(2), out);
                } else if (seg.startsWith("R:")) {
                    parseMeasure(seg.substring(2), out);
                }
            } catch (Exception ignore) {
                // 잘못된 세그먼트는 건너뜀
            }
        }
        return out;
    }

    // N: 항목의 시작 인덱스 (";N:" 또는 맨 앞 "N:")
    private static int findNoteIndex(String s) {
        if (s.startsWith("N:")) return 0;
        int i = s.indexOf(";N:");
        return i >= 0 ? i + 1 : -1;   // ";N:"의 N 위치
    }

    // M:id,type,unit,place,lat,lon
    private static void parseMarker(String s, TacticalData out) {
        String[] p = s.split(",");
        if (p.length < 6) return;
        TMarker m = new TMarker();
        m.id = Integer.parseInt(p[0].trim());
        m.type = Integer.parseInt(p[1].trim());
        m.unit = Integer.parseInt(p[2].trim());
        m.place = Integer.parseInt(p[3].trim());
        m.lat = Double.parseDouble(p[4].trim());
        m.lon = Double.parseDouble(p[5].trim());
        out.markers.add(m);
    }

    // MS:id,survType,survDisaster,lat,lon
    private static void parseSurvival(String s, TacticalData out, boolean auto) {
        String[] p = s.split(",");
        if (p.length < 5) return;
        TMarker m = new TMarker();
        m.cat = "S";
        m.auto = auto;
        m.id = Integer.parseInt(p[0].trim());
        m.survType = Integer.parseInt(p[1].trim());
        m.survDisaster = Integer.parseInt(p[2].trim());
        m.lat = Double.parseDouble(p[3].trim());
        m.lon = Double.parseDouble(p[4].trim());
        out.markers.add(m);
    }

    // L:id,n,lat1,lon1|lat2,lon2|...
    private static void parseLine(String s, TacticalData out) {
        // 첫 콤마 두 개: id, n, 그 다음부터 좌표쌍(| 구분)
        int c1 = s.indexOf(',');
        if (c1 < 0) return;
        int c2 = s.indexOf(',', c1 + 1);
        if (c2 < 0) return;
        TLine ln = new TLine();
        ln.id = Integer.parseInt(s.substring(0, c1).trim());
        // n = s.substring(c1+1, c2) — 점 개수(검증용, 생략 가능)
        String coordsPart = s.substring(c2 + 1);
        for (String pair : coordsPart.split("\\|")) {
            String[] ll = pair.split(",");
            if (ll.length >= 2) {
                ln.points.add(new double[]{ Double.parseDouble(ll[0].trim()), Double.parseDouble(ll[1].trim()) });
            }
        }
        if (!ln.points.isEmpty()) out.lines.add(ln);
    }

    // R:id,lat1,lon1|lat2,lon2
    private static void parseMeasure(String s, TacticalData out) {
        int c1 = s.indexOf(',');
        if (c1 < 0) return;
        TMeasure ms = new TMeasure();
        ms.id = Integer.parseInt(s.substring(0, c1).trim());
        String coordsPart = s.substring(c1 + 1);
        for (String pair : coordsPart.split("\\|")) {
            String[] ll = pair.split(",");
            if (ll.length >= 2) {
                ms.points.add(new double[]{ Double.parseDouble(ll[0].trim()), Double.parseDouble(ll[1].trim()) });
            }
        }
        if (ms.points.size() >= 2) out.measures.add(ms);
    }

    /**
     * 식별자 생성 (숫자 → 글자). 예: type=0,unit=3 → "HD" + id
     * 소속: 0=H 1=F 2=U 3=N 4=P 5=E 6=T
     * 병종: 0=I 1=A 2=R 3=D 4=S 5=C 6=Q 7=M (없음=X)
     * 지점: 0=B 1=G 2=H 3=K (없음=X)
     */
    public static String makeIdentifier(int type, int unit, int place, int id) {
        char[] AFFIL = { 'H', 'F', 'U', 'N', 'P', 'E', 'T' };
        char[] UNIT = { 'I', 'A', 'R', 'D', 'S', 'C', 'Q', 'M' };
        char[] PLACE = { 'B', 'G', 'H', 'K' };
        char a = (type >= 0 && type < AFFIL.length) ? AFFIL[type] : 'X';
        char b;
        if (type == 4) {   // POI → place
            b = (place >= 0 && place < PLACE.length) ? PLACE[place] : 'X';
        } else {
            b = (unit >= 0 && unit < UNIT.length) ? UNIT[unit] : 'X';
        }
        return "" + a + b + id;
    }
}