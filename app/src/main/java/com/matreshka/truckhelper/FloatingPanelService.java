// Файл: FloatingPanelService.java
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

    private static final String NOTIF_CHANNEL = "trucklog_panel";
    private static final int NOTIF_ROUND = 2001;
    private static final int NOTIF_UNDO = 2002;

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
    private boolean busy = false;

    private TextView earnings;
    private TextView count;
    private TextView progressText;
    private TextView panelTitle;
    private TextView inlineView;

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
        0xFF211735, 0xFFE2E8F0, 0xFF1B1B1B, 0xFF2A151C,
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

    private int accent() { return accents[theme()]; }
    private int background() { return backgrounds[theme()]; }
    private int cardColor() { return cards[theme()]; }
    private int fg() { return foregrounds[theme()]; }

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

    private TextView txt(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
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
        if (prefs != null &&
            prefs.getBoolean("currency_compact", false)) {
            return compactMoney(value);
        }
        return NumberFormat.getIntegerInstance(
            new Locale("ru", "RU")
        ).format(value);
    }

    private String compactMoney(long value) {
        if (value >= 1000000) {
            return String.format(
                Locale.US, "%.1fм", value / 1000000.0
            );
        }
        if (value >= 1000) {
            return String.format(
                Locale.US, "%.0fк", value / 1000.0
            );
        }
        return String.valueOf(value);
    }

    private boolean canAdd(long first, long second) {
        return first >= 0 &&
            second >= 0 &&
            first <= Long.MAX_VALUE - second;
    }

    // ---------------- ЗВУКИ И ВИБРАЦИЯ ----------------

    private void feedbackAdd() {
        playTone(ToneGenerator.TONE_PROP_BEEP2, 90);
        vibrate(25, 0);
    }

    private void feedbackUndo() {
        playTone(ToneGenerator.TONE_PROP_NACK, 140);
        vibrate(50, 60);
    }

    private void feedbackError() {
        playTone(ToneGenerator.TONE_PROP_NACK, 220);
        long[] pattern = { 0, 60, 80, 60 };
        vibratePattern(pattern);
    }

    private void playTone(int toneType, int durationMs) {
        if (prefs == null) return;
        if (prefs.getBoolean("silent_mode", false)) return;
        if (!prefs.getBoolean("sound_enabled", true)) return;

        int volume = Math.max(
            0, Math.min(100, prefs.getInt("sound_volume", 70))
        );
        if (volume <= 0) return;

        ToneGenerator tone = null;
        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, volume);
            tone.startTone(toneType, durationMs);
        } catch (Exception ignored) {
        } finally {
            if (tone != null) {
                final ToneGenerator finalTone = tone;
                mainHandler.postDelayed(() -> {
                    try { finalTone.release(); } catch (Exception ignored) {}
                }, durationMs + 50);
            }
        }
    }

    private void vibrate(long ms, long sleepMs) {
        if (prefs == null) return;
        if (prefs.getBoolean("silent_mode", false)) return;
        if (!prefs.getBoolean("vibration_enabled", true)) return;

        try {
            Vibrator v = (Vibrator)getSystemService(VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;

            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(
                    ms, VibrationEffect.DEFAULT_AMPLITUDE
                ));
            } else {
                v.vibrate(ms);
            }
        } catch (Exception ignored) {
        }
    }

    private void vibratePattern(long[] pattern) {
        if (prefs == null) return;
        if (prefs.getBoolean("silent_mode", false)) return;
        if (!prefs.getBoolean("vibration_enabled", true)) return;

        try {
            Vibrator v = (Vibrator)getSystemService(VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;

            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                v.vibrate(pattern, -1);
            }
        } catch (Exception ignored) {
        }
    }

    // ---------------- УВЕДОМЛЕНИЯ ----------------

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                NOTIF_CHANNEL,
                "TruckLog панель",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void notifyPanel(int id, String title, String text) {
        if (prefs == null) return;
        if (!prefs.getBoolean("notifications_enabled", true)) return;

        try {
            createNotificationChannel();

            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                b = new Notification.Builder(this, NOTIF_CHANNEL);
            } else {
                b = new Notification.Builder(this);
            }

            b.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true);

            NotificationManager nm = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(id, b.build());
        } catch (Exception ignored) {
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
                    if (values.length != 2) continue;

                    long id = Long.parseLong(values[0]);
                    long amount = Long.parseLong(values[1]);

                    if (id > 0 && amount > 0) {
                        result.add(new UndoRecord(id, amount));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return result;
    }

    private String serializeUndoStack(ArrayList<UndoRecord> stack) {
        StringBuilder builder = new StringBuilder();

        for (UndoRecord record : stack) {
            if (builder.length() > 0) builder.append(";");
            builder.append(record.id).append(":").append(record.amount);
        }

        return builder.toString();
    }

    // ---------------- ДИАЛОГИ ----------------

    private void styleDialog(AlertDialog dialog) {
        if (dialog == null) return;

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(shape(cardColor(), 18));
        }

        int titleId = getResources().getIdentifier(
            "alertTitle", "id", "android"
        );
        if (titleId > 0) {
            TextView titleView = (TextView)dialog.findViewById(titleId);
            if (titleView != null) titleView.setTextColor(fg());
        }

        int messageId = getResources().getIdentifier(
            "message", "id", "android"
        );
        if (messageId > 0) {
            TextView messageView = (TextView)dialog.findViewById(messageId);
            if (messageView != null) messageView.setTextColor(fg());
        }

        int button1 = getResources().getIdentifier(
            "button1", "id", "android"
        );
        int button2 = getResources().getIdentifier(
            "button2", "id", "android"
        );
        int button3 = getResources().getIdentifier(
            "button3", "id", "android"
        );

        int[] ids = { button1, button2, button3 };
        for (int id : ids) {
            if (id <= 0) continue;
            Button button = (Button)dialog.findViewById(id);
            if (button != null) button.setTextColor(accent());
        }
    }

    private void showServiceDialog(String title, String message) {
        try {
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("ОК", null)
                .create();

            Window window = dialog.getWindow();
            if (window != null) {
                if (Build.VERSION.SDK_INT >= 26) {
                    window.setType(
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    );
                } else {
                    window.setType(
                        WindowManager.LayoutParams.TYPE_PHONE
                    );
                }
            }

            dialog.show();
            styleDialog(dialog);

        } catch (Exception ignored) {
        }
    }

    private void showServiceInline(String message) {
        if (panel == null || inlineView == null) {
            showServiceDialog("TruckLog", message);
            return;
        }

        inlineView.setText(message);
        inlineView.setVisibility(View.VISIBLE);

        mainHandler.postDelayed(() -> {
            if (inlineView != null) {
                inlineView.setVisibility(View.GONE);
            }
        }, 3000);
    }

    // ---------------- ЗАПУСК СЕРВИСА ----------------

    @Override
    public void onCreate() {
        super.onCreate();

        prefs = getSharedPreferences("truck_stats", MODE_PRIVATE);
        repository = DataRepository.getInstance(this);

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        mainHandler = new Handler(Looper.getMainLooper());

        if (Build.VERSION.SDK_INT >= 23 &&
            !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        if (!repository.initialize()) {
            showServiceDialog(
                "История",
                "Ошибка переноса истории. Старые данные сохранены."
            );
        }

        listener = (preferences, key) -> {
            if (key == null) return;

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
        if (tabLp == null || prefs == null) return;

        prefs.edit()
            .putInt(KEY_TAB_X, tabLp.x)
            .putInt(KEY_TAB_Y, tabLp.y)
            .apply();
    }

    private void savePanelPosition() {
        if (panelLp == null || prefs == null) return;

        prefs.edit()
            .putInt(KEY_PANEL_X, panelLp.x)
            .putInt(KEY_PANEL_Y, panelLp.y)
            .apply();
    }

    private void limitTabPosition() {
        if (tabLp == null) return;

        int maxX = Math.max(0, screenWidth() - tabLp.width);
        int maxY = Math.max(0, screenHeight() - tabLp.height);

        tabLp.x = clamp(tabLp.x, 0, maxX);
        tabLp.y = clamp(tabLp.y, 0, maxY);
    }

    private void limitPanelPosition() {
        if (panelLp == null) return;

        int width = panelLp.width;
        if (width <= 0) width = scaledDp(300);

        int height = 0;
        if (panel != null) height = panel.getHeight();
        if (height <= 0) height = dp(350);

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
        if (view == null) return;

        view.setOnTouchListener((v, event) -> {
            if (panelLp == null || panel == null) return false;

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
                    if (panelMoved) savePanelPosition();
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    return true;
            }

            return true;
        });
    }

    private void refreshPanelSizeAndPosition() {
        if (panel == null || panelLp == null) return;

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
        inlineView = null;

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
        if (view == null || view.getBackground() == null) return;

        int alpha = (int) (255f * panelOpacity() / 100f);
        view.getBackground().setAlpha(alpha);
    }

    // ---------------- СОЗДАНИЕ ПАНЕЛИ ----------------

    private void makePanel() {
        try {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(1);
            root.setPadding(
                scaledDp(13), scaledDp(13),
                scaledDp(13), scaledDp(13)
            );
            root.setBackground(shape(background(), 20));

            LinearLayout head = new LinearLayout(this);
            head.setGravity(Gravity.CENTER_VERTICAL);

            panelTitle = txt("TRUCK HELPER", 15, fg(), true);

            head.addView(
                panelTitle,
                new LinearLayout.LayoutParams(0, -2, 1)
            );

            dragButton = btn("☰", cardColor());

            LinearLayout.LayoutParams dragParams =
                new LinearLayout.LayoutParams(
                    scaledDp(42), scaledDp(40)
                );
            dragParams.topMargin = dp(4);
            head.addView(dragButton, dragParams);
            setPanelDrag(dragButton);

            closeButton = btn("×", cardColor());

            LinearLayout.LayoutParams closeParams =
                new LinearLayout.LayoutParams(
                    scaledDp(42), scaledDp(40)
                );
            closeParams.leftMargin = dp(6);
            closeParams.bottomMargin = dp(4);
            head.addView(closeButton, closeParams);
            closeButton.setOnClickListener(v -> toggle());

            root.addView(head);

            inlineView = txt("", 12, accent(), true);
            inlineView.setVisibility(View.GONE);
            inlineView.setPadding(0, scaledDp(6), 0, 0);
            root.addView(inlineView);

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
                this, null,
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

            undoButton = btn("ОТМЕНИТЬ ПОСЛЕДНИЙ КРУГ", cardColor());

            LinearLayout.LayoutParams undoParams =
                new LinearLayout.LayoutParams(-1, scaledDp(42));
            undoParams.topMargin = scaledDp(7);
            root.addView(undoButton, undoParams);
            undoButton.setOnClickListener(v -> undoRound());

            settingsButton = btn("НАСТРОЙКИ ВЫПЛАТЫ И ЦЕЛИ", cardColor());

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
                boolean show =
                    settingsBox.getVisibility() != View.VISIBLE;

                settingsBox.setVisibility(
                    show ? View.VISIBLE : View.GONE
                );

                editing = show;
                setFocusable(show);

                if (show) {
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

                    prefs.edit()
                        .putInt("pay", (int) payValue)
                        .putLong("goal", goalValue)
                        .apply();

                    hideKeyboard();
                    settingsBox.setVisibility(View.GONE);
                    editing = false;
                    setFocusable(false);
                    refreshStats();

                } catch (Exception e) {
                    feedbackError();
                    showServiceInline("Введи корректные числа");
                }
            });

            cancelButton.setOnClickListener(v -> {
                hideKeyboard();
                settingsBox.setVisibility(View.GONE);
                editing = false;
                setFocusable(false);
            });

            resetButton = btn("СБРОСИТЬ БАЛАНС И КРУГИ", 0xFFB84040);

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
                if (panel == null || panelLp == null) return;
                limitPanelPosition();
                try {
                    wm.updateViewLayout(panel, panelLp);
                } catch (Exception ignored) {
                }
            });

        } catch (Exception e) {
            removePanel();
            expanded = false;
            showServiceDialog(
                "Панель",
                "Не удалось открыть плавающую панель."
            );
        }
    }

    // ---------------- СБРОС ТЕКУЩЕГО БАЛАНСА И КРУГОВ ----------------

    private void showResetDialog() {
        try {
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Сбросить текущий баланс и круги?")
                .setMessage(
                    "Будут обнулены только текущий баланс, количество " +
                    "кругов, заработок и таймер текущей смены. " +
                    "История рейсов, графики и общая статистика " +
                    "останутся без изменений."
                )
                .setNegativeButton("Отмена", null)
                .setPositiveButton(
                    "Сбросить",
                    (d, which) -> resetStats()
                )
                .create();

            Window window = dialog.getWindow();
            if (window != null) {
                if (Build.VERSION.SDK_INT >= 26) {
                    window.setType(
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    );
                } else {
                    window.setType(
                        WindowManager.LayoutParams.TYPE_PHONE
                    );
                }
            }

            dialog.show();
            styleDialog(dialog);

        } catch (Exception e) {
            showServiceInline("Не удалось открыть окно сброса");
        }
    }

    /**
     * Простой сброс текущих показателей (П.2):
     * - balance = 0
     * - trips = 0
     * - shiftEarned = 0
     * - shiftTrips = 0
     * - seconds = 0
     * - shiftStartedAt = 0
     * - startAt = 0
     * - running = false
     * - panel_undo_stack очищается
     *
     * История рейсов в SQLite НЕ трогается —
     * графики и общая статистика остаются.
     */
    public static boolean resetCurrentShift(Context context) {
        if (context == null) return false;

        Context app = context.getApplicationContext();
        SharedPreferences preferences = app.getSharedPreferences(
            "truck_stats", Context.MODE_PRIVATE
        );

        try {
            return preferences.edit()
                .putLong("balance", 0L)
                .putLong("trips", 0L)
                .putLong("shiftEarned", 0L)
                .putLong("shiftTrips", 0L)
                .putLong("seconds", 0L)
                .putLong(KEY_SHIFT_STARTED, 0L)
                .putLong("startAt", 0L)
                .putBoolean("running", false)
                .remove(KEY_UNDO_STACK)
                .remove(KEY_LAST_PANEL_TRIP)
                .remove(KEY_LAST_PANEL_AMOUNT)
                .commit();

        } catch (Exception e) {
            return false;
        }
    }

    private void resetStats() {
        busy = true;
        if (resetButton != null) resetButton.setEnabled(false);

        new Thread(() -> {
            boolean saved = false;
            try {
                saved = resetCurrentShift(this);
            } catch (Exception ignored) {
            }

            final boolean ok = saved;

            mainHandler.post(() -> {
                busy = false;
                if (resetButton != null) resetButton.setEnabled(true);

                if (!ok) {
                    feedbackError();
                    showServiceDialog(
                        "Сброс не выполнен",
                        "Не удалось сохранить изменения. Попробуй ещё раз."
                    );
                    refreshStats();
                    return;
                }

                feedbackUndo();
                refreshStats();
                showServiceInline(
                    "Баланс и круги сброшены. История сохранена."
                );
            });
        }).start();
    }

    // ---------------- ДОБАВЛЕНИЕ КРУГА ----------------

    private void addRound() {
        if (busy) return;
        busy = true;

        if (round != null) round.setEnabled(false);
        if (undoButton != null) undoButton.setEnabled(false);

        new Thread(() -> {
            boolean ok = false;
            String error = null;
            long pay = 0;

            try {
                pay = prefs.getInt("pay", 15000);
                long balance = prefs.getLong("balance", 0);
                long trips = prefs.getLong("trips", 0);
                long earned = prefs.getLong("shiftEarned", 0);
                long shiftTrips = prefs.getLong("shiftTrips", 0);

                if (pay <= 0 ||
                    !canAdd(balance, pay) ||
                    !canAdd(earned, pay) ||
                    trips < 0 ||
                    trips == Long.MAX_VALUE ||
                    shiftTrips < 0 ||
                    shiftTrips == Long.MAX_VALUE) {

                    error = "Невозможно добавить круг";
                } else {
                    long now = System.currentTimeMillis();

                    long recordId = repository.addTrip(
                        pay, now, 0, "", "", "floating_panel"
                    );

                    if (recordId <= 0) {
                        error = "Не удалось сохранить круг";
                    } else {
                        long shiftStartedAt =
                            prefs.getLong(KEY_SHIFT_STARTED, 0);
                        if (shiftStartedAt <= 0) shiftStartedAt = now;

                        ArrayList<UndoRecord> stack = getUndoStack();
                        stack.add(new UndoRecord(recordId, pay));

                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString(
                            KEY_UNDO_STACK, serializeUndoStack(stack)
                        );
                        editor.putLong("balance", balance + pay);
                        editor.putLong("trips", trips + 1);
                        editor.putLong("shiftEarned", earned + pay);
                        editor.putLong("shiftTrips", shiftTrips + 1);
                        editor.putLong(KEY_SHIFT_STARTED, shiftStartedAt);

                        if (editor.commit()) {
                            ok = true;
                        } else {
                            repository.deleteTrip(
                                recordId, "floating_panel"
                            );
                            error = "Не удалось сохранить круг. Попробуй ещё раз.";
                        }
                    }
                }
            } catch (Exception e) {
                error = "Ошибка при сохранении круга";
            }

            final boolean success = ok;
            final String errMsg = error;
            final long finalPay = pay;

            mainHandler.post(() -> {
                busy = false;
                if (round != null) round.setEnabled(true);

                if (success) {
                    feedbackAdd();
                    refreshStats();

                    if (prefs.getBoolean(
                            "trip_finish_notifications", true)) {
                        notifyPanel(
                            NOTIF_ROUND,
                            "Круг добавлен",
                            "+" + money(finalPay) + " ₽"
                        );
                    }
                } else {
                    feedbackError();
                    if (errMsg != null) showServiceInline(errMsg);
                }
            });
        }).start();
    }

    // ---------------- ОТМЕНА КРУГА ----------------

    private void undoRound() {
        if (busy) return;

        ArrayList<UndoRecord> stack = getUndoStack();
        if (stack.isEmpty()) {
            refreshStats();
            return;
        }

        busy = true;
        if (undoButton != null) undoButton.setEnabled(false);
        if (round != null) round.setEnabled(false);

        new Thread(() -> {
            boolean ok = false;
            String error = null;
            long amount = 0;

            try {
                ArrayList<UndoRecord> current = getUndoStack();
                if (current.isEmpty()) {
                    error = null;
                } else {
                    UndoRecord record = current.get(current.size() - 1);
                    amount = record.amount;

                    long balance = prefs.getLong("balance", 0);
                    long trips = prefs.getLong("trips", 0);
                    long earned = prefs.getLong("shiftEarned", 0);
                    long shiftTrips = prefs.getLong("shiftTrips", 0);

                    if (record.amount <= 0 ||
                        balance < record.amount ||
                        trips <= 0 ||
                        earned < record.amount ||
                        shiftTrips <= 0) {

                        error = "Не удалось безопасно отменить круг";
                    } else {
                        boolean deleted = repository.deleteTrip(
                            record.id, "floating_panel"
                        );

                        if (!deleted) {
                            error = "Запись круга не найдена";
                        } else {
                            current.remove(current.size() - 1);

                            long newBalance = balance - record.amount;
                            long newTrips = trips - 1;
                            long newEarned = earned - record.amount;
                            long newShiftTrips = shiftTrips - 1;

                            SharedPreferences.Editor editor =
                                prefs.edit();
                            editor.putLong("balance", newBalance);
                            editor.putLong("trips", newTrips);
                            editor.putLong("shiftEarned", newEarned);
                            editor.putLong("shiftTrips", newShiftTrips);
                            editor.putString(
                                KEY_UNDO_STACK,
                                serializeUndoStack(current)
                            );

                            if (newEarned == 0 && newShiftTrips == 0) {
                                editor.putLong(KEY_SHIFT_STARTED, 0);
                            }

                            if (editor.commit()) {
                                ok = true;
                            } else {
                                error = "Запись удалена, но счётчики " +
                                        "не удалось сохранить";
                            }
                        }
                    }
                }
            } catch (Exception e) {
                error = "Ошибка при отмене круга";
            }

            final boolean success = ok;
            final String errMsg = error;
            final long finalAmount = amount;

            mainHandler.post(() -> {
                busy = false;
                if (round != null) round.setEnabled(true);

                if (success) {
                    feedbackUndo();
                    refreshStats();

                    if (prefs.getBoolean(
                            "trip_finish_notifications", true)) {
                        notifyPanel(
                            NOTIF_UNDO,
                            "Круг отменён",
                            "-" + money(finalAmount) + " ₽"
                        );
                    }
                } else {
                    if (errMsg != null) {
                        feedbackError();
                        showServiceInline(errMsg);
                    }
                    refreshStats();
                }
            });
        }).start();
    }

    // ---------------- ОБНОВЛЕНИЕ СТАТИСТИКИ ----------------

    private void refreshStats() {
        if (prefs == null) return;

        long earned = prefs.getLong("shiftEarned", 0);
        long shiftTrips = prefs.getLong("shiftTrips", 0);
        long balance = prefs.getLong("balance", 0);
        long goal = prefs.getLong("goal", 5000000);
        int pay = prefs.getInt("pay", 15000);

        if (earnings != null) {
            earnings.setText(
                "Заработок за смену: " + money(earned) + " ₽"
            );
        }

        if (count != null) {
            count.setText(
                "Кругов за смену: " + shiftTrips +
                "\nОбщий баланс: " + money(balance) + " ₽"
            );
        }

        if (round != null) {
            round.setText("СДЕЛАЛ КРУГ  +" + money(pay) + " ₽");
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
            undoButton.setEnabled(has && !busy);
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

        if (panelTitle != null) panelTitle.setTextColor(currentForeground);
        if (earnings != null) earnings.setTextColor(currentAccent);
        if (count != null) count.setTextColor(currentForeground);
        if (progressText != null) progressText.setTextColor(currentAccent);

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

        if (payInput != null) payInput.setTextColor(currentForeground);
        if (goalInput != null) goalInput.setTextColor(currentForeground);
        if (inlineView != null) inlineView.setTextColor(currentAccent);

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
        if (panel == null || panelLp == null) return;

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
                    payInput.getWindowToken(), 0
                );
            }
        } catch (Exception ignored) {
        }
    }

    // ---------------- ЖИЗНЕННЫЙ ЦИКЛ ----------------

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        hideKeyboard();

        if (panel != null) savePanelPosition();
        if (tabLp != null) saveTabPosition();

        if (prefs != null) {
            if (listener != null) {
                prefs.unregisterOnSharedPreferenceChangeListener(listener);
            }

            prefs.edit()
                .putBoolean("panel_enabled", false)
                .apply();
        }

        try {
            if (tab != null) wm.removeView(tab);
        } catch (Exception ignored) {
        }

        try {
            if (panel != null) wm.removeView(panel);
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
