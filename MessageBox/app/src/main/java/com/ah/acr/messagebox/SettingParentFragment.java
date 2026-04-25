package com.ah.acr.messagebox;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.viewpager2.widget.ViewPager2;

import com.ah.acr.messagebox.adapter.SettingViewPagerAdapter;
import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.data.DeviceInfo;
import com.ah.acr.messagebox.data.DeviceStatus;
import com.ah.acr.messagebox.databinding.FragmentSettingBinding;
import com.ah.acr.messagebox.databinding.FragmentSettingParentBinding;
import com.ah.acr.messagebox.packet.security.SharedUtil;
import com.ah.acr.messagebox.util.Coordinates;
import com.ah.acr.messagebox.viewmodel.KeyViewModel;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;


public class SettingParentFragment extends Fragment {
    private static final String TAG = SettingParentFragment.class.getSimpleName();

    private FragmentSettingParentBinding binding;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_setting_parent, container, false);

        tabLayout = view.findViewById(R.id.tab_layout);
        viewPager = view.findViewById(R.id.view_pager);

        SettingViewPagerAdapter adapter = new SettingViewPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // ⭐ TabLayoutMediator에 아이콘 + 텍스트 설정 (5개 탭)
        // ⭐ getString()을 사용하여 다국어 지원
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    if (position == 0) {
                        tab.setText(getString(R.string.setting_tab_address));
                        tab.setIcon(R.drawable.ic_tab_address);
                    } else if (position == 1) {
                        tab.setText(getString(R.string.setting_tab_location));
                        tab.setIcon(R.drawable.ic_tab_location);
                    } else if (position == 2) {
                        tab.setText(getString(R.string.setting_tab_sos));
                        tab.setIcon(R.drawable.ic_tab_sos);
                    } else if (position == 3) {
                        tab.setText(getString(R.string.setting_tab_firmware));
                        tab.setIcon(R.drawable.ic_tab_firmware);
                    } else if (position == 4) {
                        // ⭐ 새 탭: 오프라인 지도 관리
                        tab.setText(getString(R.string.setting_tab_maps));
                        tab.setIcon(R.drawable.ic_tab_maps);
                    } else {
                        tab.setText(getString(R.string.setting_tab_address));
                        tab.setIcon(R.drawable.ic_tab_address);
                    }
                }).attach();

        return view;
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // ⭐ 중요: 이전 "BROAD=0" 명령 제거!
        // 이유: 설정 탭 나갈 때 장비의 BROAD 송출을 중지시켜서
        //       다른 화면에서 배터리/신호/송신대기 실시간 업데이트가 안 되는 문제 발생
        //
        // 기존 코드 (문제):
        //   BLE.INSTANCE.getWriteQueue().offer("BROAD=0");
        //
        // 대신: "BROAD=5"로 유지 (5초 주기 송출 계속)
        BLE.INSTANCE.getWriteQueue().offer("BROAD=5");

        binding = null;
    }

}
