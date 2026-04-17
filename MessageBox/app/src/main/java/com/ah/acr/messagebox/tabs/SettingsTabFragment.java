package com.ah.acr.messagebox.tabs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.ble.BLE;

/**
 * 설정 탭 - NavHost로 settings_tab_nav_graph 호스팅
 */
public class SettingsTabFragment extends Fragment {

    private int containerId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        FrameLayout root = new FrameLayout(requireContext());
        containerId = View.generateViewId();
        root.setId(containerId);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(0xFF0A1628);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getChildFragmentManager().findFragmentById(containerId) == null) {
            // 설정 진입 시 기존처럼 SET=? 요청 보내기
            BLE.INSTANCE.getWriteQueue().offer("SET=?");

            NavHostFragment navHost = NavHostFragment.create(R.navigation.settings_tab_nav_graph);
            getChildFragmentManager().beginTransaction()
                    .replace(containerId, navHost)
                    .setPrimaryNavigationFragment(navHost)
                    .commitNow();
        }
    }
}