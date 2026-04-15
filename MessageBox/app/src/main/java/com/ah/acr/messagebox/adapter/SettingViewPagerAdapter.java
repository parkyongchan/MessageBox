package com.ah.acr.messagebox.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.ah.acr.messagebox.AddrssBookFragment;
import com.ah.acr.messagebox.FirmwareFragment;
import com.ah.acr.messagebox.SettingFragment;
import com.ah.acr.messagebox.SosFragment;
import com.ah.acr.messagebox.StatusFragment;

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
            default: return new AddrssBookFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 4; // 탭 개수
    }
}
