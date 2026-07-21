package com.ah.acr.messagebox.nav;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * NAV 3단계 - 경로 구간 분할.
 *
 * 경로 지오메트리(폴리라인)를 이동수단(Mode)별 간격으로 누적거리 기준 분할하고,
 * 각 구간 대표 좌표(날씨 확보 지점)를 산출한다.
 *
 * 간격 규칙(기획서 4장):
 *   WALK    = 50 km
 *   VEHICLE = 200 km
 *   VESSEL  = 순항속력(kn) x 1.852 x 24h  (기본 10kn ≈ 444km)
 *
 * HTTP/외부 라이브러리 의존 없음(순수 계산). OSMDroid GeoPoint만 사용.
 */
public final class NavSegmenter {

    /** 이동수단 */
    public static final String MODE_WALK    = "WALK";
    public static final String MODE_VEHICLE = "VEHICLE";
    public static final String MODE_VESSEL  = "VESSEL";

    private static final double WALK_INTERVAL_M    = 50_000d;   // 50 km
    private static final double VEHICLE_INTERVAL_M = 200_000d;  // 200 km
    private static final double KN_TO_KMH          = 1.852d;    // 1노트 = 1.852 km/h

    /** 폭주 방지: 구간(=날씨 호출) 최대 개수 */
    public static final int MAX_SEGMENTS = 60;

    private NavSegmenter() {}

    /** 구간 대표 좌표 1건 */
    public static final class SegPoint {
        public final int seq;               // 0..N
        public final double lat;
        public final double lon;
        public final double distFromStartM; // 출발 기준 누적거리(m)

        public SegPoint(int seq, double lat, double lon, double distFromStartM) {
            this.seq = seq;
            this.lat = lat;
            this.lon = lon;
            this.distFromStartM = distFromStartM;
        }
    }

    /**
     * 모드별 구간 간격(m) 계산.
     * @param mode           WALK/VEHICLE/VESSEL
     * @param cruiseSpeedKn  선박 순항속력(노트). VESSEL이 아니면 무시. null/0이면 기본 10kn.
     */
    public static double intervalMeters(String mode, Double cruiseSpeedKn) {
        if (MODE_WALK.equals(mode))    return WALK_INTERVAL_M;
        if (MODE_VEHICLE.equals(mode)) return VEHICLE_INTERVAL_M;
        if (MODE_VESSEL.equals(mode)) {
            double kn = (cruiseSpeedKn == null || cruiseSpeedKn <= 0) ? 10d : cruiseSpeedKn;
            return kn * KN_TO_KMH * 24d * 1000d; // 속력×24h → m
        }
        return VEHICLE_INTERVAL_M; // 알 수 없는 모드 안전값
    }

    /**
     * 경로 좌표열을 구간 간격 기준으로 분할하여 대표 좌표 목록을 반환한다.
     * seq 0 = 출발지(0m), 이후 interval, 2*interval ... , 마지막에 목적지(총거리 지점)를 포함.
     *
     * @param routePoints    OSRM 지오메트리를 디코드한 좌표열(2점 이상)
     * @param mode           이동수단
     * @param cruiseSpeedKn  선박 순항속력(노트), VESSEL 외엔 null 가능
     */
    public static List<SegPoint> segment(List<GeoPoint> routePoints, String mode, Double cruiseSpeedKn) {
        List<SegPoint> out = new ArrayList<>();
        if (routePoints == null || routePoints.size() < 2) {
            if (routePoints != null && routePoints.size() == 1) {
                GeoPoint p = routePoints.get(0);
                out.add(new SegPoint(0, p.getLatitude(), p.getLongitude(), 0d));
            }
            return out;
        }

        double interval = intervalMeters(mode, cruiseSpeedKn);
        if (interval <= 0) interval = VEHICLE_INTERVAL_M;

        // 1) 각 정점까지의 누적거리 계산
        int n = routePoints.size();
        double[] cum = new double[n];
        cum[0] = 0d;
        for (int i = 1; i < n; i++) {
            cum[i] = cum[i - 1] + haversineM(routePoints.get(i - 1), routePoints.get(i));
        }
        double total = cum[n - 1];

        // 2) seq 0 = 출발지
        int seq = 0;
        GeoPoint start = routePoints.get(0);
        out.add(new SegPoint(seq++, start.getLatitude(), start.getLongitude(), 0d));

        // 3) interval, 2*interval ... 지점 보간
        double target = interval;
        while (target < total && seq < MAX_SEGMENTS) {
            GeoPoint p = interpolateAt(routePoints, cum, target);
            out.add(new SegPoint(seq++, p.getLatitude(), p.getLongitude(), target));
            target += interval;
        }

        // 4) 마지막 대표점 = 목적지(총거리). 직전 대표점과 너무 가까우면 생략(중복 방지).
        if (seq < MAX_SEGMENTS) {
            SegPoint last = out.get(out.size() - 1);
            if (total - last.distFromStartM > interval * 0.05d) { // 5% 이상 남았을 때만 추가
                GeoPoint end = routePoints.get(n - 1);
                out.add(new SegPoint(seq, end.getLatitude(), end.getLongitude(), total));
            }
        }
        return out;
    }

