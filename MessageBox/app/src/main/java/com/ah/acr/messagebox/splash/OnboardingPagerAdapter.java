package com.ah.acr.messagebox.splash;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * Adapter for the 4 onboarding pages inside ViewPager2.
 * Each page is the same fragment class with different arguments.
 */
public class OnboardingPagerAdapter extends FragmentStateAdapter {

    private static final int PAGE_COUNT = 4;

    public OnboardingPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return OnboardingPageFragment.newInstance(position);
    }

    @Override
    public int getItemCount() {
        return PAGE_COUNT;
    }
}
