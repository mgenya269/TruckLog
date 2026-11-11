// Файл: MainActivity.java
package com.matreshka.truckhelper;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private DataRepository repository;

    private LinearLayout root, content;
    private FrameLayout frame;
    private LinearLayout drawer;
    private String page = "home";

    private TextView timerView;
    private TextView inlineMessage;

    private long balance, trips, seconds, startAt;
    private boolean running;

    private Handler handler = new Handler();

    private static final String CHANNEL_ID = "trucklog_main";
    private static final int NOTIF_TRIP = 1001;
    private static final int NOTIF_SHIFT = 1002;

    private final String[] blockKeys = {
        "balance", "rounds", "payout", "timer", "overlay"
    };

    private final String[] blockNames = {
        "Баланс и цель", "Количество кругов",
        "Выплата за круг", "Таймер рейса", "Плавающая панель"
    };

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

    private final int[] mutedColors = {
        0xFFC6A5E8, 0xFF64748B, 0xFFAAAAAA, 0xFFE6AAB7,
        0xFFA5D6B4, 0xFFE7D69A, 0xFFA9C5F5, 0xFF9BDCE8,
        0xFFF0B1D5, 0xFFE9B99A
    };

    private int theme() {
        return Math.max(0, Math.min(9, prefs.getInt("theme", 0)));
    }

    private int accent() { return accents[theme()]; }
    private int background() { return backgrounds[theme()]; }
    private int cardColor() { return cards[theme()]; }
    private int fg() { return foregrounds[theme()]; }
    private int muted() { return mutedColors[theme()]; }

    private int pay() {
        return Math.max(1, prefs.getInt("pay", 15000));
    }

    private long goal() {
        return Math.max(1L, prefs.getLong("goal", 5000000L));
    }

    private long read(String key) {
        return prefs.getLong(key, 0);
    }

    private String money(long n) {
        if (prefs.getBoolean("currency_compact", false)) {
            return compactMoney(n);
        }
        return NumberFormat.getIntegerInstance(
            new Locale("ru", "RU")
        ).format(n);
    }

    private int dp(float n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private Button button(String s, int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(fg());
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTypeface(null, Typeface.BOLD);
        b.setBackground(shape(color, 14));
        return b;
    }

    private EditText edit(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setInputType(inputType);
        e.setHint(hint);
        e.setTextColor(fg());
        e.setHintTextColor(muted());
        return e;
    }

    private void showThemedDialog(AlertDialog dialog) {
        if (dialog == null) return;

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(shape(cardColor(), 18));
        }

        int titleId = getResources().getIdentifier(
            "alertTitle", "id", "android"
        );
        if (titleId > 0) {
            TextView title = (TextView)dialog.findViewById(titleId);
            if (title != null) title.setTextColor(fg());
        }

        int messageId = getResources().getIdentifier(
            "message", "id", "android"
        );
        if (messageId > 0) {
            TextView message = (TextView)dialog.findViewById(messageId);
            if (message != null) message.setTextColor(fg());
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

        int[] buttonIds = { button1, button2, button3 };
        for (int id : buttonIds) {
            if (id <= 0) continue;
            Button dialogButton = (Button)dialog.findViewById(id);
            if (dialogButton != null) {
                dialogButton.setTextColor(accent());
            }
        }
    }

    private void infoDialog(String title, String message) {
        AlertDialog d = new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("ОК", null)
            .create();
        showThemedDialog(d);
    }

    private void showInline(String message) {
        if (inlineMessage == null) {
            infoDialog("TruckLog", message);
            return;
        }

        inlineMessage.setText(message);
        inlineMessage.setVisibility(View.VISIBLE);

        handler.postDelayed(() -> {
            if (inlineMessage != null) {
                inlineMessage.setVisibility(View.GONE);
            }
        }, 3500);
    }

    private long startOfDay(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    // ---------------- УВЕДОМЛЕНИЯ ----------------

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "TruckLog",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager nm = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void notifyUser(int id, String title, String text) {
        if (!prefs.getBoolean("notifications_enabled", true)) return;

        try {
            createNotificationChannel();

            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(
                this, id, intent,
                Build.VERSION.SDK_INT >= 23
                    ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                    : PendingIntent.FLAG_UPDATE_CURRENT
            );

            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= 26) {
                builder = new Notification.Builder(this, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(this);
            }

            builder.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setContentIntent(pi);

            NotificationManager nm = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(id, builder.build());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        prefs = getSharedPreferences("truck_stats", MODE_PRIVATE);
        repository = DataRepository.getInstance(this);

        if (!repository.initialize()) {
            infoDialog(
                "История",
                "Не удалось перенести историю. Старые данные сохранены."
            );
        }

        listener = (p, key) -> {
            if (key == null) return;
            runOnUiThread(() -> {
                syncData();
                renderPage();
            });
        };

        prefs.registerOnSharedPreferenceChangeListener(listener);

        syncData();
        buildBase();
        renderPage();
    }

    private void syncData() {
        balance = Math.max(0L, read("balance"));
        trips = Math.max(0L, read("trips"));
        seconds = Math.max(0L, read("seconds"));
        startAt = read("startAt");
        running = prefs.getBoolean("running", false);
    }

    private void buildBase() {
        frame = new FrameLayout(this);

        root = new LinearLayout(this);
        root.setOrientation(1);
        root.setBackgroundColor(background());

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(12));

        Button menu = button("❯❯❯", accent());
        LinearLayout.LayoutParams menuParams =
            new LinearLayout.LayoutParams(dp(62), dp(46));
        menuParams.topMargin = dp(8);
        header.addView(menu, menuParams);
        menu.setOnClickListener(v -> toggleDrawer());

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(1);
        labels.setPadding(dp(12), 0, 0, 0);
        labels.addView(text("MATRESHKA", 11, muted(), true));
        labels.addView(text("TRUCK HELPER", 20, fg(), true));

        header.addView(labels);
        root.addView(header);

        inlineMessage = text("", 13, accent(), true);
        inlineMessage.setVisibility(View.GONE);
        inlineMessage.setPadding(dp(18), dp(8), dp(18), dp(8));
        root.addView(inlineMessage);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(1);
        content.setPadding(dp(18), dp(8), dp(18), dp(24));
        scroll.addView(content);

        root.addView(
            scroll,
            new LinearLayout.LayoutParams(-1, 0, 1)
        );

        frame.addView(root);
        setContentView(frame);
    }

    private void rebuildBase() {
        drawer = null;
        buildBase();
        renderPage();
    }

    private void toggleDrawer() {
        if (drawer != null) {
            frame.removeView(drawer);
            drawer = null;
            return;
        }

        drawer = new LinearLayout(this);
        drawer.setOrientation(1);
        drawer.setPadding(dp(16), dp(22), dp(12), dp(20));
        drawer.setBackground(shape(cardColor(), 0));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            dp(270), -1, Gravity.LEFT
        );
        frame.addView(drawer, lp);

        drawer.addView(text("TRUCK HELPER", 20, fg(), true));
        drawer.addView(text("РАЗДЕЛЫ ПРИЛОЖЕНИЯ", 10, muted(), true));

        addDrawerItem("⌂", "Главная", "home");
        addDrawerItem("▦", "Статистика", "stats");
        addDrawerItem("▤", "Графики заработка", "charts");
        addDrawerItem("◷", "История кругов", "history");
        addDrawerItem("◎", "Калькулятор целей", "calculator");
        addDrawerItem("⚙", "Настройки", "settings");

        View space = new View(this);
        drawer.addView(
            space,
            new LinearLayout.LayoutParams(1, 0, 1)
        );

        Button close = button("ЗАКРЫТЬ МЕНЮ", accent());
        LinearLayout.LayoutParams closeParams =
            new LinearLayout.LayoutParams(-1, dp(46));
        closeParams.bottomMargin = dp(14);
        drawer.addView(close, closeParams);
        close.setOnClickListener(v -> toggleDrawer());
    }

    private void addDrawerItem(String icon, String title, String target) {
        Button b = button(icon + "     " + title, cardColor());
        b.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1, dp(50));
        p.topMargin = dp(8);
        drawer.addView(b, p);

        b.setOnClickListener(v -> {
            page = target;
            toggleDrawer();
            renderPage();
        });
    }

    private void renderPage() {
        if (content == null) return;

        content.removeAllViews();
        syncData();

        if ("home".equals(page)) showHome();
        else if ("stats".equals(page)) showStats();
        else if ("charts".equals(page)) showCharts();
        else if ("history".equals(page)) showHistory();
        else if ("calculator".equals(page)) showCalculator();
        else if ("theme".equals(page)) showThemes();
        else if ("settings".equals(page)) showSettings();
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(1);
        c.setPadding(dp(16), dp(15), dp(16), dp(15));
        c.setBackground(shape(cardColor(), 18));

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(12);
        content.addView(c, p);
        return c;
    }

    private void heading(String title, String subtitle) {
        content.addView(text(title, 25, fg(), true));
        TextView sub = text(subtitle, 13, muted(), false);

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(4);
        content.addView(sub, p);
    }

    private boolean blockVisible(String key) {
        return prefs.getBoolean("home_block_" + key, true);
    }

    private void showHome() {
        heading("Главная", "Твой учёт рейсов и заработка");

        if (blockVisible("balance")) {
            LinearLayout c = card();
            c.addView(text("ТЕКУЩИЙ БАЛАНС", 12, muted(), true));
            c.addView(text(money(balance) + " ₽", 32, fg(), true));

            long remain = Math.max(0L, goal() - balance);
            c.addView(text(
                "До цели осталось " + money(remain) + " ₽",
                13, accent(), true
            ));

            ProgressBar bar = new ProgressBar(
                this, null,
                android.R.attr.progressBarStyleHorizontal
            );
            bar.setMax(100);
            bar.setProgress((int)Math.min(
                100.0,
                (double)balance * 100.0 / (double)Math.max(1L, goal())
            ));
            bar.setProgressTintList(
                ColorStateList.valueOf(accent())
            );

            LinearLayout.LayoutParams bp =
                new LinearLayout.LayoutParams(-1, dp(8));
            bp.topMargin = dp(12);
            c.addView(bar, bp);
        }

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(0);

        LinearLayout.LayoutParams stp =
            new LinearLayout.LayoutParams(-1, -2);
        stp.topMargin = dp(12);
        content.addView(stats, stp);

        if (blockVisible("rounds")) {
            LinearLayout a = miniCard("КРУГИ", String.valueOf(trips));
            LinearLayout.LayoutParams mp =
                new LinearLayout.LayoutParams(0, -2, 1);
            mp.rightMargin = dp(5);
            stats.addView(a, mp);
        }

        if (blockVisible("payout")) {
            LinearLayout b = miniCard("ВЫПЛАТА", money(pay()) + " ₽");
            LinearLayout.LayoutParams mp =
                new LinearLayout.LayoutParams(0, -2, 1);
            mp.leftMargin = dp(5);
            stats.addView(b, mp);
        }

        if (blockVisible("timer")) {
            LinearLayout timer = card();
            timer.addView(text("ТЕКУЩИЙ РЕЙС", 12, muted(), true));

            timerView = text("00:00:00", 28, fg(), true);
            timer.addView(timerView);

            timer.addView(text(
                running ? "Рейс уже идёт" : "Ожидание начала рейса",
                12, muted(), false
            ));
        } else {
            timerView = null;
        }

        Button start = button(
            running ? "РЕЙС УЖЕ ИДЁТ" : "НАЧАТЬ РЕЙС",
            accent()
        );

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1, dp(52));
        p.topMargin = dp(12);
        content.addView(start, p);

        start.setEnabled(!running);
        start.setOnClickListener(v -> startTrip());

        // П.3: кнопка «Завершить рейс +15000» убрана

        Button finishShift = button(
            running ? "ЗАВЕРШИТЬ СМЕНУ (рейс будет отменён)"
                    : "ЗАВЕРШИТЬ СМЕНУ",
            accent()
        );

        LinearLayout.LayoutParams shiftParams =
            new LinearLayout.LayoutParams(-1, dp(52));
        shiftParams.topMargin = dp(12);
        content.addView(finishShift, shiftParams);

        // Кнопка всегда активна — если идёт рейс, он просто отменится без выплаты
        finishShift.setEnabled(true);
        finishShift.setOnClickListener(v -> confirmFinishShift());

        Button resetStats = button(
            "СБРОСИТЬ ТЕКУЩИЙ БАЛАНС И КРУГИ",
            cardColor()
        );

        LinearLayout.LayoutParams resetParams =
            new LinearLayout.LayoutParams(-1, dp(48));
        resetParams.topMargin = dp(8);
        content.addView(resetStats, resetParams);
        resetStats.setOnClickListener(v -> showHomeResetDialog());

        if (blockVisible("overlay")) {
            Button overlay = button(
                prefs.getBoolean("panel_enabled", false)
                    ? "ПАНЕЛЬ ПОВЕРХ ИГРЫ ВКЛЮЧЕНА"
                    : "ВКЛЮЧИТЬ ПАНЕЛЬ ПОВЕРХ ИГРЫ",
                cardColor()
            );

            LinearLayout.LayoutParams op =
                new LinearLayout.LayoutParams(-1, dp(48));
            op.topMargin = dp(12);
            content.addView(overlay, op);
            overlay.setOnClickListener(v -> toggleOverlay());
        }
    }

    private void showHomeResetDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Сбросить текущий баланс и круги?")
            .setMessage(
                "Будут обнулены только текущий баланс, количество кругов, " +
                "заработок и таймер текущей смены. " +
                "История рейсов, графики и общая статистика останутся без изменений."
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сбросить", (d, which) -> {
                boolean saved = resetShiftNow();
                if (saved) {
                    syncData();
                    renderPage();
                    showInline("Баланс и круги сброшены. История сохранена.");
                } else {
                    infoDialog(
                        "Сброс не выполнен",
                        "Не удалось сохранить сброс. Попробуй ещё раз."
                    );
                }
            })
            .create();

        showThemedDialog(dialog);
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
     * История рейсов в SQLite НЕ трогается — графики и общая статистика остаются.
     */
    private boolean resetShiftNow() {
        try {
            return prefs.edit()
                .putLong("balance", 0L)
                .putLong("trips", 0L)
                .putLong("shiftEarned", 0L)
                .putLong("shiftTrips", 0L)
                .putLong("seconds", 0L)
                .putLong("shiftStartedAt", 0L)
                .putLong("startAt", 0L)
                .putBoolean("running", false)
                .remove("panel_undo_stack")
                .remove("panel_last_trip_id")
                .remove("panel_last_trip_amount")
                .commit();

        } catch (Exception e) {
            return false;
        }
    }

    private void confirmFinishShift() {
        long earned = Math.max(0L, read("shiftEarned"));
        long shiftTrips = Math.max(0L, read("shiftTrips"));

        if (earned <= 0 && shiftTrips <= 0) {
            showInline("В текущей смене пока нет кругов");
            return;
        }

        AlertDialog finishShiftDialog = new AlertDialog.Builder(this)
            .setTitle("Завершить смену?")
            .setMessage(
                "Заработок: " + money(earned) + " ₽\n" +
                "Кругов: " + shiftTrips + "\n" +
                "Время: " + formatTime(seconds) + "\n\n" +
                (running
                    ? "Текущий незавершённый рейс будет отменён без выплаты.\n\n"
                    : "") +
                "Результаты будут сохранены в историю смен. " +
                "Общий баланс и история кругов останутся без изменений."
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Завершить", (dialog, which) ->
                finishShift()
            )
            .create();

        showThemedDialog(finishShiftDialog);
    }

    private void finishShift() {
        long earned = Math.max(0L, read("shiftEarned"));
        long shiftTrips = Math.max(0L, read("shiftTrips"));
        long duration = Math.max(0L, read("seconds"));

        if (earned <= 0 && shiftTrips <= 0) {
            showInline("В текущей смене пока нет кругов");
            return;
        }

        // Сначала останавливаем рейс, если он идёт
        if (running) {
            boolean stopped = prefs.edit()
                .putBoolean("running", false)
                .putLong("startAt", 0L)
                .commit();

            if (!stopped) {
                infoDialog(
                    "Смена не закрыта",
                    "Не удалось остановить текущий рейс. Попробуй ещё раз."
                );
                return;
            }

            running = false;
            startAt = 0L;
            handler.removeCallbacks(ticker);
        }

        long finishTime = System.currentTimeMillis();
        long startedAt = prefs.getLong("shiftStartedAt", 0);

        if (startedAt <= 0 || startedAt > finishTime) {
            startedAt = finishTime;
        }

        try {
            String shiftKey = prefs.getString(
                "pending_shift_key",
                null
            );

            if (shiftKey == null || shiftKey.trim().isEmpty()) {
                shiftKey = UUID.randomUUID().toString();

                boolean keySaved = prefs.edit()
                    .putString("pending_shift_key", shiftKey)
                    .commit();

                if (!keySaved) {
                    infoDialog(
                        "Смена не сохранена",
                        "Не удалось подготовить сохранение смены. Повтори попытку."
                    );
                    return;
                }
            }

            long shiftId = repository.addShift(
                earned,
                shiftTrips,
                duration,
                startedAt,
                finishTime,
                "",
                shiftKey
            );

            if (shiftId <= 0) {
                infoDialog(
                    "Смена не сохранена",
                    "Не удалось сохранить смену. Текущие показатели не сброшены."
                );
                return;
            }

            boolean saved = prefs.edit()
                .putLong("shiftEarned", 0)
                .putLong("shiftTrips", 0)
                .putLong("seconds", 0)
                .putLong("shiftStartedAt", 0)
                .putBoolean("running", false)
                .putLong("startAt", 0)
                .remove("panel_undo_stack")
                .remove("panel_last_trip_id")
                .remove("panel_last_trip_amount")
                .remove("pending_shift_key")
                .commit();

            if (!saved) {
                infoDialog(
                    "Смена сохранена",
                    "Не удалось обновить текущие показатели. Повтори завершение — повторная запись не появится."
                );
                return;
            }

            seconds = 0;
            startAt = 0;
            running = false;

            handler.removeCallbacks(ticker);
            renderPage();

            notifyUser(
                NOTIF_SHIFT,
                "Смена завершена",
                "Заработок: " + money(earned) + " ₽, кругов: " + shiftTrips
            );

            infoDialog(
                "Смена завершена!",
                "Смена сохранена в историю.\n\n" +
                "Заработок: " + money(earned) + " ₽\n" +
                "Кругов: " + shiftTrips + "\n" +
                "Время: " + formatTime(duration) + "\n\n" +
                "Можешь начинать следующую смену."
            );

        } catch (Exception e) {
            infoDialog(
                "Смена не сохранена",
                "Не удалось сохранить смену. Текущие показатели не сброшены."
            );
        }
    }

    private LinearLayout miniCard(String title, String value) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(1);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.setBackground(shape(cardColor(), 16));
        c.addView(text(title, 11, muted(), true));
        c.addView(text(value, 22, fg(), true));
        return c;
    }

    private void startTrip() {
        if (running) return;

        startAt = System.currentTimeMillis();
        running = true;

        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("running", true);
        editor.putLong("startAt", startAt);

        long shiftStartedAt = prefs.getLong("shiftStartedAt", 0);
        long shiftEarned = read("shiftEarned");
        long shiftTrips = read("shiftTrips");

        if (shiftStartedAt <= 0 &&
            shiftEarned == 0 && shiftTrips == 0) {
            editor.putLong("shiftStartedAt", startAt);
        }

        editor.apply();
        renderPage();

        if (prefs.getBoolean("shift_start_notifications", false)) {
            notifyUser(
                NOTIF_SHIFT,
                "Смена начата",
                "Таймер рейса запущен"
            );
        }

        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!running) return;

            if (timerView != null) {
                long elapsed = Math.max(
                    0L,
                    (System.currentTimeMillis() - startAt) / 1000
                );
                timerView.setText(formatTime(elapsed));
            }

            handler.postDelayed(this, 1000);
        }
    };

    private void showStats() {
        heading("Статистика", "Твои результаты и личные рекорды");

        long today = 0;
        long week = 0;
        long todayTrips = 0;
        long weekTrips = 0;
        long totalAmount = 0;
        long totalCount = 0;
        long totalDuration = 0;
        long durationCount = 0;

        HashMap<String, Long> daily = new HashMap<>();
        HashMap<String, Long> dailyCounts = new HashMap<>();
        HashSet<String> allDays = new HashSet<>();
        long[] weekdayAmounts = new long[7];

        long todayStart = startOfDay(System.currentTimeMillis());
        Calendar weekStart = Calendar.getInstance();
        weekStart.setTimeInMillis(todayStart);
        weekStart.add(Calendar.DAY_OF_YEAR, -6);
        long weekStartTime = weekStart.getTimeInMillis();

        SimpleDateFormat dayKey = new SimpleDateFormat(
            "yyyyMMdd", Locale.US
        );

        Cursor cursor = null;

        try {
            cursor = repository.getTripsHistory();
            if (cursor == null) throw new Exception();

            int amountColumn = cursor.getColumnIndexOrThrow("amount");
            int timeColumn = cursor.getColumnIndexOrThrow("created_at");
            int durationColumn = cursor.getColumnIndex("duration");

            while (cursor.moveToNext()) {
                long time = cursor.getLong(timeColumn);
                long amount = cursor.getLong(amountColumn);
                long itemDuration = durationColumn >= 0
                    ? cursor.getLong(durationColumn) : 0L;
                long itemDayStart = startOfDay(time);

                long earningsReset =
                    repository.getDailyMetricReset(itemDayStart, "earnings");
                long tripsReset =
                    repository.getDailyMetricReset(itemDayStart, "trips");

                boolean includeEarnings =
                    earningsReset <= 0 || time >= earningsReset;
                boolean includeTrip =
                    tripsReset <= 0 || time >= tripsReset;

                Calendar itemDay = Calendar.getInstance();
                itemDay.setTimeInMillis(time);
                String key = dayKey.format(itemDay.getTime());
                allDays.add(key);

                if (includeEarnings) {
                    totalAmount = safeAdd(totalAmount, amount);
                    long value = safeAdd(
                        daily.containsKey(key) ? daily.get(key) : 0L,
                        amount
                    );
                    daily.put(key, value);

                    int wd = itemDay.get(Calendar.DAY_OF_WEEK) - 1;
                    weekdayAmounts[wd] = safeAdd(weekdayAmounts[wd], amount);

                    if (time >= todayStart) {
                        today = safeAdd(today, amount);
                    }
                    if (time >= weekStartTime) {
                        week = safeAdd(week, amount);
                    }
                }

                if (includeTrip) {
                    totalCount++;
                    long count = safeAdd(
                        dailyCounts.containsKey(key)
                            ? dailyCounts.get(key) : 0L,
                        1L
                    );
                    dailyCounts.put(key, count);

                    if (time >= todayStart) todayTrips++;
                    if (time >= weekStartTime) weekTrips++;
                }

                if (itemDuration > 0) {
                    totalDuration = safeAdd(totalDuration, itemDuration);
                    durationCount++;
                }
            }

        } catch (Exception e) {
            infoDialog("Статистика", "Не удалось загрузить статистику.");
        } finally {
            if (cursor != null) cursor.close();
        }

        LinearLayout a = card();
        a.addView(text("СЕГОДНЯ", 12, muted(), true));
        a.addView(text(money(today) + " ₽", 29, accent(), true));
        a.addView(text("Кругов: " + todayTrips, 14, fg(), false));

        LinearLayout b = card();
        b.addView(text("ПОСЛЕДНИЕ 7 ДНЕЙ", 12, muted(), true));
        b.addView(text(money(week) + " ₽", 29, accent(), true));
        b.addView(text("Кругов: " + weekTrips, 14, fg(), false));

        LinearLayout all = card();
        all.addView(text("ЗА ВСЁ ВРЕМЯ", 12, muted(), true));
        all.addView(text(money(totalAmount) + " ₽", 25, fg(), true));
        all.addView(text("Всего кругов: " + totalCount, 14, fg(), false));
        all.addView(text(
            "Средний заработок за круг: " +
            money(totalCount > 0 ? totalAmount / totalCount : 0) + " ₽",
            14, muted(), false
        ));

        long avgDuration = durationCount > 0
            ? totalDuration / durationCount : 0L;

        all.addView(text(
            "Средняя длительность рейса: " + formatTime(avgDuration),
            14, muted(), false
        ));

        int bestWeekday = -1;
        long bestWeekdayAmount = 0;
        for (int i = 0; i < 7; i++) {
            if (weekdayAmounts[i] > bestWeekdayAmount) {
                bestWeekdayAmount = weekdayAmounts[i];
                bestWeekday = i;
            }
        }

        if (bestWeekday >= 0 && bestWeekdayAmount > 0) {
            String[] weekdays = {
                "Воскресенье", "Понедельник", "Вторник",
                "Среда", "Четверг", "Пятница", "Суббота"
            };
            all.addView(text(
                "Самый прибыльный день недели: " +
                weekdays[bestWeekday] + " (" +
                money(bestWeekdayAmount) + " ₽)",
                14, accent(), true
            ));
        }

        all.addView(text(
            "Серия дней с рейсами: " + calculateStreak(allDays),
            14, muted(), false
        ));

        long shiftCount = 0;
        long bestShift = 0;
        Cursor shifts = null;

        try {
            shifts = repository.getShiftsHistory();

            if (shifts != null) {
                int earnedColumn = shifts.getColumnIndex("earned");
                while (shifts.moveToNext()) {
                    shiftCount++;
                    if (earnedColumn >= 0) {
                        bestShift = Math.max(
                            bestShift,
                            shifts.getLong(earnedColumn)
                        );
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (shifts != null) shifts.close();
        }

        LinearLayout shiftStats = card();
        shiftStats.addView(text("СМЕНЫ", 15, fg(), true));
        shiftStats.addView(text(
            "Всего смен: " + shiftCount, 14, fg(), false
        ));
        shiftStats.addView(text(
            "Лучший заработок за смену: " + money(bestShift) + " ₽",
            14, accent(), true
        ));

        LinearLayout daysCard = card();
        daysCard.addView(text("СТАТИСТИКА ПО ДНЯМ", 15, fg(), true));
        daysCard.addView(text(
            "Можно отдельно сбросить заработок или количество кругов. " +
            "Баланс и история рейсов при этом не меняются.",
            12, muted(), false
        ));

        SimpleDateFormat dateFormat = new SimpleDateFormat(
            "dd.MM.yyyy", Locale.getDefault()
        );

        ArrayList<String> sortedDays = new ArrayList<>(allDays);
        Collections.sort(sortedDays, Collections.reverseOrder());

        for (String key : sortedDays) {
            try {
                Date parsedDay = dayKey.parse(key);
                if (parsedDay == null) continue;

                long dayStart = startOfDay(parsedDay.getTime());
                long amount = daily.containsKey(key) ? daily.get(key) : 0L;
                long count = dailyCounts.containsKey(key)
                    ? dailyCounts.get(key) : 0L;

                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setOrientation(LinearLayout.HORIZONTAL);

                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                info.addView(text(
                    dateFormat.format(parsedDay),
                    13, fg(), true
                ));
                info.addView(text(
                    money(amount) + " ₽  •  " + count + " кругов",
                    12, muted(), false
                ));

                row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

                Button resetDay = button("СБРОС", cardColor());
                row.addView(
                    resetDay,
                    new LinearLayout.LayoutParams(dp(82), dp(40))
                );
                resetDay.setOnClickListener(v ->
                    showDailyResetDialog(dayStart)
                );

                LinearLayout.LayoutParams rowParams =
                    new LinearLayout.LayoutParams(-1, -2);
                rowParams.topMargin = dp(10);
                daysCard.addView(row, rowParams);
            } catch (Exception ignored) {
            }
        }

        LinearLayout records = card();
        records.addView(text("ЛИЧНЫЕ РЕКОРДЫ", 15, fg(), true));

        long bestDay = read("best_day_amount");
        long bestDayTime = read("best_day_time");

        if (bestDay <= 0) {
            for (String key : daily.keySet()) {
                long value = daily.get(key);
                if (value > bestDay) {
                    bestDay = value;
                    try {
                        Date d = dayKey.parse(key);
                        bestDayTime = d != null ? d.getTime() : 0L;
                    } catch (Exception ignored) {
                        bestDayTime = 0L;
                    }
                }
            }
        }

        records.addView(text(
            "Самый прибыльный день: " + money(bestDay) + " ₽",
            14, accent(), true
        ));

        if (bestDayTime > 0) {
            records.addView(text(
                new SimpleDateFormat(
                    "dd.MM.yyyy", Locale.getDefault()
                ).format(new Date(bestDayTime)),
                12, muted(), false
            ));
        }

        Button resetBest = button("СБРОСИТЬ ЛУЧШИЙ ДЕНЬ", cardColor());
        records.addView(
            resetBest,
            new LinearLayout.LayoutParams(-1, dp(44))
        );
        resetBest.setOnClickListener(v -> {
            prefs.edit()
                .putLong("best_day_amount", 0L)
                .putLong("best_day_time", 0L)
                .apply();
            renderPage();
            showInline("Лучший день заработка сброшен");
        });
    }

    private int calculateStreak(HashSet<String> allDays) {
        if (allDays.isEmpty()) return 0;

        SimpleDateFormat dayKey = new SimpleDateFormat(
            "yyyyMMdd", Locale.US
        );

        Calendar cursor = Calendar.getInstance();
        cursor.set(Calendar.HOUR_OF_DAY, 0);
        cursor.set(Calendar.MINUTE, 0);
        cursor.set(Calendar.SECOND, 0);
        cursor.set(Calendar.MILLISECOND, 0);

        String todayKey = dayKey.format(cursor.getTime());

        if (!allDays.contains(todayKey)) {
            cursor.add(Calendar.DAY_OF_YEAR, -1);
            if (!allDays.contains(dayKey.format(cursor.getTime()))) {
                return 0;
            }
        }

        int streak = 0;

        while (true) {
            String key = dayKey.format(cursor.getTime());
            if (allDays.contains(key)) {
                streak++;
                cursor.add(Calendar.DAY_OF_YEAR, -1);
            } else {
                break;
            }

            if (streak > 3650) break;
        }

        return streak;
    }

    private void showDailyResetDialog(long dayStart) {
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(dayStart);
        String date = new SimpleDateFormat(
            "dd.MM.yyyy", Locale.getDefault()
        ).format(day.getTime());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), 0);

        CheckBox earnings = new CheckBox(this);
        earnings.setText("Сбросить заработок за этот день");
        earnings.setTextColor(fg());
        earnings.setButtonTintList(
            ColorStateList.valueOf(accent())
        );
        box.addView(earnings);

        CheckBox tripsCount = new CheckBox(this);
        tripsCount.setText("Сбросить количество кругов за этот день");
        tripsCount.setTextColor(fg());
        tripsCount.setButtonTintList(
            ColorStateList.valueOf(accent())
        );
        box.addView(tripsCount);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Сброс показателей — " + date)
            .setMessage(
                "Выбери, какие показатели убрать из статистики. " +
                "Записи в истории и общий баланс сохранятся."
            )
            .setView(box)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сбросить", (d, which) -> {
                if (!earnings.isChecked() && !tripsCount.isChecked()) {
                    showInline("Выбери хотя бы один показатель");
                    return;
                }

                long resetAt = System.currentTimeMillis();
                boolean success = true;

                if (earnings.isChecked()) {
                    success &= repository.saveDailyMetricReset(
                        dayStart, "earnings", resetAt
                    );
                }

                if (tripsCount.isChecked()) {
                    success &= repository.saveDailyMetricReset(
                        dayStart, "trips", resetAt
                    );
                }

                if (success) {
                    renderPage();
                    showInline("Выбранные показатели сброшены. Баланс не изменён.");
                } else {
                    infoDialog(
                        "Сброс не выполнен",
                        "Не удалось сохранить сброс показателей."
                    );
                }
            })
            .create();

        showThemedDialog(dialog);
    }

    private void showHistory() {
        heading("История кругов", "Сохранённые записи рейсов");

        Cursor cursor = null;

        try {
            cursor = repository.getTripsHistory();

            long todayStart = startOfDay(System.currentTimeMillis());
            long todayCount = 0;
            long previousDaysCount = 0;

            HashMap<String, Long> byDayCount = new HashMap<>();
            HashMap<String, Long> byDayAmount = new HashMap<>();
            SimpleDateFormat dayKey = new SimpleDateFormat(
                "yyyyMMdd", Locale.US
            );

            if (cursor != null) {
                int summaryTimeColumn =
                    cursor.getColumnIndexOrThrow("created_at");
                int summaryAmountColumn =
                    cursor.getColumnIndexOrThrow("amount");

                while (cursor.moveToNext()) {
                    long time = cursor.getLong(summaryTimeColumn);
                    long amount = cursor.getLong(summaryAmountColumn);

                    if (time >= todayStart) {
                        todayCount++;
                    } else {
                        previousDaysCount++;

                        Calendar d = Calendar.getInstance();
                        d.setTimeInMillis(time);
                        String key = dayKey.format(d.getTime());

                        byDayCount.put(
                            key,
                            (byDayCount.containsKey(key)
                                ? byDayCount.get(key) : 0L) + 1
                        );
                        byDayAmount.put(
                            key,
                            (byDayAmount.containsKey(key)
                                ? byDayAmount.get(key) : 0L) + amount
                        );
                    }
                }

                cursor.moveToPosition(-1);
            }

            LinearLayout summary = card();
            summary.addView(text("КОЛИЧЕСТВО КРУГОВ", 12, muted(), true));
            summary.addView(text(
                "Сегодня: " + todayCount,
                18, accent(), true
            ));

            final HashMap<String, Long> dayCounts = byDayCount;
            final HashMap<String, Long> dayAmounts = byDayAmount;

            Button prevDaysBtn = button(
                "За предыдущие дни: " + previousDaysCount,
                cardColor()
            );
            summary.addView(
                prevDaysBtn,
                new LinearLayout.LayoutParams(-1, dp(44))
            );
            prevDaysBtn.setOnClickListener(v ->
                showDaysBreakdown(dayCounts, dayAmounts)
            );

            if (cursor == null || cursor.getCount() == 0) {
                card().addView(text(
                    "Пока нет записей. Добавь первый круг!",
                    14, muted(), false
                ));
                return;
            }

            int amountColumn = cursor.getColumnIndexOrThrow("amount");
            int timeColumn = cursor.getColumnIndexOrThrow("created_at");
            int durationColumn = cursor.getColumnIndexOrThrow("duration");
            int routeColumn = cursor.getColumnIndexOrThrow("route");
            int noteColumn = cursor.getColumnIndexOrThrow("note");

            SimpleDateFormat fmt = new SimpleDateFormat(
                "dd.MM.yyyy  HH:mm", Locale.getDefault()
            );

            int number = cursor.getCount();

            while (cursor.moveToNext()) {
                long amount = cursor.getLong(amountColumn);
                long time = cursor.getLong(timeColumn);
                long duration = cursor.getLong(durationColumn);

                String route = cursor.getString(routeColumn);
                String note = cursor.getString(noteColumn);

                LinearLayout c = card();

                c.addView(text(
                    "Запись №" + number, 12, muted(), true
                ));

                c.addView(text(
                    "+" + money(amount) + " ₽",
                    21, accent(), true
                ));

                c.addView(text(
                    time > 0 ? fmt.format(new Date(time))
                        : "Время неизвестно",
                    12, fg(), false
                ));

                if (duration > 0) {
                    c.addView(text(
                        "Длительность: " + formatTime(duration),
                        12, muted(), false
                    ));
                }

                if (route != null && !route.isEmpty()) {
                    c.addView(text(
                        "Маршрут: " + route, 12, fg(), false
                    ));
                }

                if (note != null && !note.isEmpty()) {
                    c.addView(text(
                        "Заметка: " + note, 12, muted(), false
                    ));
                }

                number--;
            }

        } catch (Exception e) {
            card().addView(text(
                "Не удалось загрузить историю",
                14, muted(), false
            ));
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void showDaysBreakdown(
        HashMap<String, Long> counts,
        HashMap<String, Long> amounts
    ) {
        if (counts.isEmpty()) {
            infoDialog("Прошлые дни", "Записей за прошлые дни нет.");
            return;
        }

        SimpleDateFormat dayKey = new SimpleDateFormat(
            "yyyyMMdd", Locale.US
        );
        SimpleDateFormat display = new SimpleDateFormat(
            "dd.MM.yyyy", Locale.getDefault()
        );

        ArrayList<String> keys = new ArrayList<>(counts.keySet());
        Collections.sort(keys, Collections.reverseOrder());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(1);
        box.setPadding(dp(12), dp(8), dp(12), 0);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);

        for (String key : keys) {
            try {
                Date d = dayKey.parse(key);
                if (d == null) continue;

                long cnt = counts.get(key) != null ? counts.get(key) : 0L;
                long amt = amounts.get(key) != null ? amounts.get(key) : 0L;

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(1);
                row.setPadding(dp(10), dp(10), dp(10), dp(10));
                row.setBackground(shape(cardColor(), 12));

                row.addView(text(display.format(d), 14, fg(), true));
                row.addView(text(
                    money(amt) + " ₽  •  " + cnt + " кругов",
                    12, muted(), false
                ));

                LinearLayout.LayoutParams rp =
                    new LinearLayout.LayoutParams(-1, -2);
                rp.topMargin = dp(8);
                box.addView(row, rp);

            } catch (Exception ignored) {
            }
        }

        AlertDialog d = new AlertDialog.Builder(this)
            .setTitle("Круги за прошлые дни")
            .setView(scroll)
            .setPositiveButton("Закрыть", null)
            .create();

        showThemedDialog(d);
    }

    private JSONArray getGoals() {
        try {
            return new JSONArray(prefs.getString("custom_goals", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void saveGoals(JSONArray goals) {
        prefs.edit().putString("custom_goals", goals.toString()).apply();
    }

    private void showCalculator() {
        heading("Калькулятор целей", "Создавай и отслеживай свои накопления");

        Button add = button("+ ДОБАВИТЬ ЦЕЛЬ", accent());
        LinearLayout.LayoutParams ap =
            new LinearLayout.LayoutParams(-1, dp(48));
        ap.topMargin = dp(12);
        content.addView(add, ap);
        add.setOnClickListener(v -> showGoalEditor(-1));

        JSONArray goals = getGoals();

        if (goals.length() == 0) {
            card().addView(text(
                "Пока нет пользовательских целей. Добавь первую!",
                14, muted(), false
            ));
        }

        for (int i = 0; i < goals.length(); i++) {
            final int index = i;

            try {
                JSONObject goal = goals.getJSONObject(i);
                String name = goal.optString("name", "Моя цель");
                long amount = Math.max(1L, goal.optLong("amount", 1));
                long roundsPerDay = Math.max(1L, goal.optLong("rounds", 1));

                long remaining = Math.max(0L, amount - balance);
                long roundsNeeded = remaining / pay();

                if (remaining % pay() != 0) roundsNeeded++;

                long expected = safeMultiply(roundsPerDay, pay());
                long days = roundsNeeded == 0 ? 0 :
                    (roundsNeeded / roundsPerDay +
                    (roundsNeeded % roundsPerDay == 0 ? 0 : 1));

                double percent = Math.min(
                    100.0,
                    (double)balance * 100.0 / Math.max(1L, amount)
                );

                LinearLayout c = card();

                c.addView(text(name, 19, fg(), true));
                c.addView(text(
                    "Цель: " + money(amount) + " ₽",
                    13, muted(), false
                ));

                c.addView(text(
                    "Осталось: " + money(remaining) + " ₽",
                    16, accent(), true
                ));

                ProgressBar bar = new ProgressBar(
                    this, null,
                    android.R.attr.progressBarStyleHorizontal
                );
                bar.setMax(100);
                bar.setProgress((int)percent);
                bar.setProgressTintList(
                    ColorStateList.valueOf(accent())
                );

                LinearLayout.LayoutParams bp =
                    new LinearLayout.LayoutParams(-1, dp(8));
                bp.topMargin = dp(10);
                c.addView(bar, bp);

                c.addView(text(
                    "Выполнено: " + String.format(
                        Locale.US, "%.1f", percent
                    ) + "%",
                    13, fg(), false
                ));

                c.addView(text(
                    "Осталось процентов: " +
                    String.format(
                        Locale.US, "%.1f",
                        Math.max(0, 100.0 - percent)
                    ) + "%",
                    13, muted(), false
                ));

                c.addView(text(
                    "Кругов осталось: " + roundsNeeded,
                    14, fg(), true
                ));

                c.addView(text(
                    "План: " + roundsPerDay + " кругов в день",
                    13, muted(), false
                ));

                c.addView(text(
                    "Ожидаемый заработок в день: " +
                    money(expected) + " ₽",
                    13, accent(), true
                ));

                c.addView(text(
                    "Примерно дней до цели: " + days,
                    13, fg(), false
                ));

                Button editButton = button(
                    "ИЗМЕНИТЬ ЦЕЛЬ", cardColor()
                );
                c.addView(
                    editButton,
                    new LinearLayout.LayoutParams(-1, dp(44))
                );
                editButton.setOnClickListener(
                    v -> showGoalEditor(index)
                );

                Button deleteButton = button(
                    "УДАЛИТЬ ЦЕЛЬ", cardColor()
                );
                c.addView(
                    deleteButton,
                    new LinearLayout.LayoutParams(-1, dp(44))
                );
                deleteButton.setOnClickListener(
                    v -> confirmDeleteGoal(index)
                );

            } catch (Exception ignored) {
            }
        }
    }

    private void showGoalEditor(int index) {
        JSONArray goals = getGoals();

        String oldName = "";
        String oldAmount = "";
        String oldRounds = "5";

        if (index >= 0 && index < goals.length()) {
            JSONObject item = goals.optJSONObject(index);
            if (item != null) {
                oldName = item.optString("name", "");
                oldAmount = String.valueOf(item.optLong("amount"));
                oldRounds = String.valueOf(item.optLong("rounds", 5));
            }
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(1);
        box.setPadding(dp(18), dp(8), dp(18), 0);

        EditText name = edit("Название цели", InputType.TYPE_CLASS_TEXT);
        name.setText(oldName);
        box.addView(name);

        EditText amount = edit(
            "Сумма цели в рублях",
            InputType.TYPE_CLASS_NUMBER
        );
        amount.setText(oldAmount);
        box.addView(amount);

        EditText rounds = edit(
            "Кругов в день",
            InputType.TYPE_CLASS_NUMBER
        );
        rounds.setText(oldRounds);
        box.addView(rounds);

        AlertDialog goalEditorDialog = new AlertDialog.Builder(this)
            .setTitle(index < 0 ? "Новая цель" : "Изменить цель")
            .setView(box)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить", (d, w) -> {
                try {
                    String n = name.getText().toString().trim();
                    long a = Long.parseLong(
                        amount.getText().toString().trim()
                    );
                    long r = Long.parseLong(
                        rounds.getText().toString().trim()
                    );

                    if (n.isEmpty() || a <= 0 || r <= 0) {
                        throw new Exception();
                    }

                    JSONObject item = new JSONObject();
                    item.put("name", n);
                    item.put("amount", a);
                    item.put("rounds", r);

                    if (index >= 0 && index < goals.length()) {
                        goals.put(index, item);
                    } else {
                        goals.put(item);
                    }

                    saveGoals(goals);
                    renderPage();

                } catch (Exception e) {
                    infoDialog(
                        "Цель не сохранена",
                        "Проверь название, сумму и количество кругов."
                    );
                }
            })
            .create();

        showThemedDialog(goalEditorDialog);
    }

    private void confirmDeleteGoal(int index) {
        AlertDialog deleteGoalDialog = new AlertDialog.Builder(this)
            .setTitle("Удалить цель?")
            .setMessage("Эта цель будет удалена.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить", (d, w) -> {
                JSONArray old = getGoals();
                JSONArray updated = new JSONArray();

                for (int i = 0; i < old.length(); i++) {
                    if (i != index) updated.put(old.opt(i));
                }

                saveGoals(updated);
                renderPage();
            })
            .create();

        showThemedDialog(deleteGoalDialog);
    }

    private void showCharts() {
        heading(
            "Графики заработка",
            "Сумма, дата и день недели за последние 7 дней"
        );

        LinearLayout c = card();
        c.addView(
            new EarningsChart(this),
            new LinearLayout.LayoutParams(-1, dp(270))
        );

        LinearLayout info = card();
        info.addView(text(
            "Данные берутся из сохранённой истории рейсов.",
            13, muted(), false
        ));
    }

    private class EarningsChart extends View {

        Paint paint = new Paint(3);

        EarningsChart(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int w = getWidth();
            int h = getHeight();

            long[] values = new long[7];
            Calendar today = Calendar.getInstance();

            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            Calendar[] days = new Calendar[7];

            for (int i = 0; i < 7; i++) {
                days[i] = (Calendar)today.clone();
                days[i].add(Calendar.DAY_OF_YEAR, i - 6);
            }

            Cursor cursor = null;

            try {
                cursor = repository.getTripsHistory();

                if (cursor != null) {
                    int timeColumn =
                        cursor.getColumnIndexOrThrow("created_at");
                    int amountColumn =
                        cursor.getColumnIndexOrThrow("amount");

                    while (cursor.moveToNext()) {
                        long time = cursor.getLong(timeColumn);
                        long amount = cursor.getLong(amountColumn);

                        for (int i = 0; i < 7; i++) {
                            Calendar next = (Calendar)days[i].clone();
                            next.add(Calendar.DAY_OF_YEAR, 1);

                            if (time >= days[i].getTimeInMillis() &&
                                time < next.getTimeInMillis()) {
                                long resetAt = repository.getDailyMetricReset(
                                    days[i].getTimeInMillis(),
                                    "earnings"
                                );

                                if (resetAt <= 0 || time >= resetAt) {
                                    values[i] = safeAdd(values[i], amount);
                                }
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) cursor.close();
            }

            long max = 1;
            for (long value : values) {
                max = Math.max(max, value);
            }

            float leftPad = dp(8);
            float gap = dp(7);
            float barW = (w - dp(16) - gap * 6) / 7f;
            float base = h - dp(48);
            float maxHeight = h - dp(105);

            paint.setColor(muted());
            paint.setStrokeWidth(dp(1));
            canvas.drawLine(
                dp(5), base, w - dp(5), base, paint
            );

            SimpleDateFormat dateFormat = new SimpleDateFormat(
                "dd.MM", Locale.getDefault()
            );
            SimpleDateFormat dayFormat = new SimpleDateFormat(
                "EE", Locale.getDefault()
            );

            for (int i = 0; i < 7; i++) {
                float left = leftPad + i * (barW + gap);
                float bh = (float)(
                    ((double)values[i] / (double)max) * maxHeight
                );

                paint.setColor(accent());
                canvas.drawRoundRect(
                    left, base - bh, left + barW, base,
                    dp(5), dp(5), paint
                );

                paint.setColor(fg());
                paint.setTextSize(dp(9));
                paint.setTextAlign(Paint.Align.CENTER);

                String amount = values[i] > 0
                    ? compactMoney(values[i])
                    : "0";

                canvas.drawText(
                    amount,
                    left + barW / 2,
                    Math.max(dp(12), base - bh - dp(5)),
                    paint
                );

                paint.setTextSize(dp(9));
                canvas.drawText(
                    dayFormat.format(days[i].getTime()),
                    left + barW / 2,
                    h - dp(27),
                    paint
                );

                canvas.drawText(
                    dateFormat.format(days[i].getTime()),
                    left + barW / 2,
                    h - dp(10),
                    paint
                );
            }

            paint.setTextAlign(Paint.Align.LEFT);
        }
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

    private void showThemes() {
        heading(
            "Оформление",
            "Выбери цветовую тему приложения"
        );

        String[] names = {
            "Фиолетовая", "Светлая", "Чёрная", "Красная",
            "Зелёная", "Жёлтая", "Синяя", "Голубая",
            "Розовая", "Оранжевая"
        };

        for (int i = 0; i < names.length; i++) {
            final int index = i;

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(shape(cardColor(), 14));

            LinearLayout.LayoutParams rp =
                new LinearLayout.LayoutParams(-1, dp(56));
            rp.topMargin = dp(7);
            content.addView(row, rp);

            View swatch = new View(this);
            swatch.setBackground(shape(accents[i], 30));
            row.addView(
                swatch,
                new LinearLayout.LayoutParams(dp(30), dp(30))
            );

            TextView name = text(names[i], 15, fg(), true);
            LinearLayout.LayoutParams np =
                new LinearLayout.LayoutParams(0, -2, 1);
            np.leftMargin = dp(12);
            row.addView(name, np);

            TextView selected = text(
                prefs.getInt("theme", 0) == i ? "✓" : "›",
                22, accents[i], true
            );
            row.addView(selected);

            row.setOnClickListener(v -> {
                prefs.edit().putInt("theme", index).apply();
                rebuildBase();
            });
        }
    }

    private void showSettings() {
        heading(
            "Настройки",
            "Управление приложением и внешним видом"
        );

        LinearLayout sound = card();
        sound.addView(text("ЗВУК И ВИБРАЦИЯ", 14, fg(), true));

        addSwitch(sound, "Звуки приложения", "sound_enabled", true);
        addSwitch(sound, "Вибрация", "vibration_enabled", true);
        addSwitch(sound, "Тихий режим", "silent_mode", false);

        addSeekBar(sound, "Громкость звуков", "sound_volume", 0, 100, 70);

        LinearLayout panel = card();
        panel.addView(text("ПЛАВАЮЩАЯ ПАНЕЛЬ", 14, fg(), true));

        addSeekBar(
            panel, "Размер панели", "panel_size",
            70, 130, 100
        );

        addSeekBar(
            panel, "Прозрачность панели", "panel_opacity",
            30, 100, 100
        );

        addSwitch(
            panel,
            "Показывать панель поверх игры",
            "panel_enabled",
            false
        );

        Button themes = button("ПЕРЕЙТИ К ОФОРМЛЕНИЮ", accent());
        panel.addView(
            themes,
            new LinearLayout.LayoutParams(-1, dp(46))
        );
        themes.setOnClickListener(v -> {
            page = "theme";
            renderPage();
        });

        LinearLayout currency = card();
        currency.addView(text("ВАЛЮТА И ФОРМАТ", 14, fg(), true));
        addSwitch(
            currency,
            "Компактный формат сумм (к, м)",
            "currency_compact",
            false
        );

        LinearLayout notifications = card();
        notifications.addView(text(
            "УВЕДОМЛЕНИЯ", 14, fg(), true
        ));

        addSwitch(
            notifications,
            "Показывать уведомления",
            "notifications_enabled",
            true
        );

        addSwitch(
            notifications,
            "Уведомление о завершении рейса",
            "trip_finish_notifications",
            true
        );

        addSwitch(
            notifications,
            "Уведомление о начале смены",
            "shift_start_notifications",
            false
        );

        LinearLayout home = card();
        home.addView(text(
            "ГЛАВНЫЙ ЭКРАН", 14, fg(), true
        ));

        Button customize = button(
            "НАСТРОИТЬ БЛОКИ ГЛАВНОГО ЭКРАНА",
            accent()
        );

        home.addView(
            customize,
            new LinearLayout.LayoutParams(-1, dp(48))
        );

        customize.setOnClickListener(v -> showHomeCustomization());

        LinearLayout backup = card();
        backup.addView(text("ДАННЫЕ", 14, fg(), true));
        backup.addView(text(
            "Экспорт копирует статистику в буфер обмена " +
            "в текстовом виде.",
            12, muted(), false
        ));

        Button exportButton = button(
            "ЭКСПОРТ ДАННЫХ",
            cardColor()
        );
        backup.addView(
            exportButton,
            new LinearLayout.LayoutParams(-1, dp(46))
        );
        exportButton.setOnClickListener(v -> exportData());

        LinearLayout reset = card();
        reset.addView(text(
            "СБРОС НАСТРОЕК", 14, fg(), true
        ));

        reset.addView(text(
            "Вернуть стандартные параметры приложения. " +
            "Статистика и история рейсов останутся без изменений.",
            13, muted(), false
        ));

        Button resetButton = button(
            "СБРОСИТЬ НАСТРОЙКИ",
            cardColor()
        );

        reset.addView(
            resetButton,
            new LinearLayout.LayoutParams(-1, dp(46))
        );

        resetButton.setOnClickListener(v -> confirmSettingsReset());
    }

    private void exportData() {
        try {
            JSONObject data = new JSONObject();
            data.put("balance", balance);
            data.put("trips", trips);
            data.put("seconds", seconds);
            data.put("theme", theme());
            data.put("pay", pay());
            data.put("goal", goal());
            data.put("custom_goals", getGoals().toString());

            JSONArray shiftArray = new JSONArray();
            Cursor shifts = repository.getShiftsHistory();
            if (shifts != null) {
                int earned = shifts.getColumnIndex("earned");
                int count = shifts.getColumnIndex("trip_count");
                int dur = shifts.getColumnIndex("duration");
                int started = shifts.getColumnIndex("started_at");
                int finished = shifts.getColumnIndex("finished_at");
                while (shifts.moveToNext()) {
                    JSONObject s = new JSONObject();
                    if (earned >= 0) s.put("earned", shifts.getLong(earned));
                    if (count >= 0) s.put("trips", shifts.getLong(count));
                    if (dur >= 0) s.put("duration", shifts.getLong(dur));
                    if (started >= 0) s.put("started_at", shifts.getLong(started));
                    if (finished >= 0) s.put("finished_at", shifts.getLong(finished));
                    shiftArray.put(s);
                }
                shifts.close();
            }
            data.put("shifts", shiftArray.toString());

            JSONArray tripArray = new JSONArray();
            Cursor tripsCursor = repository.getTripsHistory();
            if (tripsCursor != null) {
                int amount = tripsCursor.getColumnIndex("amount");
                int time = tripsCursor.getColumnIndex("created_at");
                int dur = tripsCursor.getColumnIndex("duration");
                while (tripsCursor.moveToNext()) {
                    JSONObject t = new JSONObject();
                    if (amount >= 0) t.put("amount", tripsCursor.getLong(amount));
                    if (time >= 0) t.put("created_at", tripsCursor.getLong(time));
                    if (dur >= 0) t.put("duration", tripsCursor.getLong(dur));
                    tripArray.put(t);
                }
                tripsCursor.close();
            }
            data.put("trips_history", tripArray.toString());

            android.content.ClipboardManager cm =
                (android.content.ClipboardManager)getSystemService(
                    Context.CLIPBOARD_SERVICE
                );
            cm.setPrimaryClip(
                android.content.ClipData.newPlainText(
                    "TruckLog Export", data.toString(2)
                )
            );

            infoDialog(
                "Экспорт готов",
                "Копия статистики скопирована в буфер обмена. " +
                "Вставь её в заметки или сообщение, чтобы сохранить."
            );
        } catch (Exception e) {
            infoDialog(
                "Экспорт не выполнен",
                "Не удалось сформировать копию данных."
            );
        }
    }

    private void addSwitch(
        LinearLayout parent,
        String title,
        String key,
        boolean defaultValue
    ) {
        Switch sw = new Switch(this);
        sw.setText(title);
        sw.setTextColor(fg());
        sw.setTextSize(14);
        sw.setChecked(prefs.getBoolean(key, defaultValue));

        sw.setOnCheckedChangeListener((button, checked) -> {
            if ("panel_enabled".equals(key)) {
                setOverlayEnabled(checked);
            } else {
                prefs.edit().putBoolean(key, checked).apply();
            }
        });

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1, dp(48));
        parent.addView(sw, p);
    }

    private void addSeekBar(
        LinearLayout parent,
        String title,
        String key,
        int min,
        int max,
        int defaultValue
    ) {
        TextView label = text(
            title + ": " + prefs.getInt(key, defaultValue),
            13, fg(), true
        );

        parent.addView(label);

        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(
            Math.max(0, Math.min(
                max - min,
                prefs.getInt(key, defaultValue) - min
            ))
        );

        seek.setProgressTintList(
            ColorStateList.valueOf(accent())
        );

        seek.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(
                    SeekBar bar, int progress, boolean fromUser
                ) {
                    int value = min + progress;
                    label.setText(title + ": " + value);

                    if (fromUser) {
                        prefs.edit().putInt(key, value).apply();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {}

                @Override
                public void onStopTrackingTouch(SeekBar bar) {}
            }
        );

        parent.addView(seek);
    }

    private void confirmSettingsReset() {
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Сбросить настройки?")
            .setMessage(
                "Тема, настройки панели, звука, вибрации, " +
                "уведомлений и главного экрана вернутся к стандартным. " +
                "Заработок, статистика, цели и история рейсов " +
                "останутся без изменений. Плавающая панель будет выключена."
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сбросить", (d, w) -> {
                stopService(
                    new Intent(this, FloatingPanelService.class)
                );

                SharedPreferences.Editor e = prefs.edit();

                e.putInt("theme", 0);
                e.putBoolean("panel_enabled", false);

                e.remove("sound_enabled");
                e.remove("sound_volume");
                e.remove("vibration_enabled");
                e.remove("silent_mode");
                e.remove("currency_compact");
                e.remove("panel_size");
                e.remove("panel_opacity");
                e.remove("notifications_enabled");
                e.remove("trip_finish_notifications");
                e.remove("shift_start_notifications");

                e.remove("home_block_balance");
                e.remove("home_block_rounds");
                e.remove("home_block_payout");
                e.remove("home_block_timer");
                e.remove("home_block_overlay");
                e.remove("home_block_order");

                e.apply();

                renderPage();

                showInline("Настройки сброшены");
            })
            .create();

        showThemedDialog(dialog);
    }

    private void showHomeCustomization() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(1);
        box.setPadding(dp(18), dp(8), dp(18), 0);

        CheckBox[] checks = new CheckBox[blockKeys.length];

        for (int i = 0; i < blockKeys.length; i++) {
            checks[i] = new CheckBox(this);
            checks[i].setText(blockNames[i]);
            checks[i].setTextColor(fg());
            checks[i].setButtonTintList(
                ColorStateList.valueOf(accent())
            );
            checks[i].setChecked(blockVisible(blockKeys[i]));
            box.addView(checks[i]);
        }

        AlertDialog homeCustomizationDialog = new AlertDialog.Builder(this)
            .setTitle("Блоки главного экрана")
            .setView(box)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить", (d, w) -> {
                SharedPreferences.Editor e = prefs.edit();

                for (int i = 0; i < blockKeys.length; i++) {
                    e.putBoolean(
                        "home_block_" + blockKeys[i],
                        checks[i].isChecked()
                    );
                }

                e.apply();

                renderPage();

                showInline("Главный экран обновлён");
            })
            .create();

        showThemedDialog(homeCustomizationDialog);
    }

    private long safeAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) {
            return Long.MAX_VALUE;
        }

        if (b < 0 && a < Long.MIN_VALUE - b) {
            return Long.MIN_VALUE;
        }

        return a + b;
    }

    private long safeMultiply(long a, long b) {
        if (a <= 0 || b <= 0) return 0;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
        return a * b;
    }

    private String formatTime(long sec) {
        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            sec / 3600,
            (sec % 3600) / 60,
            sec % 60
        );
    }

    private void setOverlayEnabled(boolean enabled) {
        if (enabled) {
            if (Build.VERSION.SDK_INT >= 23 &&
                !Settings.canDrawOverlays(this)) {

                infoDialog(
                    "Разрешение",
                    "Разреши отображение поверх других окон, " +
                    "затем включи панель снова."
                );

                Intent i = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName())
                );

                startActivity(i);
                return;
            }

            try {
                startService(
                    new Intent(this, FloatingPanelService.class)
                );

                prefs.edit()
                    .putBoolean("panel_enabled", true)
                    .apply();

            } catch (Exception e) {
                infoDialog(
                    "Панель не включена",
                    "Не удалось включить плавающую панель."
                );
            }

        } else {
            stopService(
                new Intent(this, FloatingPanelService.class)
            );

            prefs.edit()
                .putBoolean("panel_enabled", false)
                .apply();
        }

        renderPage();
    }

    private void toggleOverlay() {
        boolean enabled = prefs.getBoolean("panel_enabled", false);
        setOverlayEnabled(!enabled);
    }

    @Override
    protected void onResume() {
        super.onResume();

        syncData();
        renderPage();

        if (running) {
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);

        if (prefs != null && listener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(listener);
        }

        super.onDestroy();
    }
                        }
