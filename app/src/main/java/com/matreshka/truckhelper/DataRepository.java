// Файл: DataRepository.java
package com.matreshka.truckhelper;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

public final class DataRepository {

    private static volatile DataRepository instance;
    private final AppPrefs prefs;
    private final DatabaseHelper database;
    private final Context context;

    private DataRepository(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = new AppPrefs(this.context);
        this.database = new DatabaseHelper(this.context);
    }

    public static DataRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (DataRepository.class) {
                if (instance == null) instance = new DataRepository(context);
            }
        }
        return instance;
    }

    public synchronized boolean initialize() { return database.migrateOldData(context); }

    public AppPrefs getPrefs() { return prefs; }
    public int getPay() { return prefs.getPay(); }
    public void setPay(int value) { prefs.setPay(value); }
    public long getGoal() { return prefs.getGoal(); }
    public void setGoal(long value) { prefs.setGoal(value); }
    public int getTheme() { return prefs.getTheme(); }
    public void setTheme(int value) { prefs.setTheme(value); }

    public long getBalance() { return prefs.getBalance(); }
    public long getTripsCount() { return prefs.getTrips(); }
    public long getSeconds() { return prefs.getSeconds(); }
    public long getShiftEarned() { return prefs.getShiftEarned(); }
    public long getShiftTrips() { return prefs.getShiftTrips(); }
    public boolean isRunning() { return prefs.isRunning(); }
    public long getStartAt() { return prefs.getStartAt(); }
    public long getShiftStartedAt() { return getLong("shiftStartedAt", 0L); }

    public void setBalance(long value) { prefs.getPreferences().edit().putLong("balance", Math.max(0L, value)).apply(); }
    public void setTripsCount(long value) { prefs.getPreferences().edit().putLong("trips", Math.max(0L, value)).apply(); }
    public void setSeconds(long value) { prefs.getPreferences().edit().putLong("seconds", Math.max(0L, value)).apply(); }
    public void setShiftEarned(long value) { prefs.getPreferences().edit().putLong("shiftEarned", Math.max(0L, value)).apply(); }
    public void setShiftTrips(long value) { prefs.getPreferences().edit().putLong("shiftTrips", Math.max(0L, value)).apply(); }
    public void setShiftStartedAt(long value) { prefs.getPreferences().edit().putLong("shiftStartedAt", Math.max(0L, value)).apply(); }
    public void setRunning(boolean value) { prefs.getPreferences().edit().putBoolean("running", value).apply(); }
    public void setStartAt(long value) { prefs.getPreferences().edit().putLong("startAt", Math.max(0L, value)).apply(); }

    public synchronized boolean resetCurrentStats() {
        SharedPreferences.Editor editor = prefs.getPreferences().edit();
        editor.putLong("balance", 0L);
        editor.putLong("trips", 0L);
        editor.putLong("seconds", 0L);
        editor.putLong("shiftEarned", 0L);
        editor.putLong("shiftTrips", 0L);
        editor.putLong("shiftStartedAt", 0L);
        editor.putLong("startAt", 0L);
        editor.putBoolean("running", false);
        return editor.commit();
    }

    public SharedPreferences getPreferences() { return prefs.getPreferences(); }
    public boolean getBoolean(String key, boolean defaultValue) { return prefs.getBoolean(key, defaultValue); }
    public int getInt(String key, int defaultValue) { return prefs.getInt(key, defaultValue); }
    public long getLong(String key, long defaultValue) { return prefs.getLong(key, defaultValue); }
    public void putBoolean(String key, boolean value) { prefs.putBoolean(key, value); }
    public void putInt(String key, int value) { prefs.putInt(key, value); }
    public void putLong(String key, long value) { prefs.putLong(key, value); }

    public synchronized long addTrip(long amount, long createdAt, long duration, String route, String note, String source) {
        return database.addTrip(amount, createdAt, duration, route, note, source);
    }

    public synchronized boolean deleteTrip(long id) { return database.deleteTrip(id); }
    public synchronized boolean deleteTrip(long id, String expectedSource) { return database.deleteTrip(id, expectedSource); }

    public synchronized DatabaseHelper.ResetResult resetShift(long shiftStartedAt) {
        return database.resetShift(shiftStartedAt);
    }

    public synchronized int deleteTripsInRange(long start, long end) {
        return database.deleteTripsInRange(start, end);
    }

    public synchronized long getDailyMetricReset(long dayStart, String metric) {
        if (metric == null || dayStart <= 0) return 0L;
        return prefs.getLong("daily_reset_" + metric + "_" + dayStart, 0L);
    }

    public synchronized boolean saveDailyMetricReset(long dayStart, String metric, long resetAt) {
        if (metric == null || dayStart <= 0 || resetAt <= 0) return false;
        return prefs.getPreferences().edit().putLong("daily_reset_" + metric + "_" + dayStart, resetAt).commit();
    }

    public synchronized Cursor getTripsHistory() { return database.getTrips(); }
    public synchronized long getHistoryCount() { return database.getTripCount(); }

    public synchronized long addShift(long earned, long tripCount, long duration, long startedAt, long finishedAt, String note) {
        return database.addShift(earned, tripCount, duration, startedAt, finishedAt, note);
    }

    public synchronized long addShift(long earned, long tripCount, long duration, long startedAt, long finishedAt, String note, String shiftKey) {
        return database.addShift(earned, tripCount, duration, startedAt, finishedAt, note, shiftKey);
    }

    public synchronized long addShiftOnce(long earned, long tripCount, long duration, long startedAt, long finishedAt, String note, String shiftKey) {
        return addShift(earned, tripCount, duration, startedAt, finishedAt, note, shiftKey);
    }

    public synchronized Cursor getShiftsHistory() { return database.getShifts(); }
    public synchronized long getShiftsCount() { return database.getShiftCount(); }
    public synchronized long getLatestTripId() { return database.getLatestTripId(); }
    public synchronized void clearTripsHistory() { database.clearTrips(); }
    public synchronized void clearShiftsHistory() { database.clearShifts(); }
    public synchronized void close() { database.close(); }
}
