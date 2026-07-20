package com.ah.acr.messagebox.nav;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import org.osmdroid.util.GeoPoint;

/**
 * [NAV] Route calculation via OSRM public server (free). HttpURLConnection + org.json.
 * mode: 0=WALK(foot), 1=VEHICLE(driving), 2=VESSEL(straight, no API).
 */
public final class NavRouteService {

    private static final String TAG = "NAV-ROUTE";
    private static final String OSRM = "https://router.project-osrm.org/route/v1/";

    private NavRouteService() {}

    public static final class RouteResult {
        public boolean ok;
        public String error;
        public double distanceM;      // total distance (m)
        public double durationS;      // total duration (s)
        public List<GeoPoint> geometry = new ArrayList<>();  // route line points
    }

    public interface Callback { void onResult(RouteResult r); }

    /** Async route request. Callback is delivered on main thread. */
    public static void requestRoute(final int mode, final double sLat, final double sLon,
                                    final double dLat, final double dLon, final Callback cb) {
        final Handler main = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            RouteResult r = new RouteResult();
            try {
                if (mode == 2) {
                    // VESSEL: straight line approximation (no free marine routing)
                    r.ok = true;
                    r.geometry.add(new GeoPoint(sLat, sLon));
                    r.geometry.add(new GeoPoint(dLat, dLon));
                    r.distanceM = haversine(sLat, sLon, dLat, dLon);
                    // vessel duration filled later by caller using cruise speed
                    r.durationS = 0;
                } else {
                    String profile = (mode == 0) ? "foot" : "driving";
                    String url = OSRM + profile + "/"
                            + String.format(Locale.US, "%f,%f;%f,%f", sLon, sLat, dLon, dLat)
                            + "?overview=full&geometries=geojson";
                    String json = httpGet(url);
                    JSONObject root = new JSONObject(json);
                    if (!"Ok".equals(root.optString("code"))) {
                        r.ok = false; r.error = "OSRM: " + root.optString("code");
                    } else {
                        JSONArray routes = root.getJSONArray("routes");
                        JSONObject r0 = routes.getJSONObject(0);
                        r.distanceM = r0.getDouble("distance");
                        r.durationS = r0.getDouble("duration");
                        JSONArray coords = r0.getJSONObject("geometry").getJSONArray("coordinates");
                        for (int i = 0; i < coords.length(); i++) {
                            JSONArray c = coords.getJSONArray(i);
                            // geojson = [lon, lat]
                            r.geometry.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
                        }
                        r.ok = true;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "route fail: " + e.getMessage(), e);
                r.ok = false; r.error = e.getMessage();
            }
            main.post(() -> cb.onResult(r));
        });
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("User-Agent", "TYTOConnect/1.0");
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Great-circle distance in meters. */
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}