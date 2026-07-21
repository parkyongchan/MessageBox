package com.ah.acr.messagebox.nav;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * NAV 3단계 - 구간 대표 좌표별 7일 날씨 확보 (Open-Meteo, 무료·키 불필요).
 *
 *  - 육상: Forecast API   (temperature_2m_max/min, precipitation_sum, wind_speed_10m_max, weather_code)
 *  - 해상: Marine API      (wave_height_max, wind_wave_height_max)
 *  - VESSEL: 해상 + 육상(풍속/기온/강수/코드) 병행 확보 후 병합
 *
 * 모든 메서드는 블로킹(동기) 호출이며 반드시 백그라운드 스레드에서 호출할 것.
 * 호출 간 지연(throttle)은 상위 오케스트레이터(NavPlanner)에서 처리한다.
 *
 * HttpURLConnection + org.json 만 사용(OkHttp/Retrofit/Gson 미설치).
 */
public final class NavWeatherService {

    private static final String FORECAST_URL =
            "https://api.open-meteo.com/v1/forecast";
    private static final String MARINE_URL =
            "https://marine-api.open-meteo.com/v1/marine";

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS    = 12000;

    private NavWeatherService() {}

    /** 하루치 파싱 결과(정규화 표시용) */
    public static final class DayForecast {
        public String date;         // YYYY-MM-DD
        public Double tMin;         // 최저 기온
        public Double tMax;         // 최고 기온
        public Double precipMm;     // 강수량
        public Double windMax;      // 최대 풍속
        public Double waveMax;      // 최대 파고(해상)
        public Integer weatherCode; // WMO 코드
    }

    /** 구간 1건에 대한 7일 날씨 결과 */
    public static final class WeatherResult {
        public boolean marine;               // 해상 예보 포함 여부(선박)
        public String jsonDaily;             // 원본 JSON(직렬화) - nav_weather.json_daily 저장용
        public List<DayForecast> days = new ArrayList<>(); // 정규화 결과 - nav_weather_day 저장용
    }

    /**
     * 이동수단에 맞춰 한 좌표의 7일 날씨를 확보한다.
     * @param mode WALK/VEHICLE/VESSEL
     */
    public static WeatherResult fetchForSegment(double lat, double lon, String mode) throws Exception {
        if (NavSegmenter.MODE_VESSEL.equals(mode)) {
            return fetchVessel(lat, lon);
        }
        return fetchLandOnly(lat, lon);
    }

    // ---------------------- 육상(WALK/VEHICLE) ----------------------

    private static WeatherResult fetchLandOnly(double lat, double lon) throws Exception {
        String url = FORECAST_URL
                + "?latitude=" + fmt(lat)
                + "&longitude=" + fmt(lon)
                + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max,weather_code"
                + "&forecast_days=7&timezone=auto";
        JSONObject root = getJson(url);

        WeatherResult r = new WeatherResult();
        r.marine = false;
        r.jsonDaily = root.toString();
        r.days = parseLandDaily(root.optJSONObject("daily"));
        return r;
    }

    // ---------------------- 해상(VESSEL) ----------------------

    private static WeatherResult fetchVessel(double lat, double lon) throws Exception {
        WeatherResult r = new WeatherResult();
        r.marine = true;

        // 1) 육상 파라미터(풍속/기온/강수/코드) - 해안/근해에서도 유효
        JSONObject land = null;
        try {
            land = getJson(FORECAST_URL
                    + "?latitude=" + fmt(lat)
                    + "&longitude=" + fmt(lon)
                    + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max,weather_code"
                    + "&forecast_days=7&timezone=auto");
        } catch (Exception ignore) {
            // 원양 등에서 육상값이 비어도 계속 진행
        }

        // 2) 해상 파라미터(파고)
        JSONObject marine = null;
        try {
            marine = getJson(MARINE_URL
                    + "?latitude=" + fmt(lat)
                    + "&longitude=" + fmt(lon)
                    + "&daily=wave_height_max,wind_wave_height_max"
                    + "&forecast_days=7&timezone=auto");
        } catch (Exception ignore) {
            // 내륙 좌표 등에서 marine 이 오류일 수 있음 → 파고 없이 진행
        }

        // 원본 보관: 육상+해상 합쳐서 저장
        JSONObject combined = new JSONObject();
        combined.put("land",   land   == null ? JSONObject.NULL : land);
        combined.put("marine", marine == null ? JSONObject.NULL : marine);
        r.jsonDaily = combined.toString();

        // 정규화 병합: 육상 daily 기준으로 날짜 배열 만들고, marine 파고를 인덱스로 합침
        r.days = mergeVesselDaily(
                land   != null ? land.optJSONObject("daily")   : null,
                marine != null ? marine.optJSONObject("daily") : null);
        return r;
    }

