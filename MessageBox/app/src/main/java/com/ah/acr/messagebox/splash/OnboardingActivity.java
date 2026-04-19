package com.ah.acr.messagebox.splash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.ah.acr.messagebox.MainActivity;
import com.ah.acr.messagebox.R;

/**
 * OnboardingActivity
 * - 4-page swipeable walkthrough that explains TYTO device setup.
 * - Hosts ViewPager2, a custom dot indicator, and the Next / Get Started button.
 * - On the final page the button text switches and marks onboarding complete.
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "tyto_prefs";
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";
    private static final int PAGE_COUNT = 4;

    private ViewPager2 pager;
    private LinearLayout dotsContainer;
    private Button btnNext;
    private View[] dots;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        pager = findViewById(R.id.onboarding_pager);
        dotsContainer = findViewById(R.id.onboarding_dots);
        btnNext = findViewById(R.id.onboarding_btn_next);

        pager.setAdapter(new OnboardingPagerAdapter(this));
        buildDots();
        setCurrentPage(0);

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                setCurrentPage(position);
            }
        });

        btnNext.setOnClickListener(v -> {
            int current = pager.getCurrentItem();
            if (current < PAGE_COUNT - 1) {
                pager.setCurrentItem(current + 1, true);
            } else {
                finishOnboarding();
            }
        });
    }

    private void buildDots() {
        dots = new View[PAGE_COUNT];
        int sizePx = (int) (6 * getResources().getDisplayMetrics().density);
        int widePx = (int) (18 * getResources().getDisplayMetrics().density);
        int marginPx = (int) (4 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < PAGE_COUNT; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMargins(marginPx, 0, marginPx, 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.shape_dot_inactive);
            dotsContainer.addView(dot);
            dots[i] = dot;
        }
        // placeholder widths will be replaced by setCurrentPage
    }

    private void setCurrentPage(int index) {
        float density = getResources().getDisplayMetrics().density;
        int small = (int) (6 * density);
        int wide = (int) (18 * density);
        int margin = (int) (4 * density);

        for (int i = 0; i < dots.length; i++) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dots[i].getLayoutParams();
            if (i == index) {
                lp.width = wide;
                lp.height = small;
                lp.setMargins(margin, 0, margin, 0);
                dots[i].setBackgroundResource(R.drawable.shape_dot_active);
            } else {
                lp.width = small;
                lp.height = small;
                lp.setMargins(margin, 0, margin, 0);
                dots[i].setBackgroundResource(R.drawable.shape_dot_inactive);
            }
            dots[i].setLayoutParams(lp);
        }

        btnNext.setText(index == PAGE_COUNT - 1
                ? R.string.onboarding_btn_done
                : R.string.onboarding_btn_next);
    }

    private void finishOnboarding() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply();

        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
