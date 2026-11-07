package com.matreshka.truckhelper;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "trucklog.db";
    private static final int DATABASE_VERSION = 3;

    private static final String TABLE_TRIPS = "trips_history";
    private static final String TABLE_SHIFTS = "shifts_history";
    private static final String KEY_MIGRATION = "database_migration_v1";

    public DatabaseHelper(Context context) {
        super(context.getApplicationContext(),
                DATABASE_NAME, null, DATABASE_VERSION);
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

        createIndexes(db);
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

        createIndexes(db);
    }

    public synchronized boolean migrateOldData(Context context) {

        Context appContext = context.getApplicationContext();

        SharedPreferences prefs = appContext.getSharedPreferences(
                "truck_stats", Context.MODE_PRIVATE);

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
                TABLE_TRIPS, null, values);
    }

    // Старый метод оставлен для совместимости с существующим кодом.
    public synchronized boolean deleteTrip(long id) {
        if (id <= 0) return false;

        return getWritableDatabase().delete(
                TABLE_TRIPS,
                "id = ?",
                new String[]{String.valueOf(id)}
        ) > 0;
    }

    // Защищённое удаление: запись удаляется только при совпадении источника.
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

    // Старый метод сохранения смены оставлен для совместимости.
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
                TABLE_SHIFTS, null, values);
    }

    // Защищённое сохранение смены.
    // Повторный вызов с тем же shiftKey не создаёт новую запись.
    // Вместо этого возвращается ID уже сохранённой смены.
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

        // Если запись уже появилась, возвращаем её ID.
        // Если произошла другая ошибка, вернётся -1.
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
                "SELECT COUNT(*) FROM " + table, null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    public synchronized long getLatestTripId() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id FROM " + TABLE_TRIPS +
                        " ORDER BY id DESC LIMIT 1",
                null
        )) {
            return cursor.moveToFirst() ? cursor.getLong(0) : -1;
        }
    }

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