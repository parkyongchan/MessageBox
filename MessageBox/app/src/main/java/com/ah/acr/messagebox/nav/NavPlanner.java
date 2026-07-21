package com.ah.acr.messagebox.nav;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.ah.acr.messagebox.nav.db.NavDao;
import com.ah.acr.messagebox.nav.db.NavPlan;
import com.ah.acr.messagebox.nav.db.NavRoute;
import com.ah.acr.messagebox.nav.db.NavSegment;
import com.ah.acr.messagebox.nav.db.NavWeather;
import com.ah.acr.messagebox.nav.db.NavWeatherDay;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NAV 3~4단계 오케스트레이터.
 *
 * 3단계: 구간 분할 → 각 대표좌표 7일 날씨(순차+지연) → Room DB 저장.
 * 4단계: (1) 구간별로 API 호출 전 캐시(근접좌표+최신성) 우선 재사용,
 *        (2) 온라인/오프라인 판정 → 오프라인이면 위성(3순위) 폴백.
 *
 * 데이터 확보 3단계 폴백(기획서 5.1):
 *   1순위 온라인(Open-Meteo 직접) → 2순위 캐시(Room DB) → 3순위 위성(NavSatelliteProvider)
 */
public class NavPlanner {

    private static final String TAG = "NAV-PLAN";

    /** Open-Meteo 호출 간 지연(ms). 공개 서버 예의상 순차+지연. */
    private static final long THROTTLE_MS = 700L;

    /** 캐시 수명(ms). 이 시간 이내 확보분은 재사용(기획서 5.3: 6~12h). */
    private static final long CACHE_TTL_MS = 6L * 60L * 60L * 1000L; // 6시간

    /** 근접좌표 재사용 반경(도). 약 0.03° ≈ 3.3km (기획서 5.3: 2~5km). */
    private static final double CACHE_RADIUS_DEG = 0.03d;

    public interface Callback {
        /** done/total = 날씨 확보(캐시 재사용 포함) 완료 구간 수 / 전체 구간 수 */
        void onProgress(int done, int total);
        void onComplete(long routeId, int segmentCount, int weatherOkCount);
        void onError(String message);
    }

    /** 경로 계산 결과 입력값 */
    public static class RouteInput {
        public String mode;                 // WALK/VEHICLE/VESSEL
        public List<GeoPoint> points;       // 디코드된 경로 좌표열(오프라인 위성 시 비어있을 수 있음)
        public double totalDistanceM;
        public long   totalDurationS;
        public double originLat, originLon;
        public double destLat, destLon;
        public String destName;             // 선택
        public String geometry;             // 원본 encoded polyline(선택, 저장용)
        public String sourceApi;            // "osrm"/"ors"/"straight"
        public Double cruiseSpeedKn;        // VESSEL 순항속력(선택)
    }

    private final Context appCtx;
    private final NavDao dao;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable private NavSatelliteProvider satelliteProvider; // 미설정이면 오프라인 시 오류

    public NavPlanner(Context appContext, NavDao dao) {
        this.appCtx = appContext != null ? appContext.getApplicationContext() : null;
        this.dao = dao;
    }

    /** 위성(3순위) 폴백 공급자 설정. 앱 위성 송수신 코드에 연결한 구현체를 넣는다. */
    public void setSatelliteProvider(@Nullable NavSatelliteProvider provider) {
        this.satelliteProvider = provider;
    }

    /** 비동기 실행. 콜백은 메인스레드에서 호출된다. */
    public void run(final RouteInput in, final Callback cb) {
        io.execute(() -> {
            try {
                boolean online = NavConnectivity.isOnline(appCtx);
                if (!online) {
                    // 1순위 불가 → 위성(3순위). (2순위 '저장된 경로 재사용'은 5단계 안내화면에서)
                    if (satelliteProvider != null) {
                        Log.i(TAG, "offline → satellite fallback");
                        NavPlan plan = satelliteProvider.requestPlanViaSatellite(in);
                        if (plan == null || plan.route == null) {
                            postError(cb, "위성 응답이 비어 있습니다.");
                            return;
                        }
                        long rid = dao.saveWholePlan(plan);
                        postComplete(cb, rid, plan.segments.size(), countWeather(plan));
                        return;
                    }
                    postError(cb, "오프라인 상태입니다. (위성 폴백 미설정 — 온라인에서 다시 시도하세요)");
                    return;
                }

                // ---- 온라인 경로 ----
                runOnline(in, cb);

            } catch (Exception e) {
                Log.e(TAG, "plan fail: " + e.getMessage(), e);
                postError(cb, "경로/날씨 처리 실패: " + e.getMessage());
            }
        });
    }

