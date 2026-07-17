package com.ah.acr.messagebox;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * [S5-fav] \uc0dd\uc874 \uc990\uaca8\ucc3e\uae30 \uc800\uc7a5\uc18c (\ucd5c\ub300 10\uc2ac\ub86f, \uc601\uad6c \uc800\uc7a5).
 * SharedPreferences\uc5d0 slot\ubcc4\ub85c name|lat|lon \ud3ec\ub9f7\uc73c\ub85c \uc800\uc7a5. \uc571 \uaebc\ub3c4 \uc720\uc9c0.
 */
public final class SurvivalFavStore {

    private SurvivalFavStore() {}

    public static final int MAX_SLOTS = 10;
    private static final String PREF = "survival_fav";
    private static final String KEY = "slot_";   // slot_0 ~ slot_9

    /** \uc990\uaca8\ucc3e\uae30 \ud55c \uce78. slot=0~9, \ube44\uc5b4\uc788\uc73c\uba74 name=null. */
    public static final class Fav {
        public final int slot;
        public final String name;
        public final double lat;
        public final double lon;
        public Fav(int slot, String name, double lat, double lon) {
            this.slot = slot; this.name = name; this.lat = lat; this.lon = lon;
        }
        public boolean isEmpty() { return name == null || name.isEmpty(); }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** \uc804\uccb4 10\uc2ac\ub86f \uc77d\uae30 (\ube48 \uc2ac\ub86f\ud3ec\ud568). */
    public static List<Fav> getAll(Context ctx) {
        SharedPreferences p = prefs(ctx);
        List<Fav> out = new ArrayList<>();
        for (int i = 0; i < MAX_SLOTS; i++) {
            String raw = p.getString(KEY + i, null);
            if (raw == null) { out.add(new Fav(i, null, 0, 0)); continue; }
            String[] parts = raw.split("\\|", 3);
            if (parts.length < 3) { out.add(new Fav(i, null, 0, 0)); continue; }
            try {
                double lat = Double.parseDouble(parts[1]);
                double lon = Double.parseDouble(parts[2]);
                out.add(new Fav(i, parts[0], lat, lon));
            } catch (Exception e) {
                out.add(new Fav(i, null, 0, 0));
            }
        }
        return out;
    }

    /** \uccab \ube48 \uc2ac\ub86f\uc5d0 \uc800\uc7a5. \uc131\uacf5\ud558\uba74 slot \ubc88\ud638, \uac00\ub4dd \ucc28\uba74 -1. */
    public static int saveToFirstEmpty(Context ctx, String name, double lat, double lon) {
        List<Fav> all = getAll(ctx);
        for (Fav f : all) {
            if (f.isEmpty()) { save(ctx, f.slot, name, lat, lon); return f.slot; }
        }
        return -1;
    }

    /** \ud2b9\uc815 \uc2ac\ub86f\uc5d0 \uc800\uc7a5(\ub36e\uc5b4\uc4f0\uae30). */
    public static void save(Context ctx, int slot, String name, double lat, double lon) {
        if (slot < 0 || slot >= MAX_SLOTS || name == null) return;
        String safe = name.replace("|", "/").trim();
        prefs(ctx).edit().putString(KEY + slot, safe + "|" + lat + "|" + lon).apply();
    }

    /** \uc2ac\ub86f \uc0ad\uc81c(\ube44\uc6c0). */
    public static void delete(Context ctx, int slot) {
        if (slot < 0 || slot >= MAX_SLOTS) return;
        prefs(ctx).edit().remove(KEY + slot).apply();
    }

    /** \ube44\uc5b4\uc788\uc9c0 \uc54a\uc740 \uc990\uaca8\ucc3e\uae30 \uac1c\uc218. */
    public static int count(Context ctx) {
        int n = 0;
        for (Fav f : getAll(ctx)) if (!f.isEmpty()) n++;
        return n;
    }
}
