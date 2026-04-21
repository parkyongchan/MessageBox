package com.ah.acr.messagebox.util;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.ah.acr.messagebox.R;

/**
 * 지도 모드 토글 UI 설정 헬퍼
 * Fragment 에서 토글 UI 연결을 한 줄로 단순화
 */
public class MapModeToggleHelper {

    private static final String TAG = "MapModeToggle";

    public interface OnModeChangedListener {
        void onModeChanged(MapModeManager.Mode newMode);
    }

    /**
     * 토글 UI 설정 (view_map_mode_toggle.xml 이 include 된 View 에서 호출)
     * 
     * @param root 토글이 포함된 상위 View (보통 fragment 의 root)
     * @param ctx Context
     * @param listener 모드 변경 시 콜백
     */
    public static void setup(View root, Context ctx, OnModeChangedListener listener) {
        TextView btnOnline = root.findViewById(R.id.btn_mode_online);
        TextView btnOffline = root.findViewById(R.id.btn_mode_offline);

        if (btnOnline == null || btnOffline == null) {
            Log.w(TAG, "토글 버튼을 찾을 수 없습니다. view_map_mode_toggle.xml 이 include 되었는지 확인하세요.");
            return;
        }

        // 현재 모드에 맞게 UI 업데이트
        updateUI(ctx, btnOnline, btnOffline);

        // 온라인 클릭
        btnOnline.setOnClickListener(v -> {
            if (MapModeManager.getMode(ctx) == MapModeManager.Mode.ONLINE) return;
            MapModeManager.setMode(ctx, MapModeManager.Mode.ONLINE);
            updateUI(ctx, btnOnline, btnOffline);
            if (listener != null) listener.onModeChanged(MapModeManager.Mode.ONLINE);
        });

        // 오프라인 클릭
        btnOffline.setOnClickListener(v -> {
            if (MapModeManager.getMode(ctx) == MapModeManager.Mode.OFFLINE) return;
            MapModeManager.setMode(ctx, MapModeManager.Mode.OFFLINE);
            updateUI(ctx, btnOnline, btnOffline);
            if (listener != null) listener.onModeChanged(MapModeManager.Mode.OFFLINE);
        });
    }


    /** 현재 모드에 맞게 토글 UI 업데이트 */
    private static void updateUI(Context ctx, TextView btnOnline, TextView btnOffline) {
        MapModeManager.Mode currentMode = MapModeManager.getMode(ctx);

        if (currentMode == MapModeManager.Mode.ONLINE) {
            // 🌐 ONLINE 활성
            btnOnline.setBackgroundResource(R.drawable.bg_map_mode_active);
            btnOnline.setTextColor(0xFF0A1628);
            btnOffline.setBackgroundResource(0);
            btnOffline.setTextColor(0xFF95B0D4);
        } else {
            // 📁 OFFLINE 활성
            btnOffline.setBackgroundResource(R.drawable.bg_map_mode_active);
            btnOffline.setTextColor(0xFF0A1628);
            btnOnline.setBackgroundResource(0);
            btnOnline.setTextColor(0xFF95B0D4);
        }
    }
}
