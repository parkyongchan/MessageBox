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

/**
 * BLE(연결) 탭 - NavHost로 ble_tab_nav_graph를 호스팅
 * 내부에서 findNavController()가 동작하도록 보장
 */
public class BleTabFragment extends Fragment {

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
            NavHostFragment navHost = NavHostFragment.create(R.navigation.ble_tab_nav_graph);
            getChildFragmentManager().beginTransaction()
                    .replace(containerId, navHost)
                    .setPrimaryNavigationFragment(navHost)
                    .commitNow();
        }
    }
}