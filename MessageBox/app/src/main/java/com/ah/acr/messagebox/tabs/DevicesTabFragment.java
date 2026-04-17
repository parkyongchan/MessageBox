package com.ah.acr.messagebox.tabs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * 장비 탭 - 연결된 TYTO 디바이스 목록
 * Step 4에서 Node 카드 리스트 구현 예정
 */
public class DevicesTabFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        FrameLayout root = new FrameLayout(requireContext());
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(0xFF0A1628);

        TextView tv = new TextView(requireContext());
        tv.setText("🔗 장비\n\n연결된 TYTO 디바이스 목록\n(Step 4에서 구현)");
        tv.setTextColor(0xFF95B0D4);
        tv.setTextSize(16);
        tv.setGravity(android.view.Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.CENTER;
        tv.setLayoutParams(lp);
        root.addView(tv);

        return root;
    }
}