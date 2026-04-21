package com.ah.acr.messagebox.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.ah.acr.messagebox.AddrssBookFragment;
import com.ah.acr.messagebox.FirmwareFragment;
import com.ah.acr.messagebox.OfflineMapFragment;
import com.ah.acr.messagebox.SettingFragment;
import com.ah.acr.messagebox.SosFragment;

public class SettingViewPagerAdapter extends FragmentStateAdapter {
    public SettingViewPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new AddrssBookFragment();
            case 1: return new SettingFragment();
            case 2: return new SosFragment();
            case 3: return new FirmwareFragment();
            case 4: return new OfflineMapFragment();  // ⭐ 새로 추가
            default: return new AddrssBookFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 5; // ⭐ 탭 개수 (Maps 추가)
    }
}
