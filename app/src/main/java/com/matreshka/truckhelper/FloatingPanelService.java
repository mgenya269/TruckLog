package com.matreshka.truckhelper;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.content.*;
import android.provider.Settings;
import android.content.res.ColorStateList;
import android.media.AudioManager;
import android.media.ToneGenerator;

import java.text.NumberFormat;
import java.util.*;

public class FloatingPanelService extends Service {

    private static final String KEY_LAST_PANEL_TRIP = "panel_last_trip_id";
    private static final String KEY_LAST_PANEL_AMOUNT = "panel_last_trip_amount";
    private static final String KEY_SHIFT_STARTED = "shiftStartedAt";
    private static final String KEY_UNDO_STACK = "panel_undo_stack";

    private static final String KEY_TAB_X = "panel_tab_x";
    private static final String KEY_TAB_Y = "panel_tab_y";
    private static final String KEY_PANEL_X = "panel_window_x";
    private static final String KEY_PANEL_Y = "panel_window_y";

    private WindowManager wm;
    private View tab;
    private View panel;

    private WindowManager.LayoutParams tabLp;
    private WindowManager.LayoutParams panelLp;

    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private DataRepository repository;

    private boolean expanded = false;
    private boolean editing = false;

    private TextView earnings;
    private TextView count;
    private TextView progressText;
    private TextView panelTitle;

    private Button round;
    private Button undoButton;
    private Button settingsButton;
    private Button saveButton;
    private Button cancelButton;
    private Button resetButton;
    private Button closeButton;
    private Button dragButton;

    private EditText payInput;
    private EditText goalInput;

    private LinearLayout settingsBox;
    private ProgressBar progressBar;

    private Handler mainHandler;

    private float tabDownX;
    private float tabDownY;
    private int tabStartX;
    private int tabStartY;
    private boolean tabMoved;

    private float panelDownX;
    private float panelDownY;
    private int panelStartX;
    private int panelStartY;
    private boolean panelMoved;

    private static class UndoRecord {
        long id;
        long amount;

        UndoRecord(long id, long amount) {
            this.id = id;
            this.amount = amount;
        }
    }

    private final int[] accents = {
        0xFF8B4DEB, 0xFF475569, 0xFF999999, 0xFFEF4444,
        0xFF22C55E, 0xFFEAB308, 0xFF3B82F6, 0xFF06B6D4,
        0xFFEC4899, 0xFFF97316
    };

    private final int[] backgrounds = {
        0xFF100D1C, 0xFFF1F5F9, 0xFF080808, 0xFF190B10,
        0xFF0B2115, 0xFF211C0A, 0xFF0C1830, 0xFF09232B,
        0xFF2A1022, 0xFF2A170C
    };

    private final int[] cards = {
        0xFF211735, 0xFFFFFFFF, 0xFF1B1B1B, 0xFF2A151C,
        0xFF142D20, 0xFF30280E, 0xFF172849, 0xFF12313A,
        0xFF3A1830, 0xFF3A2113
    };

    private final int[] foregrounds = {
        Color.WHITE, 0xFF172033, Color.WHITE, Color.WHITE,
        Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE,
        Color.WHITE, Color.WHITE
    };

    private int theme() {
        return Math.max(0, Math.min(9, prefs.getInt("theme", 0)));
    }

    private int accent() {
        return accents[theme()];
    }

    private int background() {
        return backgrounds[theme()];
    }

    private int cardColor() {
        return cards[theme()];
    }

    private int fg() {
        return foregrounds[theme()];
    }

    private int dp(float value) {
        return (int) (
            value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView txt(
        String text,
        int size,
        int color,
        boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);

        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }

        return view;
    }

    private Button btn(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(fg());
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTypeface(null, Typeface.BOLD);
        button.setBackground(shape(color, 12));
        return button;
    }

    private String money(long value) {
        return NumberFormat.getIntegerInstance(
            new Locale("ru", "RU")
        ).format(value);
    }

    private boolean canAdd(long first, long second) {
        return first >= 0 &&
            second >= 0 &&
            first <= Long.MAX_VALUE - second;
    }

    private int panelScale() {
        int value = prefs.getInt("panel_size", 100);
        return Math.max(70, Math.min(130, value));
    }

    private int panelOpacity() {
        int value = prefs.getInt("panel_opacity", 100);
        return Math.max(30, Math.min(100, value));
    }

    private int scaledDp(int value) {
        return dp(value * panelScale() / 100f);
    }

    private void applyPanelOpacity(View view) {
        if (view == null || view.getBackground() == null) {
            return;
        }

        int alpha = (int) (255f * panelOpacity() / 100f);
        view.getBackground().setAlpha(alpha);
    }

    // ---------------- ЗВУК И ВИБРАЦИЯ ----------------

