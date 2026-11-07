
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

    private boolean applyPanelOperation(DatabaseHelper.PanelOperation operation) {
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

        return repository.markPanelOperationApplied(operation.operationId);
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

    // ---------------- ПОЛОЖЕНИЕ И ПЕРЕМЕЩЕНИЕ ----------------

    private int screenWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int screenHeight() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void saveTabPosition() {
        if (tabLp == null || prefs == null) {
            return;
        }

        prefs.edit()
            .putInt(KEY_TAB_X, tabLp.x)
            .putInt(KEY_TAB_Y, tabLp.y)
            .apply();
    }

    private void savePanelPosition() {
        if (panelLp == null || prefs == null) {
            return;
        }

        prefs.edit()
            .putInt(KEY_PANEL_X, panelLp.x)
            .putInt(KEY_PANEL_Y, panelLp.y)
            .apply();
    }

    private void limitTabPosition() {
        if (tabLp == null) {
            return;
        }

        int maxX = Math.max(0, screenWidth() - tabLp.width);
        int maxY = Math.max(0, screenHeight() - tabLp.height);

        tabLp.x = clamp(tabLp.x, 0, maxX);
        tabLp.y = clamp(tabLp.y, 0, maxY);
    }

    private void limitPanelPosition() {
        if (panelLp == null) {
            return;
        }

        int width = panelLp.width;

        if (width <= 0) {
            width = scaledDp(300);
        }

        int height = 0;

        if (panel != null) {
            height = panel.getHeight();
        }

        if (height <= 0) {
            height = dp(350);
        }

        int maxX = Math.max(0, screenWidth() - width);
        int maxY = Math.max(0, screenHeight() - height);

        panelLp.x = clamp(panelLp.x, 0, maxX);
        panelLp.y = clamp(panelLp.y, 0, maxY);
    }

    private void makeTab() {
        TextView view = txt("🚛", 22, Color.WHITE, true);

        view.setGravity(Gravity.CENTER);
        view.setBackground(shape(accent(), 18));

        tab = view;

        tabLp = lp(dp(52), dp(58));
        tabLp.gravity = Gravity.TOP | Gravity.RIGHT;

        tabLp.x = prefs.getInt(KEY_TAB_X, 0);
        tabLp.y = prefs.getInt(KEY_TAB_Y, dp(180));

        limitTabPosition();

        wm.addView(tab, tabLp);

        tab.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    tabDownX = event.getRawX();
                    tabDownY = event.getRawY();

                    tabStartX = tabLp.x;
                    tabStartY = tabLp.y;

                    tabMoved = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - tabDownX;
                    float dy = event.getRawY() - tabDownY;

                    if (Math.abs(dx) > dp(7) ||
                        Math.abs(dy) > dp(7)) {
                        tabMoved = true;
                    }

                    if (tabMoved) {
                        tabLp.x = tabStartX - (int) dx;
                        tabLp.y = tabStartY + (int) dy;

                        limitTabPosition();

                        try {
                            wm.updateViewLayout(tab, tabLp);
                        } catch (Exception ignored) {
                        }
                    }

                    return true;

                case MotionEvent.ACTION_UP:
                    if (tabMoved) {
                        saveTabPosition();
                    } else {
                        toggle();
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    return true;
            }

            return true;
        });
    }

    private void setPanelDrag(View view) {
        if (view == null) {
            return;
        }

        view.setOnTouchListener((v, event) -> {
            if (panelLp == null || panel == null) {
                return false;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    panelDownX = event.getRawX();
                    panelDownY = event.getRawY();

                    panelStartX = panelLp.x;
                    panelStartY = panelLp.y;

                    panelMoved = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - panelDownX;
                    float dy = event.getRawY() - panelDownY;

                    if (Math.abs(dx) > dp(5) ||
                        Math.abs(dy) > dp(5)) {
                        panelMoved = true;
                    }

                    if (panelMoved) {
                        panelLp.x = panelStartX - (int) dx;
                        panelLp.y = panelStartY + (int) dy;

                        limitPanelPosition();

                        try {
                            wm.updateViewLayout(panel, panelLp);
                        } catch (Exception ignored) {
                        }
                    }

                    return true;

                case MotionEvent.ACTION_UP:
                    if (panelMoved) {
                        savePanelPosition();
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    return true;
            }

            return true;
        });
    }

    private void refreshPanelSizeAndPosition() {
        if (panel == null || panelLp == null) {
            return;
        }

        panelLp.width = scaledDp(300);

        limitPanelPosition();

        try {
            wm.updateViewLayout(panel, panelLp);
        } catch (Exception ignored) {
        }

        applyPanelOpacity(panel);
    }

    // ---------------- ОТКРЫТИЕ И ЗАКРЫТИЕ ----------------

    private void toggle() {
        playFeedback(true);

        if (expanded) {
            removePanel();
            expanded = false;
            editing = false;
        } else {
            makePanel();
            expanded = panel != null;
        }
    }

    private void removePanel() {
        if (panel != null) {
            savePanelPosition();

            try {
                wm.removeView(panel);
            } catch (Exception ignored) {
            }

            panel = null;
        }

        earnings = null;
        count = null;
        progressText = null;
        panelTitle = null;

        round = null;
        undoButton = null;
        settingsButton = null;
        saveButton = null;
        cancelButton = null;
        resetButton = null;
        closeButton = null;
        dragButton = null;

        payInput = null;
        goalInput = null;
        settingsBox = null;
        progressBar = null;
    }

    // ---------------- СОЗДАНИЕ ПАНЕЛИ ----------------

    private void makePanel() {
        try {
            LinearLayout root = new LinearLayout(this);

            root.setOrientation(1);
            root.setPadding(
                scaledDp(13),
                scaledDp(13),
                scaledDp(13),
                scaledDp(13)
            );
            root.setBackground(shape(background(), 20));

            LinearLayout head = new LinearLayout(this);
            head.setGravity(Gravity.CENTER_VERTICAL);

            panelTitle = txt(
                "TruckLog",
                15,
                fg(),
                true
            );

            head.addView(
                panelTitle,
                new LinearLayout.LayoutParams(0, -2, 1)
            );

            dragButton = btn("☰", cardColor());

            LinearLayout.LayoutParams dragParams =
                new LinearLayout.LayoutParams(
                    scaledDp(42),
                    scaledDp(40)
                );

            // Кнопка перемещения немного ниже.
            dragParams.topMargin = dp(4);
            head.addView(dragButton, dragParams);

            setPanelDrag(dragButton);

            closeButton = btn("×", cardColor());

            LinearLayout.LayoutParams closeParams =
                new LinearLayout.LayoutParams(
                    scaledDp(42),
                    scaledDp(40)
                );

            closeParams.leftMargin = dp(6);

            // Кнопка закрытия немного выше.
            closeParams.bottomMargin = dp(4);

            head.addView(closeButton, closeParams);

            closeButton.setOnClickListener(v -> toggle());

            root.addView(head);

            earnings = txt("", 18, accent(), true);

            LinearLayout.LayoutParams earningsParams =
                new LinearLayout.LayoutParams(-1, -2);

            earningsParams.topMargin = scaledDp(12);
            root.addView(earnings, earningsParams);

            count = txt("", 13, fg(), false);

            LinearLayout.LayoutParams countParams =
                new LinearLayout.LayoutParams(-1, -2);

            countParams.topMargin = scaledDp(6);
            root.addView(count, countParams);

            progressText = txt("", 12, accent(), true);

            LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(-1, -2);

            progressParams.topMargin = scaledDp(12);
            root.addView(progressText, progressParams);

            progressBar = new ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
            );

            progressBar.setMax(100);
            progressBar.setProgressTintList(
                ColorStateList.valueOf(accent())
            );

            LinearLayout.LayoutParams barParams =
                new LinearLayout.LayoutParams(-1, scaledDp(8));

            barParams.topMargin = scaledDp(6);
            root.addView(progressBar, barParams);

            round = btn("", accent());

            LinearLayout.LayoutParams roundParams =
                new LinearLayout.LayoutParams(-1, scaledDp(50));

            roundParams.topMargin = scaledDp(14);
            root.addView(round, roundParams);

            round.setOnClickListener(v -> addRound());

            undoButton = btn(
                "ОТМЕНИТЬ ПОСЛЕДНИЙ КРУГ",
                cardColor()
            );

            LinearLayout.LayoutParams undoParams =
                new LinearLayout.LayoutParams(-1, scaledDp(42));

            undoParams.topMargin = scaledDp(7);
            root.addView(undoButton, undoParams);

            undoButton.setOnClickListener(v -> undoRound());

            settingsButton = btn(
                "НАСТРОЙКИ ВЫПЛАТЫ И ЦЕЛИ",
                cardColor()
            );

            LinearLayout.LayoutParams settingsParams =
                new LinearLayout.LayoutParams(-1, scaledDp(44));

            settingsParams.topMargin = scaledDp(10);
            root.addView(settingsButton, settingsParams);

            settingsBox = new LinearLayout(this);
            settingsBox.setOrientation(1);
            settingsBox.setVisibility(View.GONE);
            settingsBox.setPadding(0, scaledDp(8), 0, 0);

            settingsBox.addView(
                txt("Выплата за круг", 12, fg(), true)
            );

            payInput = new EditText(this);
            payInput.setSingleLine(true);
            payInput.setInputType(2);
            payInput.setTextColor(fg());
            payInput.setHintTextColor(fg());
            payInput.setPadding(dp(10), dp(8), dp(10), dp(8));
            payInput.setBackground(shape(cardColor(), 10));
            payInput.setText(
                String.valueOf(prefs.getInt("pay", 15000))
            );

            settingsBox.addView(payInput);

            settingsBox.addView(
                txt("Цель накоплений", 12, fg(), true)
            );

            goalInput = new EditText(this);
            goalInput.setSingleLine(true);
            goalInput.setInputType(2);
            goalInput.setTextColor(fg());
            goalInput.setHintTextColor(fg());
            goalInput.setPadding(dp(10), dp(8), dp(10), dp(8));
            goalInput.setBackground(shape(cardColor(), 10));
            goalInput.setText(
                String.valueOf(prefs.getLong("goal", 5000000))
            );

            settingsBox.addView(goalInput);

            saveButton = btn("СОХРАНИТЬ", accent());
            settingsBox.addView(saveButton);

            cancelButton = btn("ОТМЕНА", cardColor());
            settingsBox.addView(cancelButton);

            root.addView(settingsBox);

            settingsButton.setOnClickListener(v -> {
                playFeedback(true);

                boolean show =
                    settingsBox.getVisibility() != View.VISIBLE;

                settingsBox.setVisibility(
                    show ? View.VISIBLE : View.GONE
                );

                editing = show;
                setFocusable(show);

                if (show) {
                    settingsButton.setText("СКРЫТЬ НАСТРОЙКИ");
                    payInput.requestFocus();

                    payInput.post(() -> {
                        InputMethodManager imm =
                            (InputMethodManager) getSystemService(
                                INPUT_METHOD_SERVICE
                            );

                        if (imm != null) {
                            imm.showSoftInput(
                                payInput,
                                InputMethodManager.SHOW_IMPLICIT
                            );
                        }
                    });

                } else {
                    settingsButton.setText(
                        "НАСТРОЙКИ ВЫПЛАТЫ И ЦЕЛИ"
                    );
                    hideKeyboard();
                }
            });

            saveButton.setOnClickListener(v -> {
                try {
                    long payValue = Long.parseLong(
                        payInput.getText().toString().trim()
                    );

                    long goalValue = Long.parseLong(
                        goalInput.getText().toString().trim()
                    );

                    if (payValue <= 0 ||
                        payValue > Integer.MAX_VALUE ||
                        goalValue <= 0) {
                        throw new NumberFormatException();
                    }

                    boolean saved = prefs.edit()
                        .putInt("pay", (int) payValue)
                        .putLong("goal", goalValue)
                        .commit();

                    if (!saved) {
                        playFeedback(false);
                        Toast.makeText(
                            this,
                            "Не удалось сохранить настройки",
                            Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    playFeedback(true);
                    hideKeyboard();

                    settingsBox.setVisibility(View.GONE);
                    settingsButton.setText(
                        "НАСТРОЙКИ ВЫПЛАТЫ И ЦЕЛИ"
                    );

                    editing = false;

                    setFocusable(false);
                    refreshStats();

                } catch (Exception e) {
                    playFeedback(false);
                    Toast.makeText(
                        this,
                        "Введи корректные числа",
                        Toast.LENGTH_SHORT
                    ).show();
                }
            });

            cancelButton.setOnClickListener(v -> {
                playFeedback(true);
                hideKeyboard();

                settingsBox.setVisibility(View.GONE);
                settingsButton.setText(
                    "НАСТРОЙКИ ВЫПЛАТЫ И ЦЕЛИ"
                );

                editing = false;

                setFocusable(false);
            });

            resetButton = btn(
                "СБРОСИТЬ СТАТИСТИКУ",
                0xFFB84040
            );

            LinearLayout.LayoutParams resetParams =
                new LinearLayout.LayoutParams(-1, scaledDp(42));

            resetParams.topMargin = scaledDp(8);
            root.addView(resetButton, resetParams);

            resetButton.setOnClickListener(v -> showResetDialog());

            panel = root;

            panelLp = lp(scaledDp(300), -2);
            panelLp.gravity = Gravity.TOP | Gravity.RIGHT;

            panelLp.x = prefs.getInt(KEY_PANEL_X, 0);
            panelLp.y = prefs.getInt(
                KEY_PANEL_Y,
                Math.max(dp(20), tabLp.y - dp(60))
            );

            limitPanelPosition();

            wm.addView(panel, panelLp);

            applyPanelOpacity(panel);
            refreshStats();

            panel.post(() -> {
                if (panel == null || panelLp == null) {
                    return;
                }

                limitPanelPosition();

                try {
                    wm.updateViewLayout(panel, panelLp);
                } catch (Exception ignored) {
                }
            });

        } catch (Exception e) {
            removePanel();
            expanded = false;

            Toast.makeText(
                this,
                "Не удалось открыть плавающую панель",
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    // ---------------- СБРОС ТЕКУЩЕЙ СМЕНЫ ----------------

    private void showResetDialog() {
        try {
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Сбросить статистику смены?")
                .setMessage(
                    "Будут сброшены только показатели текущей смены: " +
                    "заработок, количество кругов и время. " +
                    "Общий баланс, общее количество кругов, история рейсов " +
                    "и завершённые смены останутся без изменений."
                )
                .setNegativeButton("Отмена", null)
                .setPositiveButton(
                    "Сбросить",
                    (d, which) -> resetStats()
                )
                .create();

            Window window = dialog.getWindow();

            if (window != null) {
                window.setType(Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
            }

            dialog.show();

        } catch (Exception e) {
            Toast.makeText(
                this,
                "Не удалось открыть окно сброса",
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void resetStats() {
        try {
            if (!recoverPendingPanelOperations()) {
                playFeedback(false);
                Toast.makeText(
                    this,
                    "Не удалось восстановить данные. Сброс отменён.",
                    Toast.LENGTH_LONG
                ).show();
                return;
            }

            SharedPreferences.Editor editor = prefs.edit();

            editor.putLong("shiftEarned", 0L);
            editor.putLong("shiftTrips", 0L);
            editor.putLong("seconds", 0L);
            editor.putBoolean("running", false);
            editor.putLong("startAt", 0L);
            editor.putLong(KEY_SHIFT_STARTED, 0L);

            // Общий баланс, общие круги и история не изменяются.
            editor.putString(KEY_UNDO_STACK, "");
            editor.remove(KEY_LAST_PANEL_TRIP);
            editor.remove(KEY_LAST_PANEL_AMOUNT);

            if (!editor.commit()) {
                playFeedback(false);
                Toast.makeText(
                    this,
                    "Не удалось сохранить сброс. История не изменена.",
                    Toast.LENGTH_LONG
                ).show();

                refreshStats();
                return;
            }

            playFeedback(true);
            refreshStats();

            Toast.makeText(
                this,
                "Показатели текущей смены сброшены. История сохранена.",
                Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {
            playFeedback(false);
            Toast.makeText(
                this,
                "Ошибка сброса. История сохранена.",
                Toast.LENGTH_LONG
            ).show();
        }
    }

    // ---------------- ДОБАВЛЕНИЕ КРУГА ----------------

    private void addRound() {
        if (!recoverPendingPanelOperations()) {
            playFeedback(false);
            Toast.makeText(
                this,
                "Не удалось восстановить данные. Круг не добавлен.",
                Toast.LENGTH_LONG
            ).show();
            return;
        }

        long pay = prefs.getInt("pay", 15000);
        long balance = prefs.getLong("balance", 0L);
        long trips = prefs.getLong("trips", 0L);
        long earned = prefs.getLong("shiftEarned", 0L);
        long shiftTrips = prefs.getLong("shiftTrips", 0L);

        if (pay <= 0 ||
            !canAdd(balance, pay) ||
            !canAdd(earned, pay) ||
            trips < 0 ||
            trips == Long.MAX_VALUE ||
            shiftTrips < 0 ||
            shiftTrips == Long.MAX_VALUE) {

            playFeedback(false);
            Toast.makeText(
                this,
                "Невозможно добавить круг",
                Toast.LENGTH_SHORT
            ).show();

            return;
        }

        long now = System.currentTimeMillis();

        long shiftStartedAt = prefs.getLong(KEY_SHIFT_STARTED, 0L);

        if (shiftStartedAt <= 0L) {
            shiftStartedAt = now;
        }

        ArrayList<UndoRecord> stack = getUndoStack();

        DatabaseHelper.PanelOperation operation =
            repository.addPanelTripWithJournal(
                pay,
                now,
                balance + pay,
                trips + 1L,
                earned + pay,
                shiftTrips + 1L,
                shiftStartedAt,
                serializeUndoStack(stack)
            );

        if (operation == null) {
            playFeedback(false);
            Toast.makeText(
                this,
                "Не удалось сохранить круг. Статистика не изменена.",
                Toast.LENGTH_LONG
            ).show();

            return;
        }

        if (!applyPanelOperation(operation)) {
            playFeedback(false);
            Toast.makeText(
                this,
                "Круг сохранён в журнале, но счётчики ещё не обновлены. " +
                "Не нажимай добавление повторно; перезапусти приложение.",
                Toast.LENGTH_LONG
            ).show();

            refreshStats();
            return;
        }

        playFeedback(true);
        refreshStats();
    }

    // ---------------- ОТМЕНА КРУГА ----------------

    private void undoRound() {
        if (!recoverPendingPanelOperations()) {
            playFeedback(false);
            Toast.makeText(
                this,
                "Не удалось восстановить данные. Отмена не выполнена.",
                Toast.LENGTH_LONG
            ).show();
            return;
        }

        ArrayList<UndoRecord> stack = getUndoStack();

        if (stack.isEmpty()) {
            refreshStats();
            return;
        }

        UndoRecord record = stack.get(stack.size() - 1);

        long balance = prefs.getLong("balance", 0L);
        long trips = prefs.getLong("trips", 0L);
        long earned = prefs.getLong("shiftEarned", 0L);
        long shiftTrips = prefs.getLong("shiftTrips", 0L);

        if (record.id <= 0 ||
            record.amount <= 0 ||
            balance < record.amount ||
            trips <= 0 ||
            earned < record.amount ||
            shiftTrips <= 0) {

            playFeedback(false);
            Toast.makeText(
                this,
                "Не удалось безопасно отменить круг. Статистика не изменена.",
                Toast.LENGTH_LONG
            ).show();

            return;
        }

        stack.remove(stack.size() - 1);

        long newEarned = earned - record.amount;
        long newShiftTrips = shiftTrips - 1L;

        long newShiftStartedAt =
            (newEarned == 0L && newShiftTrips == 0L)
                ? 0L
                : prefs.getLong(KEY_SHIFT_STARTED, 0L);

        DatabaseHelper.PanelOperation operation =
            repository.undoPanelTripWithJournal(
                record.id,
                record.amount,
                balance - record.amount,
                trips - 1L,
                newEarned,
                newShiftTrips,
                newShiftStartedAt,
                serializeUndoStack(stack)
            );

        if (operation == null) {
            playFeedback(false);
            Toast.makeText(
                this,
                "Не удалось отменить круг. Статистика не изменена.",
                Toast.LENGTH_LONG
            ).show();

            return;
        }

        if (!applyPanelOperation(operation)) {
            playFeedback(false);
            Toast.makeText(
                this,
                "Отмена сохранена в журнале, но ещё не применена. " +
                "Не повторяй действие; перезапусти приложение.",
                Toast.LENGTH_LONG
            ).show();

            refreshStats();
            return;
        }

        playFeedback(true);
        refreshStats();
    }

    // ---------------- ОБНОВЛЕНИЕ СТАТИСТИКИ ----------------

    private void refreshStats() {
        if (prefs == null) {
            return;
        }

        long earned = prefs.getLong("shiftEarned", 0);
        long shiftTrips = prefs.getLong("shiftTrips", 0);
        long balance = prefs.getLong("balance", 0);
        long goal = prefs.getLong("goal", 5000000);
        int pay = prefs.getInt("pay", 15000);

        if (earnings != null) {
            earnings.setText(
                "Заработок за смену: " +
                money(earned) + " ₽"
            );
        }

        if (count != null) {
            count.setText(
                "Кругов за смену: " + shiftTrips +
                "\nОбщий баланс: " + money(balance) + " ₽"
            );
        }

        if (round != null) {
            round.setText(
                "СДЕЛАЛ КРУГ  +" + money(pay) + " ₽"
            );
        }

        if (progressText != null && progressBar != null) {
            double percent = goal > 0
                ? balance * 100.0 / goal
                : 0;

            progressBar.setProgress(
                (int) Math.min(100, Math.max(0, percent))
            );

            progressText.setText(
                "Прогресс: " +
                String.format(Locale.US, "%.1f", percent) +
                "%\nОсталось: " +
                money(Math.max(0, goal - balance)) + " ₽"
            );
        }

        if (undoButton != null) {
            boolean has = !getUndoStack().isEmpty();

            undoButton.setEnabled(has);
            undoButton.setAlpha(has ? 1f : 0.5f);
        }
    }

    // ---------------- СМЕНА ТЕМЫ ----------------

    private void refreshTheme() {
        int currentAccent = accent();
        int currentBackground = background();
        int currentCard = cardColor();
        int currentForeground = fg();

        if (tab != null) {
            tab.setBackground(shape(currentAccent, 18));
        }

        if (panel != null) {
            panel.setBackground(shape(currentBackground, 20));
            applyPanelOpacity(panel);
        }

        if (panelTitle != null) {
            panelTitle.setTextColor(currentForeground);
        }

        if (earnings != null) {
            earnings.setTextColor(currentAccent);
        }

        if (count != null) {
            count.setTextColor(currentForeground);
        }

        if (progressText != null) {
            progressText.setTextColor(currentAccent);
        }

        if (progressBar != null) {
            progressBar.setProgressTintList(
                ColorStateList.valueOf(currentAccent)
            );
        }

        if (round != null) {
            round.setBackground(shape(currentAccent, 12));
            round.setTextColor(currentForeground);
        }

        if (undoButton != null) {
            undoButton.setBackground(shape(currentCard, 12));
            undoButton.setTextColor(currentForeground);
        }

        if (settingsButton != null) {
            settingsButton.setBackground(shape(currentCard, 12));
            settingsButton.setTextColor(currentForeground);
        }

        if (saveButton != null) {
            saveButton.setBackground(shape(currentAccent, 12));
            saveButton.setTextColor(currentForeground);
        }

        if (cancelButton != null) {
            cancelButton.setBackground(shape(currentCard, 12));
            cancelButton.setTextColor(currentForeground);
        }

        if (resetButton != null) {
            resetButton.setBackground(shape(0xFFB84040, 12));
            resetButton.setTextColor(Color.WHITE);
        }

        if (closeButton != null) {
            closeButton.setBackground(shape(currentCard, 12));
            closeButton.setTextColor(currentForeground);
        }

        if (dragButton != null) {
            dragButton.setBackground(shape(currentCard, 12));
            dragButton.setTextColor(currentForeground);
        }

        if (payInput != null) {
            payInput.setTextColor(currentForeground);
            payInput.setHintTextColor(currentForeground);
            payInput.setBackground(shape(currentCard, 10));
        }

        if (goalInput != null) {
            goalInput.setTextColor(currentForeground);
            goalInput.setHintTextColor(currentForeground);
            goalInput.setBackground(shape(currentCard, 10));
        }

        if (settingsBox != null) {
            for (int i = 0; i < settingsBox.getChildCount(); i++) {
                View child = settingsBox.getChildAt(i);

                if (child instanceof TextView &&
                    !(child instanceof EditText)) {
                    ((TextView) child).setTextColor(currentForeground);
                }
            }
        }
    }

    // ---------------- ФОКУС И КЛАВИАТУРА ----------------

    private void setFocusable(boolean focusable) {
        if (panel == null || panelLp == null) {
            return;
        }

        panelLp.flags =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        if (!focusable) {
            panelLp.flags |=
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }

        try {
            wm.updateViewLayout(panel, panelLp);
        } catch (Exception ignored) {
        }
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm =
                (InputMethodManager) getSystemService(
                    INPUT_METHOD_SERVICE
                );

            if (imm != null && payInput != null) {
                imm.hideSoftInputFromWindow(
                    payInput.getWindowToken(),
                    0
                );
            }

        } catch (Exception ignored) {
        }
    }

    // ---------------- ЖИЗНЕННЫЙ ЦИКЛ ----------------

    @Override
    public int onStartCommand(
        Intent intent,
        int flags,
        int startId
    ) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        hideKeyboard();

        if (panel != null) {
            savePanelPosition();
        }

        if (tabLp != null) {
            saveTabPosition();
        }

        if (prefs != null) {
            if (listener != null) {
                prefs.unregisterOnSharedPreferenceChangeListener(listener);
            }

            prefs.edit()
                .putBoolean("panel_enabled", false)
                .apply();
        }

        try {
            if (tab != null) {
                wm.removeView(tab);
            }
        } catch (Exception ignored) {
        }

        try {
            if (panel != null) {
                wm.removeView(panel);
            }
        } catch (Exception ignored) {
        }

        tab = null;
        panel = null;

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
