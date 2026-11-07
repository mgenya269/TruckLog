
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

    // ---------------- БЕЗОПАСНЫЙ СБРОС ТЕКУЩЕЙ СМЕНЫ ----------------

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
     * Возвращает основные настройки к значениям по умолчанию.
     *
     * Не очищает:
     * - общий баланс;
     * - общее количество кругов;
     * - заработок и историю;
     * - сохранённые цели;
     * - историю смен;
     * - историю рейсов.
     *
     * Дополнительные настройки будут сбрасываться
     * через отдельную логику MainActivity.
     */
    public synchronized boolean resetSettingsToDefaults() {
        return prefs.edit()
                .putInt("pay", 15000)
                .putLong("goal", 5000000L)
                .putInt("theme", 0)
                .commit();
    }

    // ---------------- ПОЛНЫЙ СБРОС PREFERENCES ----------------

    /**
     * Полностью очищает SharedPreferences.
     *
     * Использовать исключительно при полном сбросе приложения
     * после отдельного предупреждения и подтверждения пользователя.
     *
     * Не вызывать при обычном сбросе настроек или статистики.
     */
    public synchronized boolean clearAllPreferences() {
        return prefs.edit()
                .clear()
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
