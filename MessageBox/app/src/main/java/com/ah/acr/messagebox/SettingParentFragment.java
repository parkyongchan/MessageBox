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

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    if (position == 0) tab.setText("Address");
                    else if (position == 1) tab.setText("Location");
                    else if (position == 2) tab.setText("SOS");
                    else if (position == 3) tab.setText("Firmware Update");
                    else tab.setText("Address");
                }).attach();

        return view;
    }


//    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
//        super.onViewCreated(view, savedInstanceState);
//
//
//        BLE.INSTANCE.getSelectedDevice().observe(getViewLifecycleOwner(), device -> {
//            if (device != null) {
//                BLE.INSTANCE.getWriteQueue().offer("BROAD=5");
//            }
//        });
//
//    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        BLE.INSTANCE.getWriteQueue().offer("BROAD=0");
        binding = null;
    }

}