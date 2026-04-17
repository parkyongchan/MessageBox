package com.ah.acr.messagebox.tabs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * 지도 탭 - 앱 진입 시 기본 화면
 * Step 3에서 OSMDroid + MBTiles + Meshtastic 스타일 마커 구현 예정
 */
public class MapTabFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        FrameLayout root = new FrameLayout(requireContext());
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(0xFF0F1A2E);

        LinearLayout center = new LinearLayout(requireContext());
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(android.view.Gravity.CENTER);

        TextView icon = new TextView(requireContext());
        icon.setText("🗺");
        icon.setTextSize(64);
        icon.setAlpha(0.3f);
        icon.setGravity(android.view.Gravity.CENTER);
        center.addView(icon);

        TextView tv1 = new TextView(requireContext());
        tv1.setText("지도");
        tv1.setTextColor(0xFF378ADD);
        tv1.setTextSize(18);
        tv1.setGravity(android.view.Gravity.CENTER);
        tv1.setPadding(0, 24, 0, 0);
        center.addView(tv1);

        TextView tv2 = new TextView(requireContext());
        tv2.setText("Step 3에서 OSMDroid + MBTiles 구현");
        tv2.setTextColor(0xFF4A5F78);
        tv2.setTextSize(12);
        tv2.setGravity(android.view.Gravity.CENTER);
        tv2.setPadding(0, 8, 0, 0);
        center.addView(tv2);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.CENTER;
        center.setLayoutParams(lp);
        root.addView(center);

        return root;
    }
}