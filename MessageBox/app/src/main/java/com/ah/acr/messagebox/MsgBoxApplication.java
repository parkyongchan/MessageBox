package com.ah.acr.messagebox;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import com.ah.acr.messagebox.packet.security.SharedUtil;
import com.ah.acr.messagebox.util.LocaleHelper;

import java.util.Locale;

public class MsgBoxApplication extends Application {

    public static SharedUtil sharedUtil;


    // ═════════════════════════════════════════════════════════════
    //   ⭐ 다국어 지원 - 강력 모드
    //   
    //   모든 가능한 위치에서 Locale 적용
    //   1. attachBaseContext
    //   2. onCreate
    //   3. getResources() override
    // ═════════════════════════════════════════════════════════════

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.onAttach(base));
        applyLocale();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.sharedUtil = new SharedUtil(getApplicationContext());
        applyLocale();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyLocale();
    }

    /**
     * ⭐ Locale을 Application 전체에 강제 적용
     */
    @SuppressWarnings("deprecation")
    private void applyLocale() {
        try {
            String lang = LocaleHelper.getLanguage(this);
            Locale locale = new Locale(lang);
            Locale.setDefault(locale);

            Resources resources = getBaseContext().getResources();
            Configuration config = resources.getConfiguration();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocale(locale);
                config.setLocales(new android.os.LocaleList(locale));
            } else {
                config.locale = locale;
            }

            resources.updateConfiguration(config, resources.getDisplayMetrics());

            android.util.Log.v("LocaleHelper", "🌐 Application Locale 적용: " + lang);
        } catch (Exception e) {
            android.util.Log.e("LocaleHelper", "applyLocale error: " + e.getMessage());
        }
    }
}