    private void runOnline(RouteInput in, Callback cb) {
        // 1) 구간 분할
        List<NavSegmenter.SegPoint> segPoints =
                NavSegmenter.segment(in.points, in.mode, in.cruiseSpeedKn);
        final int total = segPoints.size();
        if (total == 0) {
            postError(cb, "구간을 만들 수 없습니다(경로 좌표 부족).");
            return;
        }

        // 2) route 헤더
        NavPlan plan = new NavPlan();
        NavRoute route = new NavRoute();
        route.mode = in.mode;
        route.originLat = in.originLat;  route.originLon = in.originLon;
        route.destLat = in.destLat;      route.destLon = in.destLon;
        route.destName = in.destName;
        route.totalDistanceM = in.totalDistanceM;
        route.totalDurationS = in.totalDurationS;
        route.cruiseSpeed = in.cruiseSpeedKn;
        route.geometry = in.geometry;
        route.sourceApi = in.sourceApi;
        route.fetchedAt = System.currentTimeMillis();
        route.status = "PLANNED";
        plan.route = route;

        final int marineFlag = NavSegmenter.MODE_VESSEL.equals(in.mode) ? 1 : 0;

        // 3) 구간별 날씨: 캐시 우선 → 없으면 온라인(순차+throttle)
        int weatherOk = 0;
        boolean lastCallHitNetwork = false;
        for (int i = 0; i < total; i++) {
            NavSegmenter.SegPoint sp = segPoints.get(i);

            NavSegment seg = new NavSegment();
            seg.seq = sp.seq;
            seg.repLat = sp.lat;  seg.repLon = sp.lon;
            seg.distFromStartM = sp.distFromStartM;
            seg.etaOffsetS = (in.totalDistanceM > 0)
                    ? Math.round(in.totalDurationS * (sp.distFromStartM / in.totalDistanceM))
                    : 0L;
            seg.label = labelFor(sp.distFromStartM);

            NavPlan.SegmentBundle sb = new NavPlan.SegmentBundle();
            sb.segment = seg;

            // (2순위) 근접좌표 + 최신성 캐시 조회
            NavWeather cached = null;
            try {
                long minFetched = System.currentTimeMillis() - CACHE_TTL_MS;
                cached = dao.findNearbyFreshWeather(
                        sp.lat - CACHE_RADIUS_DEG, sp.lat + CACHE_RADIUS_DEG,
                        sp.lon - CACHE_RADIUS_DEG, sp.lon + CACHE_RADIUS_DEG,
                        marineFlag, minFetched);
            } catch (Exception ignore) { /* 캐시 조회 실패는 무시하고 온라인 */ }

            if (cached != null && cached.jsonDaily != null) {
                // 캐시 재사용: 새 구간용 날씨 레코드로 복제(원본 json 재파싱)
                NavWeather w = new NavWeather();
                w.marine = cached.marine;
                w.fetchedAt = cached.fetchedAt;      // 원본 확보시각 유지(최신성 표시용)
                w.jsonDaily = cached.jsonDaily;
                sb.weather = w;
                sb.weatherDays = toDays(
                        NavWeatherService.parseStoredDaily(cached.jsonDaily, cached.marine == 1));
                weatherOk++;
                lastCallHitNetwork = false;
                Log.d(TAG, "seg " + sp.seq + " weather from CACHE");
            } else {
                // (1순위) 온라인 확보
                try {
                    NavWeatherService.WeatherResult wr =
                            NavWeatherService.fetchForSegment(sp.lat, sp.lon, in.mode);
                    NavWeather w = new NavWeather();
                    w.marine = wr.marine ? 1 : 0;
                    w.fetchedAt = System.currentTimeMillis();
                    w.jsonDaily = wr.jsonDaily;
                    sb.weather = w;
                    sb.weatherDays = toDays(wr.days);
                    weatherOk++;
                    lastCallHitNetwork = true;
                    Log.d(TAG, "seg " + sp.seq + " weather from NET");
                } catch (Exception weatherErr) {
                    sb.weather = null;
                    sb.weatherDays = null;
                    lastCallHitNetwork = false;
                    Log.w(TAG, "seg " + sp.seq + " weather FAIL: " + weatherErr.getMessage());
                }
            }

            plan.segments.add(sb);
            postProgress(cb, i + 1, total);

            // 실제 네트워크 호출을 했을 때만 다음 호출 전에 지연(캐시 히트는 지연 불필요)
            if (i < total - 1 && lastCallHitNetwork) {
                try { Thread.sleep(THROTTLE_MS); } catch (InterruptedException ignore) {}
            }
        }

        // 4) 한 트랜잭션으로 저장
        long routeId = dao.saveWholePlan(plan);
        postComplete(cb, routeId, total, weatherOk);
    }

    private static int countWeather(NavPlan plan) {
        int c = 0;
        for (NavPlan.SegmentBundle sb : plan.segments) if (sb.weather != null) c++;
        return c;
    }

    private static List<NavWeatherDay> toDays(List<NavWeatherService.DayForecast> src) {
        List<NavWeatherDay> out = new ArrayList<>();
        if (src == null) return out;
        for (NavWeatherService.DayForecast d : src) {
            NavWeatherDay row = new NavWeatherDay();
            row.date = d.date;
            row.tMin = d.tMin;   row.tMax = d.tMax;
            row.precipMm = d.precipMm;
            row.windMax = d.windMax;
            row.waveMax = d.waveMax;
            row.weatherCode = d.weatherCode;
            out.add(row);
        }
        return out;
    }

    private static String labelFor(double distFromStartM) {
        if (distFromStartM <= 0) return "Start";
        long km = Math.round(distFromStartM / 1000d);
        return km + " km";
    }

    // ---- 콜백 메인스레드 포스팅 ----
    private void postProgress(@Nullable Callback cb, int done, int total) {
        if (cb == null) return;
        main.post(() -> cb.onProgress(done, total));
    }
    private void postComplete(@Nullable Callback cb, long routeId, int segCount, int wOk) {
        if (cb == null) return;
        main.post(() -> cb.onComplete(routeId, segCount, wOk));
    }
    private void postError(@Nullable Callback cb, String msg) {
        if (cb == null) return;
        main.post(() -> cb.onError(msg));
    }

    public void shutdown() {
        io.shutdown();
    }
}
