package com.ah.acr.messagebox.nav.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

/**
 * NAV DB 접근. (3단계: 저장 위주. 조회 메서드는 5단계 안내화면에서 사용)
 * 모든 호출은 백그라운드 스레드에서.
 */
@Dao
public interface NavDao {

    // ---- insert ----
    @Insert
    long insertRoute(NavRoute route);

    @Insert
    long insertSegment(NavSegment segment);

    @Insert
    long insertWeather(NavWeather weather);

    @Insert
    List<Long> insertWeatherDays(List<NavWeatherDay> days);

    // ---- update ----
    @Query("UPDATE nav_route SET status = :status WHERE id = :routeId")
    void updateRouteStatus(long routeId, String status);

    // ---- query (이후 단계 표시용) ----
    @Query("SELECT * FROM nav_route WHERE id = :routeId")
    NavRoute getRoute(long routeId);

    @Query("SELECT * FROM nav_route ORDER BY fetched_at DESC")
    List<NavRoute> getAllRoutes();

    @Query("SELECT * FROM nav_segment WHERE route_id = :routeId ORDER BY seq ASC")
    List<NavSegment> getSegments(long routeId);

    @Query("SELECT * FROM nav_weather WHERE segment_id = :segmentId ORDER BY fetched_at DESC LIMIT 1")
    NavWeather getWeatherForSegment(long segmentId);

    @Query("SELECT * FROM nav_weather_day WHERE weather_id = :weatherId ORDER BY date ASC")
    List<NavWeatherDay> getWeatherDays(long weatherId);

    // ---- 근접 캐시 재사용(5.3, 4단계에서 확장). 반경은 호출부에서 위/경도 박스로 필터 ----
    @Query("SELECT w.* FROM nav_weather w " +
           "JOIN nav_segment s ON s.id = w.segment_id " +
           "WHERE w.marine = :marine " +
           "AND s.rep_lat BETWEEN :latMin AND :latMax " +
           "AND s.rep_lon BETWEEN :lonMin AND :lonMax " +
           "AND w.fetched_at >= :minFetchedAt " +
           "ORDER BY w.fetched_at DESC LIMIT 1")
    NavWeather findNearbyFreshWeather(double latMin, double latMax,
                                      double lonMin, double lonMax,
                                      int marine, long minFetchedAt);

    // ---- 한 경로 전체 삭제(재확보 시) ----
    @Query("DELETE FROM nav_route WHERE id = :routeId")
    void deleteRoute(long routeId); // CASCADE 로 segment/weather/day 동반 삭제

    /**
     * 경로+구간+날씨(+일별)를 한 트랜잭션으로 저장.
     * plan.route 는 id 미설정 상태, plan.segments[i].weather 에 확보 결과가 담겨 있어야 한다.
     */
    @Transaction
    default long saveWholePlan(NavPlan plan) {
        long routeId = insertRoute(plan.route);
        for (NavPlan.SegmentBundle sb : plan.segments) {
            sb.segment.routeId = routeId;
            long segId = insertSegment(sb.segment);
            if (sb.weather != null) {
                sb.weather.segmentId = segId;
                long wId = insertWeather(sb.weather);
                if (sb.weatherDays != null && !sb.weatherDays.isEmpty()) {
                    for (NavWeatherDay d : sb.weatherDays) d.weatherId = wId;
                    insertWeatherDays(sb.weatherDays);
                }
            }
        }
        return routeId;
    }
}
