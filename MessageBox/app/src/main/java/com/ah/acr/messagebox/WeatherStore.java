package com.ah.acr.messagebox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [CLIMATE] \uc11c\ubc84(WX:) \ub0a0\uc528 \ud68c\uc2e0 \uc800\uc7a5\uc18c. SurvivalChatStore \ud328\ud134.
 * WX:L|T:25.7|FL:30.7|P:0|WS:4.5|WD:284|F7:date,tmax,tmin,rain,wmax;...
 * (L=\uc721\uc0c1, M=\ud574\uc0c1)
 */
public class WeatherStore {

    public static class Day {
        public String date;
        public double tmax, tmin, rain, wmax;
    }

    public static class Weather {
        public String from;          // \ubc1c\uc2e0 codeNum
        public boolean marine;       // \ud574\uc0c1(M) / \uc721\uc0c1(L)
        public double lat, lon;      // \uc694\uccad \uc704\uce58(\uc57c \uacb0\ud569 \uc2dc)
        public Map<String, String> fields = new LinkedHashMap<>();  // T, FL, P, WS, WD, WH, ...
        public List<Day> forecast7 = new ArrayList<>();
        public long recvAt = System.currentTimeMillis();
        public String rawBody;   // 영속 저장용

        public double lat() { return parseF(fields.get("LAT")); }
        public double lon() { return parseF(fields.get("LON")); }
        private static double parseF(String s) {
            if (s == null) return 0;
            try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
        }
    }

    private static final List<Weather> LIST = new ArrayList<>();

    /** WX: \ubcf8\ubb38 \ud30c\uc2f1 \ud6c4 \uc800\uc7a5. \uc131\uacf5\ud558\uba74 Weather \ubc18\ud658. */
    public static synchronized Weather addFromBody(String from, String body) {
        if (body == null || !body.startsWith("WX:")) return null;
        Weather w = new Weather();
        w.from = from;
        try {
            String[] parts = body.split("\\|");
            // parts[0] = "WX:L" or "WX:M"
            w.marine = parts[0].length() >= 4 && parts[0].charAt(3) == 'M';
            for (int i = 1; i < parts.length; i++) {
                String p = parts[i];
                int c = p.indexOf(':');
                if (c <= 0) continue;
                String key = p.substring(0, c);
                String val = p.substring(c + 1);
                if ("F7".equals(key)) {
                    // date,tmax,tmin,rain,wmax;date,...
                    for (String dseg : val.split(";")) {
                        String[] f = dseg.split(",");
                        if (f.length >= 5) {
                            Day d = new Day();
                            d.date = f[0];
                            d.tmax = parseD(f[1]); d.tmin = parseD(f[2]);
                            d.rain = parseD(f[3]); d.wmax = parseD(f[4]);
                            w.forecast7.add(d);
                        }
                    }
                } else {
                    w.fields.put(key, val);
                }
            }
        } catch (Exception ignore) {}
        w.rawBody = body;
        LIST.add(0, w);
        while (LIST.size() > 30) LIST.remove(LIST.size() - 1);
        return w;
    }

    private static double parseD(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    public static synchronized List<Weather> getAll() {
        return new ArrayList<>(LIST);
    }

    /** 발신(from)별 최신 1개만. LIST는 최신이 앞이므로 처음 만난 from을 채택. */
    public static synchronized List<Weather> getLatest() {
        java.util.LinkedHashMap<String, Weather> byFrom = new java.util.LinkedHashMap<>();
        for (Weather w : LIST) {
            String key = (w.from == null ? "" : w.from);
            if (!byFrom.containsKey(key)) byFrom.put(key, w);
        }
        return new ArrayList<>(byFrom.values());
    }

    public static synchronized Weather latest() {
        return LIST.isEmpty() ? null : LIST.get(0);
    }

    public static synchronized void clear() { LIST.clear(); }

    private static final String PREF = "weather_store";
    private static final String KEY = "raw_list";

    public static synchronized void persist(android.content.Context ctx) {
        if (ctx == null) return;
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (Weather w : LIST) {
                if (w.rawBody == null) continue;
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("from", w.from == null ? "" : w.from);
                o.put("body", w.rawBody);
                o.put("recvAt", w.recvAt);
                arr.put(o);
            }
            ctx.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE)
               .edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignore) {}
    }

    public static synchronized void load(android.content.Context ctx) {
        if (ctx == null) return;
        try {
            String s = ctx.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE)
                          .getString(KEY, null);
            if (s == null) return;
            org.json.JSONArray arr = new org.json.JSONArray(s);
            LIST.clear();
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                Weather w = addFromBody(o.optString("from"), o.optString("body"));
                if (w != null) w.recvAt = o.optLong("recvAt", System.currentTimeMillis());
            }
        } catch (Exception ignore) {}
    }

    public static synchronized Weather addAndPersist(android.content.Context ctx, String from, String body) {
        Weather w = addFromBody(from, body);
        persist(ctx);
        return w;
    }
}