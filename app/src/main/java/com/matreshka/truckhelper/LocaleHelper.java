package com.matreshka.truckhelper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

public final class LocaleHelper {

    private static final String PREFS_NAME = "truck_stats";
    private static final String KEY_LANG = "app_language";

    public static Context onAttach(Context context) {
        String lang = getLanguage(context);
        if (lang == null || lang.isEmpty()) return context;
        return setLocale(context, lang);
    }

    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANG, "");
    }

    public static void setLanguage(Context context, String lang) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANG, lang == null ? "" : lang).apply();
    }

    public static Context setLocale(Context context, String lang) {
        Locale locale;
        if (lang == null || lang.isEmpty()) {
            return context;
        } else if ("en".equals(lang)) {
            locale = new Locale("en");
        } else {
            locale = new Locale("ru");
        }

        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());

        if (Build.VERSION.SDK_INT >= 24) {
            config.setLocale(locale);
            config.setLocales(new LocaleList(locale));
            return context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
            return context;
        }
    }
}