    // ---------------------- 파싱 ----------------------

    /** 저장된 원본 JSON(json_daily)에서 일별 예보를 복원(캐시 재사용 시). 실패해도 예외 안 던지고 빈 리스트. */
    public static List<DayForecast> parseStoredDaily(String json, boolean marine) {
        try {
            JSONObject root = new JSONObject(json);
            if (marine) {
                JSONObject landRoot   = root.optJSONObject("land");
                JSONObject marineRoot = root.optJSONObject("marine");
                JSONObject landDaily   = (landRoot   != null) ? landRoot.optJSONObject("daily")   : null;
                JSONObject marineDaily = (marineRoot != null) ? marineRoot.optJSONObject("daily") : null;
                return mergeVesselDaily(landDaily, marineDaily);
            } else {
                return parseLandDaily(root.optJSONObject("daily"));
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static List<DayForecast> parseLandDaily(JSONObject daily) {
        List<DayForecast> list = new ArrayList<>();
        if (daily == null) return list;
        JSONArray time   = daily.optJSONArray("time");
        JSONArray tMax   = daily.optJSONArray("temperature_2m_max");
        JSONArray tMin   = daily.optJSONArray("temperature_2m_min");
        JSONArray precip = daily.optJSONArray("precipitation_sum");
        JSONArray wind   = daily.optJSONArray("wind_speed_10m_max");
        JSONArray code   = daily.optJSONArray("weather_code");
        int n = time == null ? 0 : time.length();
        for (int i = 0; i < n; i++) {
            DayForecast d = new DayForecast();
            d.date        = time.optString(i, null);
            d.tMax        = optD(tMax, i);
            d.tMin        = optD(tMin, i);
            d.precipMm    = optD(precip, i);
            d.windMax     = optD(wind, i);
            d.weatherCode = optI(code, i);
            list.add(d);
        }
        return list;
    }

    private static List<DayForecast> mergeVesselDaily(JSONObject land, JSONObject marine) {
        List<DayForecast> list = parseLandDaily(land);

        // 육상이 비었으면 해상 time 기준으로 뼈대 생성
        if (list.isEmpty() && marine != null) {
            JSONArray mtime = marine.optJSONArray("time");
            int n = mtime == null ? 0 : mtime.length();
            for (int i = 0; i < n; i++) {
                DayForecast d = new DayForecast();
                d.date = mtime.optString(i, null);
                list.add(d);
            }
        }

        // 해상 파고 합치기(인덱스 기준)
        if (marine != null) {
            JSONArray wave = marine.optJSONArray("wave_height_max");
            for (int i = 0; i < list.size(); i++) {
                list.get(i).waveMax = optD(wave, i);
            }
        }
        return list;
    }

    // ---------------------- HTTP ----------------------

    private static JSONObject getJson(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300)
                    ? conn.getInputStream() : conn.getErrorStream();
            String body = readAll(is);
            if (code < 200 || code >= 300) {
                throw new RuntimeException("Open-Meteo HTTP " + code + ": " + body);
            }
            return new JSONObject(body);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    // ---------------------- helpers ----------------------

    private static Double optD(JSONArray a, int i) {
        if (a == null || i >= a.length() || a.isNull(i)) return null;
        return a.optDouble(i);
    }

    private static Integer optI(JSONArray a, int i) {
        if (a == null || i >= a.length() || a.isNull(i)) return null;
        return a.optInt(i);
    }

    /** 좌표 포맷(불필요한 정밀도 제거로 캐시 히트율↑) */
    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.4f", v);
    }
}
