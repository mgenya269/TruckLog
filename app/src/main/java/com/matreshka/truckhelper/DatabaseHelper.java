package com.matreshka.truckhelper;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.UUID;

public final class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "trucklog.db";
    private static final int DATABASE_VERSION = 5;

    private static final String TABLE_TRIPS = "trips_history";
    private static final String TABLE_SHIFTS = "shifts_history";
    private static final String TABLE_PANEL_OPERATIONS = "panel_operations";
    private static final String TABLE_DAILY_STAT_RESETS = "daily_stat_resets";

    private static final String KEY_MIGRATION = "database_migration_v1";

    public static final class PanelOperation {
        public String operationId;
        public long tripId;
        public long amount;
        public long balance;
        public long trips;
        public long shiftEarned;
        public long shiftTrips;
        public long shiftStartedAt;
        public String undoStack;

        PanelOperation(
                String operationId,
                long tripId,
                long amount,
                long balance,
                long trips,
                long shiftEarned,
                long shiftTrips,
                long shiftStartedAt,
                String undoStack
        ) {
            this.operationId = operationId;
            this.tripId = tripId;
            this.amount = amount;
            this.balance = balance;
            this.trips = trips;
            this.shiftEarned = shiftEarned;
            this.shiftTrips = shiftTrips;
            this.shiftStartedAt = shiftStartedAt;
            this.undoStack = undoStack;
        }
    }

    public DatabaseHelper(Context context) {
        super(
                context.getApplicationContext(),
                DATABASE_NAME,
                null,
                DATABASE_VERSION
        );
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL("CREATE TABLE " + TABLE_TRIPS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "amount INTEGER NOT NULL DEFAULT 0, " +
                "created_at INTEGER NOT NULL DEFAULT 0, " +
                "duration INTEGER NOT NULL DEFAULT 0, " +
                "route TEXT NOT NULL DEFAULT '', " +
                "note TEXT NOT NULL DEFAULT '', " +
                "source TEXT NOT NULL DEFAULT 'manual', " +
                "legacy_index INTEGER DEFAULT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_SHIFTS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "earned INTEGER NOT NULL DEFAULT 0, " +
                "trip_count INTEGER NOT NULL DEFAULT 0, " +
                "duration INTEGER NOT NULL DEFAULT 0, " +
                "started_at INTEGER NOT NULL DEFAULT 0, " +
                "finished_at INTEGER NOT NULL DEFAULT 0, " +
                "note TEXT NOT NULL DEFAULT '', " +
                "shift_key TEXT DEFAULT NULL)");

        createPanelOperationsTable(db);
        createDailyStatResetsTable(db);
        createIndexes(db);
    }

    private void createPanelOperationsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " +
                TABLE_PANEL_OPERATIONS + " (" +
                "operation_id TEXT PRIMARY KEY, " +
                "trip_id INTEGER NOT NULL DEFAULT -1, " +
                "amount INTEGER NOT NULL DEFAULT 0, " +
                "balance INTEGER NOT NULL DEFAULT 0, " +
                "trips INTEGER NOT NULL DEFAULT 0, " +
                "shift_earned INTEGER NOT NULL DEFAULT 0, " +
                "shift_trips INTEGER NOT NULL DEFAULT 0, " +
                "shift_started_at INTEGER NOT NULL DEFAULT 0, " +
                "undo_stack TEXT NOT NULL DEFAULT '', " +
                "created_at INTEGER NOT NULL DEFAULT 0, " +
                "applied INTEGER NOT NULL DEFAULT 0)");
    }

    /*
     * Таблица хранит не удаление истории, а время,
     * с которого выбранный показатель считается сброшенным
     * для конкретного календарного дня.
     *
     * day_start — начало дня в миллисекундах.
     * metric — идентификатор показателя.
     * reset_at — время выполнения сброса.
     */
    private void createDailyStatResetsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " +
                TABLE_DAILY_STAT_RESETS + " (" +
                "day_start INTEGER NOT NULL, " +
                "metric TEXT NOT NULL, " +
                "reset_at INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(day_start, metric))");
    }

    private void createIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_trips_created_at " +
                "ON " + TABLE_TRIPS + "(created_at)");

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_shifts_finished_at " +
                "ON " + TABLE_SHIFTS + "(finished_at)");

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_trips_legacy " +
                "ON " + TABLE_TRIPS + "(legacy_index)");

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_shifts_key " +
                "ON " + TABLE_SHIFTS + "(shift_key)");

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_panel_operations_applied " +
                "ON " + TABLE_PANEL_OPERATIONS + "(applied, created_at)");

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_daily_stat_resets_day " +
                "ON " + TABLE_DAILY_STAT_RESETS + "(day_start)");
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion
    ) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_TRIPS +
                    " ADD COLUMN legacy_index INTEGER DEFAULT NULL");
        }

        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE_SHIFTS +
                    " ADD COLUMN shift_key TEXT DEFAULT NULL");
        }

        if (oldVersion < 4) {
            createPanelOperationsTable(db);
        }

        if (oldVersion < 5) {
            createDailyStatResetsTable(db);
        }

        createIndexes(db);
    }

    // ---------------- ПЕРЕНОС СТАРЫХ ДАННЫХ ----------------

    public synchronized boolean migrateOldData(Context context) {

        Context appContext = context.getApplicationContext();

        SharedPreferences prefs = appContext.getSharedPreferences(
                "truck_stats",
                Context.MODE_PRIVATE
        );

        if (prefs.getBoolean(KEY_MIGRATION, false)) {
            return true;
        }

        SQLiteDatabase db = getWritableDatabase();
        boolean committed = false;

        db.beginTransaction();

        try {
            int count = prefs.getInt(
                    "records_count",
                    prefs.getInt("record_count", 200)
            );

            count = Math.max(0, Math.min(count, 200));

            for (int i = 0; i < count; i++) {

                String amountKey = "record_amount_" + i;
                String timeKey = "record_time_" + i;
                String durationKey = "record_duration_" + i;

                if (!prefs.contains(amountKey) ||
                        !prefs.contains(timeKey)) {
                    continue;
                }

                long amount = prefs.getLong(amountKey, 0L);
                long createdAt = prefs.getLong(timeKey, 0L);
                long duration = prefs.getLong(durationKey, 0L);

                if (amount <= 0 || createdAt <= 0) {
                    continue;
                }

                ContentValues values = new ContentValues();
                values.put("amount", amount);
                values.put("created_at", createdAt);
                values.put("duration", Math.max(0L, duration));
                values.put("route", "");
                values.put("note", "");
                values.put("source", "legacy");
                values.put("legacy_index", i);

                db.insertWithOnConflict(
                        TABLE_TRIPS,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_IGNORE
                );
            }

            db.setTransactionSuccessful();
            committed = true;

        } catch (Exception e) {
            committed = false;

        } finally {
            db.endTransaction();
        }

        if (!committed) {
            return false;
        }

        return prefs.edit()
                .putBoolean(KEY_MIGRATION, true)
                .commit();
    }

    // ---------------- РЕЙСЫ ----------------

    public synchronized long addTrip(
            long amount,
            long createdAt,
            long duration,
            String route,
            String note,
            String source
    ) {
        if (amount <= 0 || createdAt <= 0) {
            return -1;
        }

        ContentValues values = new ContentValues();
        values.put("amount", amount);
        values.put("created_at", createdAt);
        values.put("duration", Math.max(0L, duration));
        values.put("route", route == null ? "" : route);
        values.put("note", note == null ? "" : note);
        values.put("source", source == null ? "manual" : source);
        values.putNull("legacy_index");

        return getWritableDatabase().insert(
                TABLE_TRIPS,
                null,
                values
        );
    }

    public synchronized boolean deleteTrip(long id) {
        if (id <= 0) return false;

        return getWritableDatabase().delete(
                TABLE_TRIPS,
                "id = ?",
                new String[]{String.valueOf(id)}
        ) > 0;
    }

    public synchronized boolean deleteTrip(
            long id,
            String expectedSource
    ) {
        if (id <= 0 ||
                expectedSource == null ||
                expectedSource.trim().isEmpty()) {
            return false;
        }

        return getWritableDatabase().delete(
                TABLE_TRIPS,
                "id = ? AND source = ?",
                new String[]{
                        String.valueOf(id),
                        expectedSource
                }
        ) > 0;
    }

    // ---------------- ЗАЩИЩЁННОЕ ДОБАВЛЕНИЕ КРУГА ПАНЕЛИ ----------------

    public synchronized PanelOperation addPanelTripWithJournal(
            long amount,
            long createdAt,
            long balance,
            long trips,
            long shiftEarned,
            long shiftTrips,
            long shiftStartedAt,
            String previousUndoStack
    ) {
        if (amount <= 0 ||
                createdAt <= 0 ||
                balance < 0 ||
                trips < 0 ||
                shiftEarned < 0 ||
                shiftTrips < 0) {
            return null;
        }

        SQLiteDatabase db = getWritableDatabase();
        String operationId = UUID.randomUUID().toString();

        db.beginTransaction();

        try {
            ContentValues tripValues = new ContentValues();
            tripValues.put("amount", amount);
            tripValues.put("created_at", createdAt);
            tripValues.put("duration", 0);
            tripValues.put("route", "");
            tripValues.put("note", "");
            tripValues.put("source", "floating_panel");
            tripValues.putNull("legacy_index");

            long tripId = db.insert(TABLE_TRIPS, null, tripValues);

            if (tripId <= 0) {
                return null;
            }

            StringBuilder stack = new StringBuilder(
                    previousUndoStack == null
                            ? ""
                            : previousUndoStack
            );

            if (stack.length() > 0) {
                stack.append(";");
            }

            stack.append(tripId).append(":").append(amount);

            ContentValues operation = new ContentValues();
            operation.put("operation_id", operationId);
            operation.put("trip_id", tripId);
            operation.put("amount", amount);
            operation.put("balance", balance);
            operation.put("trips", trips);
            operation.put("shift_earned", shiftEarned);
            operation.put("shift_trips", shiftTrips);
            operation.put("shift_started_at", shiftStartedAt);
            operation.put("undo_stack", stack.toString());
            operation.put("created_at", createdAt);
            operation.put("applied", 0);

            long journalId = db.insert(
                    TABLE_PANEL_OPERATIONS,
                    null,
                    operation
            );

            if (journalId <= 0) {
                return null;
            }

            db.setTransactionSuccessful();

            return new PanelOperation(
                    operationId,
                    tripId,
                    amount,
                    balance,
                    trips,
                    shiftEarned,
                    shiftTrips,
                    shiftStartedAt,
                    stack.toString()
            );

        } catch (Exception e) {
            return null;

        } finally {
            db.endTransaction();
        }
    }

    // ---------------- ЗАЩИЩЁННАЯ ОТМЕНА КРУГА ПАНЕЛИ ----------------

    public synchronized PanelOperation undoPanelTripWithJournal(
            long tripId,
            long amount,
            long balance,
            long trips,
            long shiftEarned,
            long shiftTrips,
            long shiftStartedAt,
            String newUndoStack
    ) {
        if (tripId <= 0 ||
                amount <= 0 ||
                balance < 0 ||
                trips < 0 ||
                shiftEarned < 0 ||
                shiftTrips < 0) {
            return null;
        }

        SQLiteDatabase db = getWritableDatabase();
        String operationId = UUID.randomUUID().toString();

        db.beginTransaction();

        try {
            ContentValues operation = new ContentValues();
            operation.put("operation_id", operationId);
            operation.put("trip_id", tripId);
            operation.put("amount", amount);
            operation.put("balance", balance);
            operation.put("trips", trips);
            operation.put("shift_earned", shiftEarned);
            operation.put("shift_trips", shiftTrips);
            operation.put("shift_started_at", shiftStartedAt);
            operation.put(
                    "undo_stack",
                    newUndoStack == null ? "" : newUndoStack
            );
            operation.put("created_at", System.currentTimeMillis());
            operation.put("applied", 0);

            long journalId = db.insert(
                    TABLE_PANEL_OPERATIONS,
                    null,
                    operation
            );

            if (journalId <= 0) {
                return null;
            }

            int deleted = db.delete(
                    TABLE_TRIPS,
                    "id = ? AND source = ?",
                    new String[]{
                            String.valueOf(tripId),
                            "floating_panel"
                    }
            );

            if (deleted != 1) {
                return null;
            }

            db.setTransactionSuccessful();

            return new PanelOperation(
                    operationId,
                    tripId,
                    amount,
                    balance,
                    trips,
                    shiftEarned,
                    shiftTrips,
                    shiftStartedAt,
                    newUndoStack == null ? "" : newUndoStack
            );

        } catch (Exception e) {
            return null;

        } finally {
            db.endTransaction();
        }
    }

    // ---------------- ВОССТАНОВЛЕНИЕ ОПЕРАЦИЙ ----------------

    public synchronized ArrayList<PanelOperation>
    getPendingPanelOperations() {

        ArrayList<PanelOperation> result = new ArrayList<>();

        try (Cursor cursor = getReadableDatabase().query(
                TABLE_PANEL_OPERATIONS,
                new String[]{
                        "operation_id",
                        "trip_id",
                        "amount",
                        "balance",
                        "trips",
                        "shift_earned",
                        "shift_trips",
                        "shift_started_at",
                        "undo_stack"
                },
                "applied = ?",
                new String[]{"0"},
                null,
                null,
                "created_at ASC, rowid ASC"
        )) {
            while (cursor.moveToNext()) {
                result.add(new PanelOperation(
                        cursor.getString(0),
                        cursor.getLong(1),
                        cursor.getLong(2),
                        cursor.getLong(3),
                        cursor.getLong(4),
                        cursor.getLong(5),
                        cursor.getLong(6),
                        cursor.getLong(7),
                        cursor.getString(8)
                ));
            }
        }

        return result;
    }

    public synchronized boolean markPanelOperationApplied(
            String operationId
    ) {
        if (operationId == null || operationId.trim().isEmpty()) {
            return false;
        }

        ContentValues values = new ContentValues();
        values.put("applied", 1);

        return getWritableDatabase().update(
                TABLE_PANEL_OPERATIONS,
                values,
                "operation_id = ? AND applied = 0",
                new String[]{operationId}
        ) == 1;
    }

    // ---------------- СБРОС ПОКАЗАТЕЛЕЙ ОТДЕЛЬНОГО ДНЯ ----------------

    /**
     * Сохраняет отметку сброса одного показателя за выбранный день.
     * История рейсов при этом не удаляется и не изменяется.
     *
     * dayStart — timestamp начала выбранного дня в миллисекундах.
     * metric — уникальное имя показателя, например "rounds" или "earned".
     * resetAt — timestamp момента сброса в миллисекундах.
     */
    public synchronized boolean saveDailyMetricReset(
            long dayStart,
            String metric,
            long resetAt
    ) {
        if (dayStart <= 0 ||
                resetAt <= 0 ||
                metric == null ||
                metric.trim().isEmpty()) {
            return false;
        }

        ContentValues values = new ContentValues();
        values.put("day_start", dayStart);
        values.put("metric", metric.trim());
        values.put("reset_at", resetAt);

        long result = getWritableDatabase().insertWithOnConflict(
                TABLE_DAILY_STAT_RESETS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );

        return result != -1;
    }

    /**
     * Возвращает timestamp последнего сброса показателя за день.
     * Если отметки нет, возвращает 0.
     */
    public synchronized long getDailyMetricReset(
            long dayStart,
            String metric
    ) {
        if (dayStart <= 0 ||
                metric == null ||
                metric.trim().isEmpty()) {
            return 0L;
        }

        try (Cursor cursor = getReadableDatabase().query(
                TABLE_DAILY_STAT_RESETS,
                new String[]{"reset_at"},
                "day_start = ? AND metric = ?",
                new String[]{
                        String.valueOf(dayStart),
                        metric.trim()
                },
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst()
                    ? cursor.getLong(0)
                    : 0L;
        }
    }

    /**
     * Удаляет только отметку сброса указанного показателя за указанный день.
     * История рейсов не затрагивается.
     */
    public synchronized boolean deleteDailyMetricReset(
            long dayStart,
            String metric
    ) {
        if (dayStart <= 0 ||
                metric == null ||
                metric.trim().isEmpty()) {
            return false;
        }

        return getWritableDatabase().delete(
                TABLE_DAILY_STAT_RESETS,
                "day_start = ? AND metric = ?",
                new String[]{
                        String.valueOf(dayStart),
                        metric.trim()
                }
        ) > 0;
    }

    // ---------------- СМЕНЫ ----------------

    public synchronized long addShift(
            long earned,
            long tripCount,
            long duration,
            long startedAt,
            long finishedAt,
            String note
    ) {
        if (earned < 0 || tripCount < 0) return -1;

        ContentValues values = createShiftValues(
                earned,
                tripCount,
                duration,
                startedAt,
                finishedAt,
                note
        );

        return getWritableDatabase().insert(
                TABLE_SHIFTS,
                null,
                values
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
        if (earned < 0 ||
                tripCount < 0 ||
                shiftKey == null ||
                shiftKey.trim().isEmpty()) {
            return -1;
        }

        SQLiteDatabase db = getWritableDatabase();

        long existingId = getShiftIdByKey(db, shiftKey);

        if (existingId > 0) {
            return existingId;
        }

        ContentValues values = createShiftValues(
                earned,
                tripCount,
                duration,
                startedAt,
                finishedAt,
                note
        );

        values.put("shift_key", shiftKey);

        long insertedId = db.insertWithOnConflict(
                TABLE_SHIFTS,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );

        if (insertedId > 0) {
            return insertedId;
        }

        return getShiftIdByKey(db, shiftKey);
    }

    private ContentValues createShiftValues(
            long earned,
            long tripCount,
            long duration,
            long startedAt,
            long finishedAt,
            String note
    ) {
        ContentValues values = new ContentValues();
        values.put("earned", earned);
        values.put("trip_count", tripCount);
        values.put("duration", Math.max(0L, duration));
        values.put("started_at", Math.max(0L, startedAt));
        values.put("finished_at", Math.max(0L, finishedAt));
        values.put("note", note == null ? "" : note);

        return values;
    }

    private long getShiftIdByKey(
            SQLiteDatabase db,
            String shiftKey
    ) {
        try (Cursor cursor = db.query(
                TABLE_SHIFTS,
                new String[]{"id"},
                "shift_key = ?",
                new String[]{shiftKey},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst()
                    ? cursor.getLong(0)
                    : -1;
        }
    }

    // ---------------- ЧТЕНИЕ ИСТОРИИ ----------------

    public synchronized Cursor getTrips() {
        return getReadableDatabase().query(
                TABLE_TRIPS,
                null,
                null,
                null,
                null,
                null,
                "created_at DESC, id DESC"
        );
    }

    public synchronized Cursor getShifts() {
        return getReadableDatabase().query(
                TABLE_SHIFTS,
                null,
                null,
                null,
                null,
                null,
                "finished_at DESC, id DESC"
        );
    }

    public synchronized long getTripCount() {
        return getCount(TABLE_TRIPS);
    }

    public synchronized long getShiftCount() {
        return getCount(TABLE_SHIFTS);
    }

    private long getCount(String table) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + table,
                null
        )) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    public synchronized long getLatestTripId() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id FROM " + TABLE_TRIPS +
                        " ORDER BY id DESC LIMIT 1",
                null
        )) {
            return cursor.moveToFirst()
                    ? cursor.getLong(0)
                    : -1;
        }
    }

    // ---------------- ПОЛНАЯ ОЧИСТКА ДАННЫХ БАЗЫ ----------------

    /**
     * Полностью очищает данные SQLite в одной транзакции.
     *
     * ВНИМАНИЕ:
     * Метод предназначен исключительно для подтверждённого
     * пользователем полного сброса приложения.
     *
     * Обычный сброс статистики или настроек не должен вызывать
     * этот метод.
     *
     * SharedPreferences здесь намеренно не очищаются:
     * ими управляют AppPrefs и MainActivity отдельно.
     */
    public synchronized boolean clearAllData() {

        SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();

        try {
            db.delete(TABLE_TRIPS, null, null);
            db.delete(TABLE_SHIFTS, null, null);
            db.delete(TABLE_PANEL_OPERATIONS, null, null);
            db.delete(TABLE_DAILY_STAT_RESETS, null, null);

            // Сбрасываем счётчики AUTOINCREMENT после удаления данных.
            db.delete(
                    "sqlite_sequence",
                    "name IN (?, ?)",
                    new String[]{
                            TABLE_TRIPS,
                            TABLE_SHIFTS
                    }
            );

            db.setTransactionSuccessful();
            return true;

        } catch (Exception e) {
            return false;

        } finally {
            db.endTransaction();
        }
    }

    // ---------------- ОТДЕЛЬНАЯ ОЧИСТКА ИСТОРИИ ----------------

    public synchronized void clearTrips() {
        getWritableDatabase().delete(TABLE_TRIPS, null, null);
    }

    public synchronized void clearShifts() {
        getWritableDatabase().delete(TABLE_SHIFTS, null, null);
    }

    @Override
    public synchronized void close() {
        super.close();
    }
}
