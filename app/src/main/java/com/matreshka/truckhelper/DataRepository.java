
package com.matreshka.truckhelper;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

import java.util.ArrayList;

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
                if (instance == null) {
                    instance = new DataRepository(context);
                }
            }
        }
        return instance;
    }

    // ---------------- ИНИЦИАЛИЗАЦИЯ ----------------

    public synchronized boolean initialize() {
        return database.migrateOldData(context);
    }

    // ---------------- НАСТРОЙКИ ----------------

    public AppPrefs getPrefs() {
        return prefs;
    }

    public int getPay() {
        return prefs.getPay();
    }

    public void setPay(int value) {
        prefs.setPay(value);
    }

    public long getGoal() {
        return prefs.getGoal();
    }

    public void setGoal(long value) {
        prefs.setGoal(value);
    }

    public int getTheme() {
        return prefs.getTheme();
    }

    public void setTheme(int value) {
        prefs.setTheme(value);
    }

    // ---------------- ОСНОВНАЯ СТАТИСТИКА ----------------

    public long getBalance() {
        return prefs.getBalance();
    }

    public long getTripsCount() {
        return prefs.getTrips();
    }

    public long getSeconds() {
        return prefs.getSeconds();
    }

    public long getShiftEarned() {
        return prefs.getShiftEarned();
    }

    public long getShiftTrips() {
        return prefs.getShiftTrips();
    }

    public boolean isRunning() {
        return prefs.isRunning();
    }

    public long getStartAt() {
        return prefs.getStartAt();
    }

    public long getShiftStartedAt() {
        return getLong("shiftStartedAt", 0L);
    }

    public void setBalance(long value) {
        prefs.getPreferences().edit()
                .putLong("balance", Math.max(0L, value))
                .apply();
    }

    public void setTripsCount(long value) {
        prefs.getPreferences().edit()
                .putLong("trips", Math.max(0L, value))
                .apply();
    }

    public void setSeconds(long value) {
        prefs.getPreferences().edit()
                .putLong("seconds", Math.max(0L, value))
                .apply();
    }

    public void setShiftEarned(long value) {
        prefs.getPreferences().edit()
                .putLong("shiftEarned", Math.max(0L, value))
                .apply();
    }

    public void setShiftTrips(long value) {
        prefs.getPreferences().edit()
                .putLong("shiftTrips", Math.max(0L, value))
                .apply();
    }

    public void setShiftStartedAt(long value) {
        prefs.getPreferences().edit()
                .putLong("shiftStartedAt", Math.max(0L, value))
                .apply();
    }

    public void setRunning(boolean value) {
        prefs.getPreferences().edit()
                .putBoolean("running", value)
                .apply();
    }

    public void setStartAt(long value) {
        prefs.getPreferences().edit()
                .putLong("startAt", Math.max(0L, value))
                .apply();
    }

    // ---------------- СБРОС ТЕКУЩЕЙ СМЕНЫ ----------------
    // Общий баланс, общее количество кругов и история
    // при этом не изменяются.

    public synchronized boolean resetCurrentStats() {
        SharedPreferences.Editor editor =
                prefs.getPreferences().edit();

        editor.putLong("seconds", 0L);
        editor.putLong("shiftEarned", 0L);
        editor.putLong("shiftTrips", 0L);
        editor.putLong("shiftStartedAt", 0L);
        editor.putLong("startAt", 0L);
        editor.putBoolean("running", false);

        return editor.commit();
    }

    // ---------------- УНИВЕРСАЛЬНЫЙ ДОСТУП ----------------

    public SharedPreferences getPreferences() {
        return prefs.getPreferences();
    }

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
        prefs.putBoolean(key, value);
    }

    public void putInt(String key, int value) {
        prefs.putInt(key, value);
    }

    public void putLong(String key, long value) {
        prefs.putLong(key, value);
    }

    // ---------------- ИСТОРИЯ РЕЙСОВ ----------------

    public synchronized long addTrip(
            long amount,
            long createdAt,
            long duration,
            String route,
            String note,
            String source
    ) {
        return database.addTrip(
                amount,
                createdAt,
                duration,
                route,
                note,
                source
        );
    }

    public synchronized boolean deleteTrip(long id) {
        return database.deleteTrip(id);
    }

    public synchronized boolean deleteTrip(
            long id,
            String expectedSource
    ) {
        return database.deleteTrip(id, expectedSource);
    }

    public synchronized Cursor getTripsHistory() {
        return database.getTrips();
    }

    public synchronized long getHistoryCount() {
        return database.getTripCount();
    }

    // ---------------- ЖУРНАЛ ВОССТАНОВЛЕНИЯ ПАНЕЛИ ----------------

    public synchronized DatabaseHelper.PanelOperation
    addPanelTripWithJournal(
            long amount,
            long createdAt,
            long newBalance,
            long newTrips,
            long newShiftEarned,
            long newShiftTrips,
            long shiftStartedAt,
            String previousUndoStack
    ) {
        return database.addPanelTripWithJournal(
                amount,
                createdAt,
                newBalance,
                newTrips,
                newShiftEarned,
                newShiftTrips,
                shiftStartedAt,
                previousUndoStack
        );
    }

    public synchronized DatabaseHelper.PanelOperation
    undoPanelTripWithJournal(
            long tripId,
            long amount,
            long newBalance,
            long newTrips,
            long newShiftEarned,
            long newShiftTrips,
            long shiftStartedAt,
            String newUndoStack
    ) {
        return database.undoPanelTripWithJournal(
                tripId,
                amount,
                newBalance,
                newTrips,
                newShiftEarned,
                newShiftTrips,
                shiftStartedAt,
                newUndoStack
        );
    }

    public synchronized ArrayList<DatabaseHelper.PanelOperation>
    getPendingPanelOperations() {
        return database.getPendingPanelOperations();
    }

    public synchronized boolean markPanelOperationApplied(
            String operationId
    ) {
        return database.markPanelOperationApplied(operationId);
    }

    // ---------------- СБРОС ПОКАЗАТЕЛЕЙ ПО ДНЯМ ----------------

    /**
     * Сохраняет отметку сброса выбранного показателя
     * за конкретный календарный день.
     *
     * История рейсов и заработка не удаляется.
     *
     * dayStart — начало дня в миллисекундах.
     * metric — идентификатор показателя.
     * resetAt — время сброса в миллисекундах.
     */
    public synchronized boolean saveDailyMetricReset(
            long dayStart,
            String metric,
            long resetAt
    ) {
        return database.saveDailyMetricReset(
                dayStart,
                metric,
                resetAt
        );
    }

    /**
     * Получает время последнего сброса показателя за день.
     * Возвращает 0, если сброса не было.
     */
    public synchronized long getDailyMetricReset(
            long dayStart,
            String metric
    ) {
        return database.getDailyMetricReset(
                dayStart,
                metric
        );
    }

    /**
     * Удаляет только отметку сброса выбранного показателя
     * за выбранный день. История остаётся нетронутой.
     */
    public synchronized boolean deleteDailyMetricReset(
            long dayStart,
            String metric
    ) {
        return database.deleteDailyMetricReset(
                dayStart,
                metric
        );
    }

    // ---------------- ИСТОРИЯ СМЕН ----------------

    public synchronized long addShift(
            long earned,
            long tripCount,
            long duration,
            long startedAt,
            long finishedAt,
            String note
    ) {
        return database.addShift(
                earned,
                tripCount,
                duration,
                startedAt,
                finishedAt,
                note
        );
    }

    public synchronized long addShift(
            long earned,
            long tripCount,
            long duration,
            long startedAt,
            long finishedAt,
            String note,
            String shiftKey
    ) {
        return database.addShift(
                earned,
                tripCount,
                duration,
                startedAt,
                finishedAt,
                note,
                shiftKey
        );
    }

    public synchronized long addShiftOnce(
            long earned,
            long tripCount,
            long duration,
            long startedAt,
            long finishedAt,
            String note,
            String shiftKey
    ) {
        return addShift(
                earned,
                tripCount,
                duration,
                startedAt,
                finishedAt,
                note,
                shiftKey
        );
    }

    public synchronized Cursor getShiftsHistory() {
        return database.getShifts();
    }

    public synchronized long getShiftsCount() {
        return database.getShiftCount();
    }

    // ---------------- ПОСЛЕДНЯЯ ЗАПИСЬ ----------------

    public synchronized long getLatestTripId() {
        return database.getLatestTripId();
    }

    // ---------------- ОЧИСТКА ИСТОРИИ ----------------
    // Эти методы предназначены только для полного сброса
    // после отдельного предупреждения и подтверждения.

    public synchronized void clearTripsHistory() {
        database.clearTrips();
    }

    public synchronized void clearShiftsHistory() {
        database.clearShifts();
    }

    // ---------------- ПОЛНЫЙ СБРОС БАЗЫ ----------------

    /**
     * Полностью очищает SQLite после подтверждения пользователя.
     *
     * Обычный сброс настроек и сброс отдельных показателей
     * не должны вызывать этот метод.
     *
     * SharedPreferences здесь не очищаются.
     * Их сброс будет отдельно согласован с AppPrefs и MainActivity.
     */
    public synchronized boolean clearAllDatabaseData() {
        return database.clearAllData();
    }

    // ---------------- ЗАКРЫТИЕ БАЗЫ ----------------

    public synchronized void close() {
        database.close();
    }
}