    private void playFeedback(boolean success) {
        if (prefs == null) {
            return;
        }

        if (prefs.getBoolean("sound_enabled", true)) {
            int volume = prefs.getInt("sound_volume", 70);
            volume = clamp(volume, 0, 100);

            if (volume > 0) {
                try {
                    ToneGenerator generator = new ToneGenerator(
                        AudioManager.STREAM_NOTIFICATION,
                        volume
                    );

                    generator.startTone(
                        success
                            ? ToneGenerator.TONE_PROP_ACK
                            : ToneGenerator.TONE_PROP_NACK,
                        success ? 100 : 160
                    );

                    if (mainHandler != null) {
                        mainHandler.postDelayed(() -> {
                            try {
                                generator.release();
                            } catch (Exception ignored) {
                            }
                        }, 220);
                    } else {
                        generator.release();
                    }

                } catch (Exception ignored) {
                }
            }
        }

        if (prefs.getBoolean("vibration_enabled", true)) {
            try {
                Vibrator vibrator = (Vibrator) getSystemService(
                    VIBRATOR_SERVICE
                );

                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= 26) {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                success ? 35 : 75,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        );
                    } else {
                        vibrator.vibrate(success ? 35 : 75);
                    }
                }

            } catch (Exception ignored) {
            }
        }
    }

    // ---------------- ИСТОРИЯ ОТМЕНЫ КРУГОВ ----------------

    private ArrayList<UndoRecord> getUndoStack() {
        ArrayList<UndoRecord> result = new ArrayList<>();

        String saved = prefs.getString(KEY_UNDO_STACK, "");

        if (saved != null && !saved.trim().isEmpty()) {
            String[] records = saved.split(";");

            for (String item : records) {
                try {
                    String[] values = item.split(":");

                    if (values.length != 2) {
                        continue;
                    }

                    long id = Long.parseLong(values[0]);
                    long amount = Long.parseLong(values[1]);

                    if (id > 0 && amount > 0) {
                        result.add(new UndoRecord(id, amount));
                    }

                } catch (Exception ignored) {
                }
            }
        }

        if (result.isEmpty()) {
            long oldId = prefs.getLong(KEY_LAST_PANEL_TRIP, -1);
            long oldAmount = prefs.getLong(KEY_LAST_PANEL_AMOUNT, 0);

            if (oldId > 0 && oldAmount > 0) {
                result.add(new UndoRecord(oldId, oldAmount));
            }
        }

        return result;
    }

    private String serializeUndoStack(ArrayList<UndoRecord> stack) {
        StringBuilder builder = new StringBuilder();

        for (UndoRecord record : stack) {
            if (builder.length() > 0) {
                builder.append(";");
            }

            builder.append(record.id)
                .append(":")
                .append(record.amount);
        }

        return builder.toString();
    }

    private void saveUndoStack(ArrayList<UndoRecord> stack) {
        prefs.edit()
            .putString(KEY_UNDO_STACK, serializeUndoStack(stack))
            .remove(KEY_LAST_PANEL_TRIP)
            .remove(KEY_LAST_PANEL_AMOUNT)
            .apply();
    }

    // ---------------- ВОССТАНОВЛЕНИЕ ОПЕРАЦИЙ ПАНЕЛИ ----------------

    private boolean applyPanelOperation(
            DatabaseHelper.PanelOperation operation
    ) {
        if (operation == null ||
            operation.operationId == null ||
            operation.operationId.trim().isEmpty()) {
            return false;
        }

        SharedPreferences.Editor editor = prefs.edit();

        editor.putLong("balance", operation.balance);
        editor.putLong("trips", operation.trips);
        editor.putLong("shiftEarned", operation.shiftEarned);
        editor.putLong("shiftTrips", operation.shiftTrips);
        editor.putLong(KEY_SHIFT_STARTED, operation.shiftStartedAt);

        editor.putString(
            KEY_UNDO_STACK,
            operation.undoStack == null ? "" : operation.undoStack
        );

        editor.remove(KEY_LAST_PANEL_TRIP);
        editor.remove(KEY_LAST_PANEL_AMOUNT);

        if (!editor.commit()) {
            return false;
        }

        return repository.markPanelOperationApplied(
            operation.operationId
        );
    }

    private boolean recoverPendingPanelOperations() {
        try {
            ArrayList<DatabaseHelper.PanelOperation> pending =
                repository.getPendingPanelOperations();

            if (pending == null) {
                return false;
            }

            for (DatabaseHelper.PanelOperation operation : pending) {
                if (!applyPanelOperation(operation)) {
                    return false;
                }
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    // ---------------- ЗАПУСК СЕРВИСА ----------------

    @Override
    public void onCreate() {
        super.onCreate();

        prefs = getSharedPreferences(
            "truck_stats",
            MODE_PRIVATE
        );

        repository = DataRepository.getInstance(this);

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        mainHandler = new Handler(Looper.getMainLooper());

        if (Build.VERSION.SDK_INT >= 23 &&
            !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        if (!repository.initialize()) {
            Toast.makeText(
                this,
                "Ошибка подготовки базы. Данные сохранены, панель не запущена.",
                Toast.LENGTH_LONG
            ).show();

            stopSelf();
            return;
        }

        if (!recoverPendingPanelOperations()) {
            Toast.makeText(
                this,
                "Не удалось восстановить операцию. Панель остановлена.",
                Toast.LENGTH_LONG
            ).show();

            stopSelf();
            return;
        }

        listener = (preferences, key) -> {
            if (key == null) {
                return;
            }

            mainHandler.post(() -> {
                refreshStats();

                if ("theme".equals(key) ||
                    "panel_size".equals(key) ||
                    "panel_opacity".equals(key)) {

                    refreshTheme();
                    refreshPanelSizeAndPosition();
                }
            });
        };

        prefs.registerOnSharedPreferenceChangeListener(listener);

        try {
            makeTab();

            prefs.edit()
                .putBoolean("panel_enabled", true)
                .apply();

        } catch (Exception e) {
            stopSelf();
        }
    }

    private WindowManager.LayoutParams lp(int width, int height) {
        return new WindowManager.LayoutParams(
            width,
            height,
            Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
    }
