package com.ah.acr.messagebox.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

/**
 * ⭐ 다국어 지원 헬퍼
 *
 * 핵심:
 * 1. createConfigurationContext로 새 Context 생성 (SDK 17+)
 * 2. 동시에 Resources도 직접 업데이트 (deprecated지만 효과적)
 * 3. 둘 다 해야 모든 곳에서 일본어 적용됨
 */
public class LocaleHelper {

    private static final String TAG = "LocaleHelper";
    private static final String SELECTED_LANGUAGE = "selected_language";
    private static final String PREFS_NAME = "LocaleHelper_Prefs";
    public static final String LANG_ENGLISH = "en";
    public static final String LANG_JAPANESE = "ja";
    public static final String DEFAULT_LANG = LANG_ENGLISH;


    public static Context onAttach(Context context) {
        if (context == null) return null;
        try {
            String lang = getPersistedLanguage(context, DEFAULT_LANG);
            return setLocale(context, lang);
        } catch (Exception e) {
            android.util.Log.e(TAG, "onAttach error: " + e.getMessage());
            return context;
        }
    }


    public static Context setLocale(Context context, String language) {
        if (context == null || language == null) return context;
        try {
            persistLanguage(context, language);
            return updateResources(context, language);
        } catch (Exception e) {
            android.util.Log.e(TAG, "setLocale error: " + e.getMessage());
            return context;
        }
    }


    public static String getLanguage(Context context) {
        if (context == null) return DEFAULT_LANG;
        try {
            return getPersistedLanguage(context, DEFAULT_LANG);
        } catch (Exception e) {
            android.util.Log.e(TAG, "getLanguage error: " + e.getMessage());
            return DEFAULT_LANG;
        }
    }


    private static void persistLanguage(Context context, String language) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(
                    PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs == null) return;
            prefs.edit().putString(SELECTED_LANGUAGE, language).apply();
            android.util.Log.v(TAG, "💾 언어 저장: " + language);
        } catch (Exception e) {
            android.util.Log.e(TAG, "persistLanguage error: " + e.getMessage());
        }
    }


    private static String getPersistedLanguage(Context context, String defaultLang) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(
                    PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs == null) return defaultLang;
            String lang = prefs.getString(SELECTED_LANGUAGE, defaultLang);
            android.util.Log.v(TAG, "📖 언어 로드: " + lang);
            return lang;
        } catch (Exception e) {
            android.util.Log.e(TAG, "getPersistedLanguage error: " + e.getMessage());
            return defaultLang;
        }
    }


    /**
     * ⭐ 핵심: Context + Resources 둘 다 업데이트
     */
    @SuppressWarnings("deprecation")
    private static Context updateResources(Context context, String language) {
        try {
            Locale locale = new Locale(language);
            Locale.setDefault(locale);

            Resources resources = context.getResources();
            if (resources == null) return context;

            Configuration configuration = new Configuration(resources.getConfiguration());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                configuration.setLocale(locale);
                configuration.setLocales(
                        new android.os.LocaleList(locale));

                // ⭐ 핵심! Resources도 직접 업데이트 (legacy)
                // createConfigurationContext만 하면 부족
                // 기존 Resources의 Configuration도 업데이트 필요
                resources.updateConfiguration(
                        configuration, resources.getDisplayMetrics());

                // 새 Context도 반환
                return context.createConfigurationContext(configuration);
            } else {
                configuration.locale = locale;
                resources.updateConfiguration(configuration, resources.getDisplayMetrics());
                return context;
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "updateResources error: " + e.getMessage());
            return context;
        }
    }
}