    /**
     * 누적거리 배열 기준으로 targetM 지점의 좌표를 선형 보간.
     */
    private static GeoPoint interpolateAt(List<GeoPoint> pts, double[] cum, double targetM) {
        int n = pts.size();
        if (targetM <= 0) return pts.get(0);
        if (targetM >= cum[n - 1]) return pts.get(n - 1);
        // targetM 이 속한 구간 [i-1, i] 탐색
        int i = 1;
        while (i < n && cum[i] < targetM) i++;
        double d0 = cum[i - 1];
        double d1 = cum[i];
        double f = (d1 - d0) <= 0 ? 0d : (targetM - d0) / (d1 - d0);
        GeoPoint a = pts.get(i - 1);
        GeoPoint b = pts.get(i);
        double lat = a.getLatitude()  + (b.getLatitude()  - a.getLatitude())  * f;
        double lon = a.getLongitude() + (b.getLongitude() - a.getLongitude()) * f;
        return new GeoPoint(lat, lon);
    }

    /** 두 좌표 사이 대권 거리(m) */
    public static double haversineM(GeoPoint a, GeoPoint b) {
        return haversineM(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
    }

    public static double haversineM(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000d; // 지구 반경(m)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double s = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.min(1d, Math.sqrt(s)));
    }

    // ------------------------------------------------------------------
    // OSRM/Google encoded polyline 디코더 (precision 5)
    // 2단계에서 이미 List<GeoPoint>로 디코드해 두었다면 이 메서드는 사용하지 않아도 됨.
    // OSRM 요청 시 geometries=polyline 을 썼다면 이 디코더로 좌표열을 얻는다.
    // (geometries=geojson 을 썼다면 좌표배열을 그대로 GeoPoint 리스트로 변환하면 됨)
    // ------------------------------------------------------------------
    /** List<GeoPoint> → encoded polyline (precision 5). decodePolyline 의 역변환. */
    public static String encodePolyline(List<GeoPoint> pts) {
        StringBuilder sb = new StringBuilder();
        if (pts == null) return sb.toString();
        long prevLat = 0, prevLon = 0;
        for (GeoPoint p : pts) {
            long lat = Math.round(p.getLatitude() * 1e5);
            long lon = Math.round(p.getLongitude() * 1e5);
            encodeSigned(lat - prevLat, sb);
            encodeSigned(lon - prevLon, sb);
            prevLat = lat; prevLon = lon;
        }
        return sb.toString();
    }

    private static void encodeSigned(long v, StringBuilder sb) {
        long shifted = v << 1;
        if (v < 0) shifted = ~shifted;
        while (shifted >= 0x20) {
            sb.append((char) ((int) ((0x20 | (shifted & 0x1f)) + 63)));
            shifted >>= 5;
        }
        sb.append((char) ((int) (shifted + 63)));
    }

    public static List<GeoPoint> decodePolyline(String encoded) {
        List<GeoPoint> poly = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return poly;
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;
        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0; result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            poly.add(new GeoPoint(lat / 1e5, lng / 1e5));
        }
        return poly;
    }
}
