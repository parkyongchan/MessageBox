package com.ah.acr.messagebox.util;

/**
 * [S5] 생존 안내 계산 유틸. 내 위치 ↔ 표적 간 거리/방위/도보시간.
 * 순수 계산만 (GPS·UI 독립). 위급 상황용이라 단순·견고하게.
 */
public final class SurvivalNav {

    private SurvivalNav() {}

    private static final double R = 6371000.0; // 지구 반경(m)

    /** 두 지점 간 거리(m). Haversine. */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** 내 위치에서 표적으로의 방위각(0~360°, 정북=0, 시계방향). */
    public static double bearingDegrees(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                - Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLon);
        double brng = Math.toDegrees(Math.atan2(y, x));
        return (brng + 360) % 360;
    }

    /** 방위각 → 8방위 한글 (북/북동/동/…). */
    public static String compass8(double bearing) {
        String[] dirs = {"N (북)", "NE (북동)", "E (동)", "SE (남동)", "S (남)", "SW (남서)", "W (서)", "NW (북서)"};
        int idx = (int) Math.round(bearing / 45.0) % 8;
        return dirs[idx];
    }

    /** 거리(m) → 사람이 읽는 문자열 ("850 m" / "1.2 km"). */
    public static String formatDistance(double meters) {
        if (meters < 1000) return Math.round(meters) + " m";
        return String.format(java.util.Locale.US, "%.1f km", meters / 1000.0);
    }

    /**
     * 도보 시간 추정(분). 기본 보행속도 4km/h.
     * 위급·비정상 지형 감안해 보수적으로. 거리만으로 대략치(경로 아님).
     */
    public static int walkMinutes(double meters) {
        double kmh = 4.0;
        double minutes = (meters / 1000.0) / kmh * 60.0;
        return (int) Math.ceil(minutes);
    }

    /** 도보 시간 → 문자열 ("약 18분" / "약 1시간 20분"). */
    public static String formatWalk(double meters) {
        int min = walkMinutes(meters);
        if (min < 60) return "~" + min + "min (약 " + min + "분)";
        return "~" + (min / 60) + "h" + (min % 60) + "m (약 " + (min / 60) + "시간 " + (min % 60) + "분)";
    }

    /** [S5-nav] 상대 방위: 표적방위 - 내 향한방향. -180~180 (양수=우측, 음수=좌측, 0=정면). */
    public static double relativeBearing(double targetBearing, double heading) {
        double rel = targetBearing - heading;
        while (rel > 180) rel -= 360;
        while (rel < -180) rel += 360;
        return rel;
    }

    /** [S5-nav] 상대방위 → 안내 텍스트. 위급용 간결하게. */
    public static String steerHint(double rel) {
        double a = Math.abs(rel);
        if (a <= 15) return "정면 · 직진 (On course)";
        if (a <= 75) return rel > 0 ? "우측 " + Math.round(a) + "° (turn Right)" : "좌측 " + Math.round(a) + "° (turn Left)";
        if (a <= 135) return rel > 0 ? "우측 크게 (hard Right)" : "좌측 크게 (hard Left)";
        return "반대 방향 · 뒤돌기 (Turn around)";
    }

    /** [S5-nav] 제대로 가는 중? (정면 ±30°). 색 표시용. */
    public static boolean onCourse(double rel) {
        return Math.abs(rel) <= 30;
    }

    /**
     * 선분(내위치→표적)과 점(위험지) 사이 최단 근접거리(m) 근사.
     * 경로 근처 위험(절벽 등) 감지용. 평면 근사(짧은 거리라 충분).
     */
    public static double distancePointToPathMeters(
            double lat1, double lon1, double lat2, double lon2, double plat, double plon) {
        // 위경도를 로컬 평면(m)으로 근사
        double latRef = Math.toRadians((lat1 + lat2) / 2.0);
        double mPerLat = 111320.0;
        double mPerLon = 111320.0 * Math.cos(latRef);
        double ax = lon1 * mPerLon, ay = lat1 * mPerLat;
        double bx = lon2 * mPerLon, by = lat2 * mPerLat;
        double px = plon * mPerLon, py = plat * mPerLat;
        double abx = bx - ax, aby = by - ay;
        double apx = px - ax, apy = py - ay;
        double ab2 = abx * abx + aby * aby;
        double t = ab2 == 0 ? 0 : (apx * abx + apy * aby) / ab2;
        t = Math.max(0, Math.min(1, t));
        double cx = ax + t * abx, cy = ay + t * aby;
        double dx = px - cx, dy = py - cy;
        return Math.sqrt(dx * dx + dy * dy);
    }
}