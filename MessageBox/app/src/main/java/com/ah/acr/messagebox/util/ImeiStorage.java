package com.ah.acr.messagebox.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Stores the last connected TYTO device's IMEI in SharedPreferences.
 *
 * Usage:
 *   ImeiStorage.save(context, "865123456789012");   // When BLE connects
 *   String imei = ImeiStorage.getLast(context);      // When exporting
 *
 * If no IMEI has ever been stored, getLast() returns null.
 * Filenames use sanitize() to strip unsafe characters.
 */
public class ImeiStorage {

    private static final String TAG = "ImeiStorage";
    private static final String PREFS_NAME = "MessageBox_Prefs";
    private static final String KEY_LAST_IMEI = "last_imei";


    /** Save IMEI when TYTO device connects */
    public static void save(Context context, String imei) {
        if (context == null || imei == null || imei.trim().isEmpty()) return;

        // Skip placeholder values
        String trimmed = imei.trim();
        if (trimmed.equals("-") || trimmed.equalsIgnoreCase("unknown")) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_IMEI, trimmed).apply();
        Log.v(TAG, "IMEI saved: " + trimmed);
    }


    /** Get the last connected IMEI (null if never connected) */
    public static String getLast(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_IMEI, null);
    }


    /** Check if an IMEI has been stored */
    public static boolean hasImei(Context context) {
        String imei = getLast(context);
        return imei != null && !imei.isEmpty();
    }


    /**
     * Sanitize IMEI for use in a filename.
     * Keeps alphanumeric, dash, underscore — replaces others with underscore.
     * Returns empty string if input is null/empty.
     */
    public static String sanitize(String imei) {
        if (imei == null || imei.trim().isEmpty()) return "";
        return imei.trim().replaceAll("[^a-zA-Z0-9_-]", "_");
    }


    /** Get sanitized IMEI ready for filename use (empty if none stored) */
    public static String getSanitizedLast(Context context) {
        String imei = getLast(context);
        return sanitize(imei);
    }


    /** Clear stored IMEI (for testing / reset) */
    public static void clear(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_LAST_IMEI).apply();
        Log.v(TAG, "IMEI cleared");
    }
}
