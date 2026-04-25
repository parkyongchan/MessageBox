package com.ah.acr.messagebox.util;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.ah.acr.messagebox.R;

/**
 * Map Mode Toggle UI Helper
 *
 * BUGFIX (2026-04-25):
 *   - Old behavior: clicking same mode = no-op (early return)
 *   - Problem: When MBTiles fallback happens, mode silently becomes ONLINE
 *     but UI still shows OFFLINE selected. User clicks ONLINE -> "already online" -> no UI update
 *   - New behavior: Always apply UI update based on saved mode (after click)
 *     The actual map switch is delegated to caller via listener
 */
public class MapModeToggleHelper {

    public interface OnModeChangedListener {
        void onModeChanged(@NonNull MapModeManager.Mode newMode);
    }

    /** Setup toggle UI */
    public static void setup(View root, Context context, OnModeChangedListener listener) {
        View btnOnline = root.findViewById(R.id.btn_mode_online);
        View btnOffline = root.findViewById(R.id.btn_mode_offline);

        if (btnOnline == null || btnOffline == null) return;

        // Sync initial UI with current saved mode
        updateUI(btnOnline, btnOffline, MapModeManager.getMode(context));

        // Online click
        btnOnline.setOnClickListener(v -> {
            // ⭐ BUGFIX: Always apply, don't early-return
            // (Even if "already online" from fallback, we want UI to refresh)
            MapModeManager.setMode(context, MapModeManager.Mode.ONLINE);
            updateUI(btnOnline, btnOffline, MapModeManager.Mode.ONLINE);
            if (listener != null) listener.onModeChanged(MapModeManager.Mode.ONLINE);
        });

        // Offline click
        btnOffline.setOnClickListener(v -> {
            // ⭐ BUGFIX: Always apply, don't early-return
            MapModeManager.setMode(context, MapModeManager.Mode.OFFLINE);
            updateUI(btnOnline, btnOffline, MapModeManager.Mode.OFFLINE);
            if (listener != null) listener.onModeChanged(MapModeManager.Mode.OFFLINE);
        });
    }


    /**
     * ⭐ NEW: Sync UI with current saved mode.
     *
     * Used by callers after applyToMapView() to handle fallback case:
     *   - User clicks OFFLINE
     *   - applyToMapView() falls back to ONLINE
     *   - Caller sees actual applied mode is ONLINE
     *   - Caller calls syncUI() to fix toggle visual
     */
    public static void syncUI(View root, Context context) {
        if (root == null) return;
        View btnOnline = root.findViewById(R.id.btn_mode_online);
        View btnOffline = root.findViewById(R.id.btn_mode_offline);
        if (btnOnline == null || btnOffline == null) return;

        updateUI(btnOnline, btnOffline, MapModeManager.getMode(context));
    }


    /** Update UI based on mode */
    private static void updateUI(View btnOnline, View btnOffline, MapModeManager.Mode mode) {
        TextView tvOnline = (TextView) btnOnline;
        TextView tvOffline = (TextView) btnOffline;

        if (mode == MapModeManager.Mode.ONLINE) {
            tvOnline.setBackgroundResource(R.drawable.bg_map_mode_active);
            tvOnline.setTextColor(Color.parseColor("#0A1628"));
            tvOnline.setTypeface(null, android.graphics.Typeface.BOLD);

            tvOffline.setBackgroundColor(Color.TRANSPARENT);
            tvOffline.setTextColor(Color.parseColor("#95B0D4"));
            tvOffline.setTypeface(null, android.graphics.Typeface.NORMAL);
        } else {
            tvOffline.setBackgroundResource(R.drawable.bg_map_mode_active);
            tvOffline.setTextColor(Color.parseColor("#0A1628"));
            tvOffline.setTypeface(null, android.graphics.Typeface.BOLD);

            tvOnline.setBackgroundColor(Color.TRANSPARENT);
            tvOnline.setTextColor(Color.parseColor("#95B0D4"));
            tvOnline.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }
}
