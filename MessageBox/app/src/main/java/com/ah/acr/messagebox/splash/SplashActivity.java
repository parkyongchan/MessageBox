package com.ah.acr.messagebox.splash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.ah.acr.messagebox.MainActivity;
import com.ah.acr.messagebox.R;

/**
 * SplashActivity
 * - Displays the TYTO Connect logo for ~2 seconds.
 * - On first launch, routes the user into OnboardingActivity.
 * - On subsequent launches, routes directly into MainActivity.
 *
 * Registered as the LAUNCHER activity in AndroidManifest.xml.
 */
public class SplashActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "tyto_prefs";
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";
    private static final long SPLASH_DURATION_MS = 2000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Immersive splash — keep system bars translucent over the artwork.
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(this::routeNext, SPLASH_DURATION_MS);
    }

    private void routeNext() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean done = prefs.getBoolean(KEY_ONBOARDING_DONE, false);

        Intent next = done
                ? new Intent(this, MainActivity.class)
                : new Intent(this, OnboardingActivity.class);

        startActivity(next);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
