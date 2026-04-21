package com.ah.acr.messagebox.util;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.ah.acr.messagebox.R;

/**
 * 지도 모드 토글 UI 헬퍼
 * 
 * 사용법:
 *   1. Fragment layout 에 <include layout="@layout/view_map_mode_toggle" /> 추가
 *   2. Fragment 에서 MapModeToggleHelper.setup(root, context, listener) 호출
 */
public class MapModeToggleHelper {

    public interface OnModeChangedListener {
        void onModeChanged(@NonNull MapModeManager.Mode newMode);
    }

    /** 토글 UI 초기화 */
    public static void setup(View root, Context context, OnModeChangedListener listener) {
        View btnOnline = root.findViewById(R.id.btn_mode_online);
        View btnOffline = root.findViewById(R.id.btn_mode_offline);

        if (btnOnline == null || btnOffline == null) return;

        // 초기 UI 반영
        updateUI(btnOnline, btnOffline, MapModeManager.getMode(context));

        // 온라인 클릭
        btnOnline.setOnClickListener(v -> {
            if (MapModeManager.isOnline(context)) return;  // 이미 온라인
            MapModeManager.setMode(context, MapModeManager.Mode.ONLINE);
            updateUI(btnOnline, btnOffline, MapModeManager.Mode.ONLINE);
            if (listener != null) listener.onModeChanged(MapModeManager.Mode.ONLINE);
        });

        // 오프라인 클릭
        btnOffline.setOnClickListener(v -> {
            if (MapModeManager.isOffline(context)) return;  // 이미 오프라인
            MapModeManager.setMode(context, MapModeManager.Mode.OFFLINE);
            updateUI(btnOnline, btnOffline, MapModeManager.Mode.OFFLINE);
            if (listener != null) listener.onModeChanged(MapModeManager.Mode.OFFLINE);
        });
    }

    /** 현재 모드에 따라 UI 업데이트 */
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
