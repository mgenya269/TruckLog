package com.matreshka.truckhelper;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {

    private static final String PREFS_NAME = "truck_stats";

    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ---------------- ОСНОВНЫЕ НАСТРОЙКИ ----------------

    public int getPay() {
        return Math.max(1, prefs.getInt("pay", 15000));
    }

    public void setPay(int value) {
        if (value > 0) {
            prefs.edit().putInt("pay", value).apply();
        }
    }

    public long getGoal() {
        return Math.max(1L, prefs.getLong("goal", 5000000L));
    }

    public void setGoal(long value) {
        if (value > 0) {
            prefs.edit().putLong("goal", value).apply();
        }
    }

    public int getTheme() {
        return Math.max(0, Math.min(9, prefs.getInt("theme", 0)));
    }

    public void setTheme(int theme) {
        if (theme >= 0 && theme <= 9) {
            prefs.edit().putInt("theme", theme).apply();
        }
    }

    // ---------------- ОСНОВНАЯ СТАТИСТИКА ----------------

    public long getBalance() {
        return Math.max(0L, prefs.getLong("balance", 0L));
    }

    public long getTrips() {
        return Math.max(0L, prefs.getLong("trips", 0L));
    }

    public long getSeconds() {
        return Math.max(0L, prefs.getLong("seconds", 0L));
    }

    public long getShiftEarned() {
        return Math.max(0L, prefs.getLong("shiftEarned", 0L));
    }

    public long getShiftTrips() {
        return Math.max(0L, prefs.getLong("shiftTrips", 0L));
    }

    public boolean isRunning() {
        return prefs.getBoolean("running", false);
    }

    public long getStartAt() {
        return prefs.getLong("startAt", 0L);
    }

    public long getShiftStartedAt() {
        return prefs.getLong("shiftStartedAt", 0L);
    }

    // ---------------- СБРОС ТЕКУЩЕЙ СМЕНЫ ----------------

    /**
     * Сбрасывает только показатели текущей смены.
     *
     * Общий баланс, общее количество кругов,
     * историю рейсов и историю смен не изменяет.
     */
    public synchronized boolean resetCurrentStats() {
        return resetShiftStats();
    }

    /**
     * Отдельный метод сброса показателей текущей смены.
     */
    public synchronized boolean resetShiftStats() {
        return prefs.edit()
                .putLong("seconds", 0L)
                .putLong("shiftEarned", 0L)
                .putLong("shiftTrips", 0L)
                .putLong("shiftStartedAt", 0L)
                .putLong("startAt", 0L)
                .putBoolean("running", false)
                .commit();
    }

    // ---------------- СБРОС НАСТРОЕК ----------------

    /**
     * Возвращает настройки приложения к значениям по умолчанию.
     *
     * Не очищает:
     * - общий баланс;
     * - общее количество кругов;
     * - статистику и историю рейсов;
     * - историю завершённых смен;
     * - сохранённые пользовательские цели;
     * - журнал операций базы данных.
     */
    public synchronized boolean resetSettingsToDefaults() {
        return prefs.edit()
                .putInt("pay", 15000)
                .putLong("goal", 5000000L)
                .putInt("theme", 0)

                .putBoolean("panel_enabled", false)

                .putBoolean("sound_enabled", true)
                .putBoolean("vibration_enabled", true)
                .putInt("sound_volume", 70)

                .putInt("panel_size", 100)
                .putInt("panel_opacity", 100)

                .putBoolean("notifications_enabled", true)

                .putBoolean("home_block_balance", true)
                .putBoolean("home_block_rounds", true)
                .putBoolean("home_block_payout", true)
                .putBoolean("home_block_timer", true)
                .putBoolean("home_block_overlay", true)

                .remove("home_block_order")

                .remove("panel_tab_x")
                .remove("panel_tab_y")
                .remove("panel_window_x")
                .remove("panel_window_y")

                .commit();
    }

    // ---------------- ПОЛНЫЙ СБРОС PREFERENCES ----------------

    /**
     * Полностью очищает SharedPreferences.
     *
     * Использовать только при полном сбросе приложения
     * после отдельного предупреждения и подтверждения пользователя.
     *
     * Маркер миграции сохраняется, чтобы старые записи,
     * перенесённые из прежней версии приложения, не появились повторно.
     */
    public synchronized boolean clearAllPreferences() {
        return prefs.edit()
                .clear()
                .putBoolean("database_migration_v1", true)
                .commit();
    }

    // ---------------- ПЛАВАЮЩАЯ ПАНЕЛЬ ----------------

    public boolean isPanelEnabled() {
        return prefs.getBoolean("panel_enabled", false);
    }

    public void setPanelEnabled(boolean enabled) {
        prefs.edit()
                .putBoolean("panel_enabled", enabled)
                .apply();
    }

    // ---------------- УНИВЕРСАЛЬНЫЕ НАСТРОЙКИ ----------------

    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }

    public long getLong(String key, long defaultValue) {
        return prefs.getLong(key, defaultValue);
    }

    public void putBoolean(String key, boolean value) {
        prefs.edit()
                .putBoolean(key, value)
                .apply();
    }

    public void putInt(String key, int value) {
        prefs.edit()
                .putInt(key, value)
                .apply();
    }

    public void putLong(String key, long value) {
        prefs.edit()
                .putLong(key, value)
                .apply();
    }

    public SharedPreferences getPreferences() {
        return prefs;
    }
}
