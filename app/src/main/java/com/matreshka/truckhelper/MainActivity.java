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
    private LinearLayout bottomNav;
    private LinearLayout header;
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
        "rounds", "payout", "timer"
    };

    private final String[] blockNames = {
        "Количество кругов", "Выплата за круг", "Таймер рейса"
    };

    private final int[] accents = {
        0xFF8B4DEB, 0xFF6366F1, 0xFF06B6D4, 0xFFEF4444,
        0xFF22C55E, 0xFFEAB308, 0xFF3B82F6, 0xFF14B8A6,
        0xFFEC4899, 0xFFF97316
    };

    private final int[] backgrounds = {
        0xFF0E0B17, 0xFF0F172A, 0xFF060709, 0xFF160A0F,
        0xFF08140D, 0xFF14110A, 0xFF081326, 0xFF06202A,
        0xFF1B0A15, 0xFF1B0E06
    };

    private final int[] cards = {
        0xFF1A1428, 0xFF1E293B, 0xFF111214, 0xFF24121A,
        0xFF0F2318, 0xFF241F0F, 0xFF11213D, 0xFF0D2F3C,
        0xFF2B1023, 0xFF2B180C
    };

    private final int[] foregrounds = {
        Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE,
        Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE,
        Color.WHITE, Color.WHITE
    };

    private final int[] mutedColors = {
        0xFF9A8FB5, 0xFF94A3B8, 0xFF7A7D85, 0xFFB08A96,
        0xFF8FB5A0, 0xFFB5A78F, 0xFF8FA8D0, 0xFF8FC4D0,
        0xFFD08FB5, 0xFFD0A58F
    };

    private int theme() { return Math.max(0, Math.min(9, prefs.getInt("theme", 0))); }
    private int accent() { return accents[theme()]; }
    private int background() { return backgrounds[theme()]; }
    private int cardColor() { return cards[theme()]; }
    private int fg() { return foregrounds[theme()]; }
    private int muted() { return mutedColors[theme()]; }

    private int pay() { return Math.max(1, prefs.getInt("pay", 15000)); }
    private long goal() { return Math.max(1L, prefs.getLong("goal", 5000000L)); }
    private long read(String key) { return prefs.getLong(key, 0); }

    private String money(long n) {
        if (prefs.getBoolean("currency_compact", false)) return compactMoney(n);
        return NumberFormat.getIntegerInstance(new Locale("ru", "RU")).format(n);
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

    private GradientDrawable outline(int color, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1.2f), strokeColor);
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
        if (window != null) window.setBackgroundDrawable(shape(cardColor(), 18));
        int titleId = getResources().getIdentifier("alertTitle", "id", "android");
        if (titleId > 0) {
            TextView title = (TextView)dialog.findViewById(titleId);
            if (title != null) title.setTextColor(fg());
        }
        int messageId = getResources().getIdentifier("message", "id", "android");
        if (messageId > 0) {
            TextView message = (TextView)dialog.findViewById(messageId);
            if (message != null) message.setTextColor(fg());
        }
        int b1 = getResources().getIdentifier("button1", "id", "android");
        int b2 = getResources().getIdentifier("button2", "id", "android");
        int b3 = getResources().getIdentifier("button3", "id", "android");
        int[] ids = { b1, b2, b3 };
        for (int id : ids) {
            if (id <= 0) continue;
            Button dialogButton = (Button)dialog.findViewById(id);
            if (dialogButton != null) dialogButton.setTextColor(accent());
        }
    }

    private void infoDialog(String title, String message) {
        AlertDialog d = new AlertDialog.Builder(this)
            .setTitle(title).setMessage(message)
            .setPositiveButton("ОК", null).create();
        showThemedDialog(d);
    }

    private void showInline(String message) {
        if (inlineMessage == null) { infoDialog("TruckLog", message); return; }
        inlineMessage.setText(message);
        inlineMessage.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> {
            if (inlineMessage != null) inlineMessage.setVisibility(View.GONE);
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

    private boolean isShiftStarted() {
        if (running) return true;
        if (prefs.getLong("shiftStartedAt", 0L) > 0L) return true;
        if (prefs.getLong("shiftTrips", 0L) > 0L) return true;
        if (prefs.getLong("shiftEarned", 0L) > 0L) return true;
        return false;
    }

    private String formatDateTime(long timestamp) {
        if (timestamp <= 0) return "—";
        return new SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "TruckLog", NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
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
            if (Build.VERSION.SDK_INT >= 26) builder = new Notification.Builder(this, CHANNEL_ID);
            else builder = new Notification.Builder(this);
            builder.setContentTitle(title).setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true).setContentIntent(pi);
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(id, builder.build());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        if (Build.VERSION.SDK_INT >= 21) {
            Window window = getWindow();
            window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }

        prefs = getSharedPreferences("truck_stats", MODE_PRIVATE);
        repository = DataRepository.getInstance(this);
        if (!repository.initialize()) {
            infoDialog("История", "Не удалось перенести историю. Старые данные сохранены.");
        }
        listener = (p, key) -> {
            if (key == null) return;
            runOnUiThread(() -> { syncData(); renderPage(); });
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
        root.setFitsSystemWindows(false);

        header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(18), dp(20), dp(10));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(1);
        labels.addView(text("TRUCKLOG", 22, fg(), true));
        labels.addView(text("Бортовой компьютер", 12, muted(), false));
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView balanceChip = text(money(balance) + " ₽", 13, accent(), true);
        balanceChip.setBackground(outline(cardColor(), 20, accent()));
        balanceChip.setPadding(dp(14), dp(8), dp(14), dp(8));
        header.addView(balanceChip);

        root.addView(header);

        inlineMessage = text("", 13, accent(), true);
        inlineMessage.setVisibility(View.GONE);
        inlineMessage.setPadding(dp(20), dp(6), dp(20), dp(6));
        root.addView(inlineMessage);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(1);
        content.setPadding(dp(20), dp(8), dp(20), dp(20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(0);
        bottomNav.setBackground(shape(cardColor(), 0));
        bottomNav.setPadding(dp(6), dp(8), dp(6), dp(8));
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setMinimumHeight(dp(60));

        addNavItem("⌂", "Главная", "home");
        addNavItem("▦", "Стата", "stats");
        addNavItem("◷", "История", "history");
        addNavItem("◎", "Цели", "calculator");
        addNavItem("⚙", "Ещё", "settings");

        root.addView(bottomNav, new LinearLayout.LayoutParams(-1, -2));

        final LinearLayout headerRef = header;
        final LinearLayout navRef = bottomNav;
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= 20) {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            } else {
                top = 0;
                bottom = 0;
            }
            headerRef.setPadding(dp(20), dp(18) + top, dp(20), dp(10));
            ViewGroup.LayoutParams rawLp = navRef.getLayoutParams();
            if (rawLp instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams navLp = (LinearLayout.LayoutParams) rawLp;
                navLp.bottomMargin = bottom;
                navRef.setLayoutParams(navLp);
            }
            return insets;
        });

        frame.addView(root);
        setContentView(frame);

        if (Build.VERSION.SDK_INT >= 20) {
            root.requestApplyInsets();
        }
    }

    private void addNavItem(String icon, String title, String target) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(1);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(4), dp(6), dp(4), dp(6));

        boolean active = target.equals(page);

        TextView iconView = text(icon, 19, active ? accent() : muted(), true);
        iconView.setGravity(Gravity.CENTER);
        item.addView(iconView);

        TextView titleView = text(title, 9, active ? accent() : muted(), true);
        titleView.setGravity(Gravity.CENTER);
        item.addView(titleView);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        bottomNav.addView(item, lp);

        item.setOnClickListener(v -> {
            page = target;
            rebuildBottomNav();
            renderPage();
        });
    }

    private void rebuildBottomNav() {
        if (bottomNav == null) return;
        bottomNav.removeAllViews();
        addNavItem("⌂", "Главная", "home");
        addNavItem("▦", "Стата", "stats");
        addNavItem("◷", "История", "history");
        addNavItem("◎", "Цели", "calculator");
        addNavItem("⚙", "Ещё", "settings");
    }

    private void rebuildBase() {
        buildBase();
        renderPage();
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
        else showHome();
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(1);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        c.setBackground(shape(cardColor(), 20));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(12);
        content.addView(c, p);
        return c;
    }

    private void heading(String title, String subtitle) {
        content.addView(text(title, 24, fg(), true));
        TextView sub = text(subtitle, 13, muted(), false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(4);
        content.addView(sub, p);
    }

    private boolean blockVisible(String key) {
        return prefs.getBoolean("home_block_" + key, true);
    }

    private void showHome() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(1);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        hero.setBackground(shape(cardColor(), 24));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2);
        heroParams.topMargin = dp(8);
        content.addView(hero, heroParams);

        hero.addView(text("ТЕКУЩИЙ БАЛАНС", 12, muted(), true));
        TextView balanceView = text(money(balance) + " ₽", 40, fg(), true);
        LinearLayout.LayoutParams bv = new LinearLayout.LayoutParams(-1, -2);
        bv.topMargin = dp(6);
        hero.addView(balanceView, bv);

        long remain = Math.max(0L, goal() - balance);
        hero.addView(text("До цели: " + money(remain) + " ₽", 13, accent(), true));

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress((int)Math.min(100.0, (double)balance * 100.0 / (double)Math.max(1L, goal())));
        bar.setProgressTintList(ColorStateList.valueOf(accent()));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(8));
        bp.topMargin = dp(14);
        hero.addView(bar, bp);

        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(0);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(12);
        content.addView(tiles, tp);

        if (blockVisible("rounds")) {
            LinearLayout t1 = miniTile("КРУГИ", String.valueOf(trips), accent());
            LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, -2, 1);
            lp1.rightMargin = dp(6);
            tiles.addView(t1, lp1);
        }
        if (blockVisible("payout")) {
            LinearLayout t2 = miniTile("ВЫПЛАТА", money(pay()) + " ₽", fg());
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, -2, 1);
            lp2.leftMargin = dp(6);
            tiles.addView(t2, lp2);
        }

        if (blockVisible("timer")) {
            LinearLayout timerCard = card();
            timerCard.addView(text("ТЕКУЩИЙ РЕЙС", 12, muted(), true));
            timerView = text(running ? formatTime((System.currentTimeMillis() - startAt) / 1000) : "00:00:00", 34, fg(), true);
            timerCard.addView(timerView);
            timerCard.addView(text(
                running ? "Рейс идёт" : (isShiftStarted() ? "Смена активна" : "Смена не начата"),
                12, muted(), false
            ));
        } else {
            timerView = null;
        }

        Button main;
        if (!running && !isShiftStarted()) {
            main = button("НАЧАТЬ СМЕНУ", accent());
            main.setOnClickListener(v -> startTrip());
        } else if (running) {
            main = button("ЗАВЕРШИТЬ РЕЙС", accent());
            main.setOnClickListener(v -> finishTrip());
        } else {
            main = button("СДЕЛАЛ КРУГ  +" + money(pay()) + " ₽", accent());
            main.setOnClickListener(v -> finishTrip());
        }
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, dp(58));
        mp.topMargin = dp(14);
        content.addView(main, mp);

        Button finishShift = button(
            running ? "ЗАВЕРШИТЬ СМЕНУ (рейс отменится)" : "ЗАВЕРШИТЬ СМЕНУ",
            cardColor()
        );
        finishShift.setBackground(outline(cardColor(), 14, accent()));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(52));
        sp.topMargin = dp(8);
        content.addView(finishShift, sp);
        finishShift.setOnClickListener(v -> confirmFinishShift());

        Button resetStats = button("СБРОСИТЬ БАЛАНС И КРУГИ", cardColor());
        resetStats.setBackground(outline(cardColor(), 14, 0xFFB84040));
        resetStats.setTextColor(0xFFEF9A9A);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(48));
        rp.topMargin = dp(8);
        content.addView(resetStats, rp);
        resetStats.setOnClickListener(v -> showHomeResetDialog());

        Button overlay = button(
            prefs.getBoolean("panel_enabled", false) ? "ПАНЕЛЬ ВКЛЮЧЕНА" : "ВКЛЮЧИТЬ ПАНЕЛЬ",
            cardColor()
        );
        overlay.setBackground(outline(cardColor(), 14, accent()));
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1, dp(48));
        op.topMargin = dp(8);
        content.addView(overlay, op);
        overlay.setOnClickListener(v -> toggleOverlay());

        LinearLayout chartCard = card();
        chartCard.addView(text("ГРАФИК ЗАРАБОТКА — 7 ДНЕЙ", 12, muted(), true));
        chartCard.addView(new EarningsChart(this), new LinearLayout.LayoutParams(-1, dp(200)));
        Button openFull = button("ОТКРЫТЬ ПОЛНЫЙ ГРАФИК", cardColor());
        openFull.setBackground(outline(cardColor(), 14, accent()));
        chartCard.addView(openFull, new LinearLayout.LayoutParams(-1, dp(46)));
        openFull.setOnClickListener(v -> { page = "charts"; rebuildBottomNav(); renderPage(); });
    }

    private LinearLayout miniTile(String title, String value, int valueColor) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(1);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        t.setBackground(shape(cardColor(), 18));
        t.addView(text(title, 11, muted(), true));
        TextView v = text(value, 24, valueColor, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        t.addView(v, lp);
        return t;
    }

    private void showHomeResetDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Сбросить текущий баланс и круги?")
            .setMessage("Будут обнулены только текущий баланс, количество кругов, заработок и таймер текущей смены. История рейсов, графики и общая статистика останутся без изменений.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сбросить", (d, which) -> {
                boolean saved = resetShiftNow();
                if (saved) {
                    syncData();
                    renderPage();
                    showInline("Баланс и круги сброшены. История сохранена.");
                } else {
                    infoDialog("Сброс не выполнен", "Не удалось сохранить сброс. Попробуй ещё раз.");
                }
            })
            .create();
        showThemedDialog(dialog);
    }

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
        AlertDialog finishShiftDialog = new AlertDialog.Builder(this)
            .setTitle("Завершить смену?")
            .setMessage(
                "Заработок: " + money(earned) + " ₽\n" +
                "Кругов: " + shiftTrips + "\n" +
                "Время: " + formatTime(seconds) + "\n\n" +
                (running ? "Текущий незавершённый рейс будет отменён без выплаты.\n\n" : "") +
                "Результаты будут сохранены в историю смен."
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Завершить", (dialog, which) -> finishShift())
            .create();
        showThemedDialog(finishShiftDialog);
    }

    private void finishShift() {
        long earned = Math.max(0L, read("shiftEarned"));
        long shiftTrips = Math.max(0L, read("shiftTrips"));
        long duration = Math.max(0L, read("seconds"));

        if (running) {
            boolean stopped = prefs.edit().putBoolean("running", false).putLong("startAt", 0L).commit();
            if (!stopped) {
                infoDialog("Смена не закрыта", "Не удалось остановить текущий рейс.");
                return;
            }
            running = false;
            startAt = 0L;
            handler.removeCallbacks(ticker);
        }

        long finishTime = System.currentTimeMillis();
        long startedAt = prefs.getLong("shiftStartedAt", 0);
        if (startedAt <= 0 || startedAt > finishTime) startedAt = finishTime;

        try {
            String shiftKey = prefs.getString("pending_shift_key", null);
            if (shiftKey == null || shiftKey.trim().isEmpty()) {
                shiftKey = UUID.randomUUID().toString();
                boolean keySaved = prefs.edit().putString("pending_shift_key", shiftKey).commit();
                if (!keySaved) {
                    infoDialog("Смена не сохранена", "Не удалось подготовить сохранение смены.");
                    return;
                }
            }

            long shiftId = repository.addShift(earned, shiftTrips, duration, startedAt, finishTime, "", shiftKey);
            if (shiftId <= 0) {
                infoDialog("Смена не сохранена", "Не удалось сохранить смену.");
                return;
            }

            boolean saved = prefs.edit()
                .putLong("shiftEarned", 0).putLong("shiftTrips", 0)
                .putLong("seconds", 0).putLong("shiftStartedAt", 0)
                .putBoolean("running", false).putLong("startAt", 0)
                .putLong("last_shift_finished_at", finishTime)
                .remove("panel_undo_stack")
                .remove("panel_last_trip_id")
                .remove("panel_last_trip_amount")
                .remove("pending_shift_key")
                .commit();

            if (!saved) {
                infoDialog("Смена сохранена", "Не удалось обновить текущие показатели.");
                return;
            }

            seconds = 0; startAt = 0; running = false;
            handler.removeCallbacks(ticker);
            renderPage();

            notifyUser(NOTIF_SHIFT, "Смена завершена",
                "Заработок: " + money(earned) + " ₽, кругов: " + shiftTrips);

            infoDialog("Смена завершена!",
                "Заработок: " + money(earned) + " ₽\nКругов: " + shiftTrips + "\nВремя: " + formatTime(duration));
        } catch (Exception e) {
            infoDialog("Смена не сохранена", "Не удалось сохранить смену.");
        }
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
        if (shiftStartedAt <= 0 && shiftEarned == 0 && shiftTrips == 0) {
            editor.putLong("shiftStartedAt", startAt);
            editor.putLong("last_shift_started_at", startAt);
            editor.remove("last_shift_finished_at");
        }
        editor.apply();
        renderPage();

        if (prefs.getBoolean("shift_start_notifications", false)) {
            notifyUser(NOTIF_SHIFT, "Смена начата", "Таймер рейса запущен");
        }
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (timerView != null) {
                long elapsed = Math.max(0L, (System.currentTimeMillis() - startAt) / 1000);
                timerView.setText(formatTime(elapsed));
            }
            handler.postDelayed(this, 1000);
        }
    };

    private void finishTrip() {
        if (!isShiftStarted()) { showInline("Сначала начни смену"); return; }

        long elapsed;
        long amount = pay();
        long shiftEarned = read("shiftEarned");
        long shiftTrips = read("shiftTrips");

        if (running) {
            elapsed = Math.max(1L, (System.currentTimeMillis() - startAt) / 1000);
        } else {
            elapsed = 0L;
        }

        if (balance > Long.MAX_VALUE - amount || trips == Long.MAX_VALUE ||
            seconds > Long.MAX_VALUE - elapsed || shiftEarned > Long.MAX_VALUE - amount ||
            shiftTrips == Long.MAX_VALUE) {
            infoDialog("Предел счётчика", "Достигнут предел счётчика.");
            return;
        }

        long finishTime = System.currentTimeMillis();
        long shiftStartedAt = prefs.getLong("shiftStartedAt", 0);
        if (shiftStartedAt <= 0) shiftStartedAt = finishTime;

        long recordId = repository.addTrip(amount, finishTime, elapsed, "", "", "manual");
        if (recordId <= 0) {
            infoDialog("Рейс не сохранён", "Не удалось сохранить рейс.");
            return;
        }

        balance += amount;
        trips++;
        seconds += elapsed;

        SharedPreferences.Editor ed = prefs.edit()
            .putLong("balance", balance)
            .putLong("trips", trips)
            .putLong("seconds", seconds)
            .putLong("shiftEarned", shiftEarned + amount)
            .putLong("shiftTrips", shiftTrips + 1)
            .putLong("shiftStartedAt", shiftStartedAt)
            .putBoolean("running", false)
            .putLong("startAt", 0);

        if (prefs.getLong("last_shift_started_at", 0) <= 0) {
            ed.putLong("last_shift_started_at", shiftStartedAt);
        }
        ed.apply();

        running = false;
        startAt = 0;
        handler.removeCallbacks(ticker);
        renderPage();
        showInline("+" + money(amount) + " ₽ — круг засчитан");
    }

    // ---------------- СТАТИСТИКА + сброс смены (П.2) ----------------

    private void showStats() {
        heading("Статистика", "Результаты и личные рекорды");

        long today = 0, week = 0, todayTrips = 0, weekTrips = 0;
        long totalAmount = 0, totalCount = 0, totalDuration = 0, durationCount = 0;

        HashMap<String, Long> daily = new HashMap<>();
        HashMap<String, Long> dailyCounts = new HashMap<>();
        HashSet<String> allDays = new HashSet<>();
        long[] weekdayAmounts = new long[7];

        long todayStart = startOfDay(System.currentTimeMillis());
        Calendar weekStart = Calendar.getInstance();
        weekStart.setTimeInMillis(todayStart);
        weekStart.add(Calendar.DAY_OF_YEAR, -6);
        long weekStartTime = weekStart.getTimeInMillis();

        SimpleDateFormat dayKey = new SimpleDateFormat("yyyyMMdd", Locale.US);
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
                long itemDuration = durationColumn >= 0 ? cursor.getLong(durationColumn) : 0L;
                long itemDayStart = startOfDay(time);
                long earningsReset = repository.getDailyMetricReset(itemDayStart, "earnings");
                long tripsReset = repository.getDailyMetricReset(itemDayStart, "trips");
                boolean includeEarnings = earningsReset <= 0 || time >= earningsReset;
                boolean includeTrip = tripsReset <= 0 || time >= tripsReset;

                Calendar itemDay = Calendar.getInstance();
                itemDay.setTimeInMillis(time);
                String key = dayKey.format(itemDay.getTime());
                allDays.add(key);

                if (includeEarnings) {
                    totalAmount = safeAdd(totalAmount, amount);
                    long value = safeAdd(daily.containsKey(key) ? daily.get(key) : 0L, amount);
                    daily.put(key, value);
                    int wd = itemDay.get(Calendar.DAY_OF_WEEK) - 1;
                    weekdayAmounts[wd] = safeAdd(weekdayAmounts[wd], amount);
                    if (time >= todayStart) today = safeAdd(today, amount);
                    if (time >= weekStartTime) week = safeAdd(week, amount);
                }
                if (includeTrip) {
                    totalCount++;
                    long count = safeAdd(dailyCounts.containsKey(key) ? dailyCounts.get(key) : 0L, 1L);
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

        LinearLayout tileRow1 = new LinearLayout(this);
        tileRow1.setOrientation(0);
        LinearLayout.LayoutParams trp = new LinearLayout.LayoutParams(-1, -2);
        trp.topMargin = dp(12);
        content.addView(tileRow1, trp);

        LinearLayout t1 = miniTile("СЕГОДНЯ", money(today) + " ₽", accent());
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, -2, 1);
        p1.rightMargin = dp(6);
        tileRow1.addView(t1, p1);

        LinearLayout t2 = miniTile("7 ДНЕЙ", money(week) + " ₽", accent());
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, -2, 1);
        p2.leftMargin = dp(6);
        tileRow1.addView(t2, p2);

        LinearLayout all = card();
        all.addView(text("ЗА ВСЁ ВРЕМЯ", 12, muted(), true));
        all.addView(text(money(totalAmount) + " ₽", 26, fg(), true));
        all.addView(text("Кругов: " + totalCount, 13, muted(), false));
        all.addView(text("Средний за круг: " + money(totalCount > 0 ? totalAmount / totalCount : 0) + " ₽", 13, muted(), false));
        all.addView(text("Средняя длительность: " + formatTime(durationCount > 0 ? totalDuration / durationCount : 0L), 13, muted(), false));

        int bestWeekday = -1;
        long bestWeekdayAmount = 0;
        for (int i = 0; i < 7; i++) {
            if (weekdayAmounts[i] > bestWeekdayAmount) {
                bestWeekdayAmount = weekdayAmounts[i];
                bestWeekday = i;
            }
        }
        if (bestWeekday >= 0 && bestWeekdayAmount > 0) {
            String[] weekdays = { "Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб" };
            all.addView(text("Лучший день недели: " + weekdays[bestWeekday] + " (" + money(bestWeekdayAmount) + " ₽)", 13, accent(), true));
        }
        all.addView(text("Серия дней: " + calculateStreak(allDays), 13, muted(), false));

        LinearLayout records = card();
        records.addView(text("ЛИЧНЫЕ РЕКОРДЫ", 14, fg(), true));
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
                    } catch (Exception ignored) { bestDayTime = 0L; }
                }
            }
        }
        records.addView(text("Самый прибыльный день: " + money(bestDay) + " ₽", 14, accent(), true));
        if (bestDayTime > 0) {
            records.addView(text(new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date(bestDayTime)), 12, muted(), false));
        }
        Button resetBest = button("СБРОСИТЬ ЛУЧШИЙ ДЕНЬ", cardColor());
        resetBest.setBackground(outline(cardColor(), 14, 0xFFB84040));
        resetBest.setTextColor(0xFFEF9A9A);
        records.addView(resetBest, new LinearLayout.LayoutParams(-1, dp(44)));
        resetBest.setOnClickListener(v -> {
            prefs.edit().putLong("best_day_amount", 0L).putLong("best_day_time", 0L).apply();
            renderPage();
            showInline("Лучший день сброшен");
        });

        // П.2 — СБРОС СМЕНЫ ИЗ СТАТИСТИКИ
        LinearLayout shiftCard = card();
        shiftCard.addView(text("УПРАВЛЕНИЕ СМЕНОЙ", 14, fg(), true));
        shiftCard.addView(text("Обнуляет текущий баланс, круги и таймер. История и графики не изменятся.", 12, muted(), false));
        Button resetShiftBtn = button("СБРОСИТЬ ТЕКУЩУЮ СМЕНУ", cardColor());
        resetShiftBtn.setBackground(outline(cardColor(), 14, 0xFFB84040));
        resetShiftBtn.setTextColor(0xFFEF9A9A);
        LinearLayout.LayoutParams rsb = new LinearLayout.LayoutParams(-1, dp(46));
        rsb.topMargin = dp(8);
        shiftCard.addView(resetShiftBtn, rsb);
        resetShiftBtn.setOnClickListener(v -> showHomeResetDialog());

        long shiftCount = 0, bestShift = 0;
        Cursor shifts = null;
        try {
            shifts = repository.getShiftsHistory();
            if (shifts != null) {
                int earnedColumn = shifts.getColumnIndex("earned");
                while (shifts.moveToNext()) {
                    shiftCount++;
                    if (earnedColumn >= 0) bestShift = Math.max(bestShift, shifts.getLong(earnedColumn));
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (shifts != null) shifts.close();
        }

        LinearLayout daysCard = card();
        daysCard.addView(text("ПО ДНЯМ", 14, fg(), true));
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        ArrayList<String> sortedDays = new ArrayList<>(allDays);
        Collections.sort(sortedDays, Collections.reverseOrder());
        for (String key : sortedDays) {
            try {
                Date parsedDay = dayKey.parse(key);
                if (parsedDay == null) continue;
                long dayStart = startOfDay(parsedDay.getTime());
                long amount = daily.containsKey(key) ? daily.get(key) : 0L;
                long count = dailyCounts.containsKey(key) ? dailyCounts.get(key) : 0L;

                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setOrientation(LinearLayout.HORIZONTAL);

                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                info.addView(text(dateFormat.format(parsedDay), 13, fg(), true));
                info.addView(text(money(amount) + " ₽  •  " + count + " кругов", 12, muted(), false));
                row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

                Button resetDay = button("СБРОС", cardColor());
                resetDay.setBackground(outline(cardColor(), 10, 0xFFB84040));
                resetDay.setTextColor(0xFFEF9A9A);
                row.addView(resetDay, new LinearLayout.LayoutParams(dp(82), dp(40)));
                resetDay.setOnClickListener(v -> showDailyResetDialog(dayStart));

                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                rowParams.topMargin = dp(10);
                daysCard.addView(row, rowParams);
            } catch (Exception ignored) {
            }
        }
    }

    private int calculateStreak(HashSet<String> allDays) {
        if (allDays.isEmpty()) return 0;
        SimpleDateFormat dayKey = new SimpleDateFormat("yyyyMMdd", Locale.US);
        Calendar cursor = Calendar.getInstance();
        cursor.set(Calendar.HOUR_OF_DAY, 0);
        cursor.set(Calendar.MINUTE, 0);
        cursor.set(Calendar.SECOND, 0);
        cursor.set(Calendar.MILLISECOND, 0);
        String todayKey = dayKey.format(cursor.getTime());
        if (!allDays.contains(todayKey)) {
            cursor.add(Calendar.DAY_OF_YEAR, -1);
            if (!allDays.contains(dayKey.format(cursor.getTime()))) return 0;
        }
        int streak = 0;
        while (true) {
            String key = dayKey.format(cursor.getTime());
            if (allDays.contains(key)) {
                streak++;
                cursor.add(Calendar.DAY_OF_YEAR, -1);
            } else break;
            if (streak > 3650) break;
        }
        return streak;
    }

    private void showDailyResetDialog(long dayStart) {
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(dayStart);
        String date = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(day.getTime());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), 0);

        CheckBox earnings = new CheckBox(this);
        earnings.setText("Сбросить заработок за этот день");
        earnings.setTextColor(fg());
        earnings.setButtonTintList(ColorStateList.valueOf(accent()));
        box.addView(earnings);

        CheckBox tripsCount = new CheckBox(this);
        tripsCount.setText("Сбросить количество кругов за этот день");
        tripsCount.setTextColor(fg());
        tripsCount.setButtonTintList(ColorStateList.valueOf(accent()));
        box.addView(tripsCount);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Сброс — " + date)
            .setView(box)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сбросить", (d, which) -> {
                if (!earnings.isChecked() && !tripsCount.isChecked()) {
                    showInline("Выбери хотя бы один показатель");
                    return;
                }
                long resetAt = System.currentTimeMillis();
                boolean success = true;
                if (earnings.isChecked()) success &= repository.saveDailyMetricReset(dayStart, "earnings", resetAt);
                if (tripsCount.isChecked()) success &= repository.saveDailyMetricReset(dayStart, "trips", resetAt);
                if (success) {
                    renderPage();
                    showInline("Показатели сброшены. Баланс не изменён.");
                } else {
                    infoDialog("Сброс не выполнен", "Не удалось сохранить сброс.");
                }
            })
            .create();
        showThemedDialog(dialog);
    }

    // ---------------- ИСТОРИЯ + сброс истории (П.3) ----------------

    private void showHistory() {
        heading("История", "Смена, круги и время");

        // П.3 — СБРОС ВСЕЙ ИСТОРИИ
        LinearLayout clearCard = card();
        clearCard.addView(text("УПРАВЛЕНИЕ ИСТОРИЕЙ", 14, fg(), true));
        clearCard.addView(text("Удаляет все записи кругов и смен из базы. Баланс и счётчики не изменятся.", 12, muted(), false));
        Button clearHistoryBtn = button("СТЕРЕТЬ ВСЮ ИСТОРИЮ", cardColor());
        clearHistoryBtn.setBackground(outline(cardColor(), 14, 0xFFB84040));
        clearHistoryBtn.setTextColor(0xFFEF9A9A);
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(-1, dp(46));
        chp.topMargin = dp(8);
        clearCard.addView(clearHistoryBtn, chp);
        clearHistoryBtn.setOnClickListener(v -> confirmClearHistory());

        long startedAt = prefs.getLong("last_shift_started_at", 0);
        long finishedAt = prefs.getLong("last_shift_finished_at", 0);

        if (startedAt > 0) {
            LinearLayout startCard = card();
            startCard.addView(text("Смена начата", 16, accent(), true));
            startCard.addView(text(formatDateTime(startedAt), 14, fg(), false));
        }

        Cursor cursor = null;
        HashMap<String, Long> byDayCount = new HashMap<>();
        HashMap<String, Long> byDayAmount = new HashMap<>();
        SimpleDateFormat dayKey = new SimpleDateFormat("yyyyMMdd", Locale.US);
        long todayStart = startOfDay(System.currentTimeMillis());
        long todayCount = 0, previousDaysCount = 0;

        try {
            cursor = repository.getTripsHistory();
            if (cursor != null) {
                int timeColumn = cursor.getColumnIndexOrThrow("created_at");
                int amountColumn = cursor.getColumnIndexOrThrow("amount");
                while (cursor.moveToNext()) {
                    long time = cursor.getLong(timeColumn);
                    long amount = cursor.getLong(amountColumn);
                    if (time >= todayStart) todayCount++;
                    else {
                        previousDaysCount++;
                        Calendar d = Calendar.getInstance();
                        d.setTimeInMillis(time);
                        String key = dayKey.format(d.getTime());
                        byDayCount.put(key, (byDayCount.containsKey(key) ? byDayCount.get(key) : 0L) + 1);
                        byDayAmount.put(key, (byDayAmount.containsKey(key) ? byDayAmount.get(key) : 0L) + amount);
                    }
                }
                cursor.moveToPosition(-1);
            }

            LinearLayout summary = card();
            summary.addView(text("КОЛИЧЕСТВО КРУГОВ", 12, muted(), true));
            summary.addView(text("Сегодня: " + todayCount, 20, accent(), true));

            final HashMap<String, Long> dayCounts = byDayCount;
            final HashMap<String, Long> dayAmounts = byDayAmount;

            Button openDays = button("📁  ПО ДНЯМ (" + previousDaysCount + ")", accent());
            summary.addView(openDays, new LinearLayout.LayoutParams(-1, dp(48)));
            openDays.setOnClickListener(v -> showDaysBreakdown(dayCounts, dayAmounts));

            if (cursor != null && cursor.getCount() > 0) {
                int amountColumn = cursor.getColumnIndexOrThrow("amount");
                int timeColumn = cursor.getColumnIndexOrThrow("created_at");
                int durationColumn = cursor.getColumnIndexOrThrow("duration");
                int routeColumn = cursor.getColumnIndexOrThrow("route");
                int noteColumn = cursor.getColumnIndexOrThrow("note");

                SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault());
                int number = cursor.getCount();

                while (cursor.moveToNext()) {
                    long amount = cursor.getLong(amountColumn);
                    long time = cursor.getLong(timeColumn);
                    long duration = cursor.getLong(durationColumn);
                    String route = cursor.getString(routeColumn);
                    String note = cursor.getString(noteColumn);

                    LinearLayout c = card();
                    c.addView(text("Круг", 16, accent(), true));
                    c.addView(text("Время", 11, muted(), true));
                    c.addView(text(time > 0 ? fmt.format(new Date(time)) : "—", 14, fg(), false));
                    c.addView(text("+" + money(amount) + " ₽", 20, accent(), true));
                    if (duration > 0) c.addView(text("Длительность: " + formatTime(duration), 12, muted(), false));
                    if (route != null && !route.isEmpty()) c.addView(text("Маршрут: " + route, 12, fg(), false));
                    if (note != null && !note.isEmpty()) c.addView(text("Заметка: " + note, 12, muted(), false));
                    number--;
                }
            } else {
                card().addView(text("Пока нет записей. Добавь первый круг!", 14, muted(), false));
            }
        } catch (Exception e) {
            card().addView(text("Не удалось загрузить историю", 14, muted(), false));
        } finally {
            if (cursor != null) cursor.close();
        }

        if (finishedAt > 0) {
            LinearLayout endCard = card();
            endCard.addView(text("Закончил смену", 16, 0xFFEF9A9A, true));
            endCard.addView(text(formatDateTime(finishedAt), 14, fg(), false));
        } else if (startedAt > 0) {
            LinearLayout endCard = card();
            endCard.addView(text("Смена идёт", 16, accent(), true));
            endCard.addView(text("Ещё не завершена", 13, muted(), false));
        }
    }

    private void confirmClearHistory() {
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Стереть всю историю?")
            .setMessage("Будут удалены все записи кругов и завершённые смены из базы. Текущий баланс и счётчики останутся, но журнал «Смена начата / Круг / Закончил смену» очистится. Действие необратимо.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Стереть", (d, w) -> {
                try {
                    repository.clearTripsHistory();
                    repository.clearShiftsHistory();
                    prefs.edit()
                        .remove("last_shift_started_at")
                        .remove("last_shift_finished_at")
                        .apply();
                    renderPage();
                    showInline("История полностью стерта.");
                } catch (Exception e) {
                    infoDialog("Ошибка", "Не удалось стереть историю.");
                }
            })
            .create();
        showThemedDialog(dialog);
    }

    private void showDaysBreakdown(HashMap<String, Long> counts, HashMap<String, Long> amounts) {
        if (counts.isEmpty()) {
            infoDialog("Прошлые дни", "Записей за прошлые дни нет.");
            return;
        }
        SimpleDateFormat dayKey = new SimpleDateFormat("yyyyMMdd", Locale.US);
        SimpleDateFormat display = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
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
                row.setPadding(dp(12), dp(12), dp(12), dp(12));
                row.setBackground(shape(cardColor(), 12));
                row.addView(text(display.format(d), 14, fg(), true));
                row.addView(text(money(amt) + " ₽  •  " + cnt + " кругов", 12, muted(), false));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
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
        heading("Цели", "Копи и отслеживай прогресс");

        Button add = button("+ ДОБАВИТЬ ЦЕЛЬ", accent());
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(48));
        ap.topMargin = dp(12);
        content.addView(add, ap);
        add.setOnClickListener(v -> showGoalEditor(-1));

        JSONArray goals = getGoals();
        if (goals.length() == 0) {
            card().addView(text("Пока нет целей. Добавь первую!", 14, muted(), false));
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
                    (roundsNeeded / roundsPerDay + (roundsNeeded % roundsPerDay == 0 ? 0 : 1));
                double percent = Math.min(100.0, (double)balance * 100.0 / Math.max(1L, amount));

                LinearLayout c = card();
                c.addView(text(name, 19, fg(), true));
                c.addView(text("Цель: " + money(amount) + " ₽", 13, muted(), false));
                c.addView(text("Осталось: " + money(remaining) + " ₽", 16, accent(), true));

                ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                bar.setMax(100);
                bar.setProgress((int)percent);
                bar.setProgressTintList(ColorStateList.valueOf(accent()));
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(8));
                bp.topMargin = dp(10);
                c.addView(bar, bp);

                c.addView(text("Выполнено: " + String.format(Locale.US, "%.1f", percent) + "%", 13, fg(), false));
                c.addView(text("Кругов осталось: " + roundsNeeded, 14, fg(), true));
                c.addView(text("План: " + roundsPerDay + " кругов/день", 13, muted(), false));
                c.addView(text("Ожидаемо в день: " + money(expected) + " ₽", 13, accent(), true));
                c.addView(text("Дней до цели: " + days, 13, fg(), false));

                Button editButton = button("ИЗМЕНИТЬ", cardColor());
                c.addView(editButton, new LinearLayout.LayoutParams(-1, dp(44)));
                editButton.setOnClickListener(v -> showGoalEditor(index));

                Button deleteButton = button("УДАЛИТЬ", cardColor());
                deleteButton.setBackground(outline(cardColor(), 14, 0xFFB84040));
                deleteButton.setTextColor(0xFFEF9A9A);
                c.addView(deleteButton, new LinearLayout.LayoutParams(-1, dp(44)));
                deleteButton.setOnClickListener(v -> confirmDeleteGoal(index));
            } catch (Exception ignored) {
            }
        }
    }

    private void showGoalEditor(int index) {
        JSONArray goals = getGoals();
        String oldName = "", oldAmount = "", oldRounds = "5";
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
        EditText amount = edit("Сумма цели в рублях", InputType.TYPE_CLASS_NUMBER);
        amount.setText(oldAmount);
        box.addView(amount);
        EditText rounds = edit("Кругов в день", InputType.TYPE_CLASS_NUMBER);
        rounds.setText(oldRounds);
        box.addView(rounds);

        AlertDialog goalEditorDialog = new AlertDialog.Builder(this)
            .setTitle(index < 0 ? "Новая цель" : "Изменить цель")
            .setView(box)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить", (d, w) -> {
                try {
                    String n = name.getText().toString().trim();
                    long a = Long.parseLong(amount.getText().toString().trim());
                    long r = Long.parseLong(rounds.getText().toString().trim());
                    if (n.isEmpty() || a <= 0 || r <= 0) throw new Exception();
                    JSONObject item = new JSONObject();
                    item.put("name", n);
                    item.put("amount", a);
                    item.put("rounds", r);
                    if (index >= 0 && index < goals.length()) goals.put(index, item);
                    else goals.put(item);
                    saveGoals(goals);
                    renderPage();
                } catch (Exception e) {
                    infoDialog("Цель не сохранена", "Проверь название, сумму и количество кругов.");
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
                for (int i = 0; i < old.length(); i++) if (i != index) updated.put(old.opt(i));
                saveGoals(updated);
                renderPage();
            })
            .create();
        showThemedDialog(deleteGoalDialog);
    }

    private void showCharts() {
        heading("Графики", "Заработок за 7 дней");
        LinearLayout c = card();
        c.addView(new EarningsChart(this), new LinearLayout.LayoutParams(-1, dp(300)));
        LinearLayout info = card();
        info.addView(text("Источник: сохранённая история рейсов.", 13, muted(), false));
        Button back = button("НАЗАД НА ГЛАВНУЮ", cardColor());
        back.setBackground(outline(cardColor(), 14, accent()));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(46));
        bp.topMargin = dp(12);
        content.addView(back, bp);
        back.setOnClickListener(v -> { page = "home"; rebuildBottomNav(); renderPage(); });
    }

    private class EarningsChart extends View {
        Paint paint = new Paint(3);
        EarningsChart(Context c) { super(c); }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
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
                    int timeColumn = cursor.getColumnIndexOrThrow("created_at");
                    int amountColumn = cursor.getColumnIndexOrThrow("amount");
                    while (cursor.moveToNext()) {
                        long time = cursor.getLong(timeColumn);
                        long amount = cursor.getLong(amountColumn);
                        for (int i = 0; i < 7; i++) {
                            Calendar next = (Calendar)days[i].clone();
                            next.add(Calendar.DAY_OF_YEAR, 1);
                            if (time >= days[i].getTimeInMillis() && time < next.getTimeInMillis()) {
                                long resetAt = repository.getDailyMetricReset(days[i].getTimeInMillis(), "earnings");
                                if (resetAt <= 0 || time >= resetAt) values[i] = safeAdd(values[i], amount);
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
            for (long value : values) max = Math.max(max, value);
            float leftPad = dp(8), gap = dp(7);
            float barW = (w - dp(16) - gap * 6) / 7f;
            float base = h - dp(48), maxHeight = h - dp(105);
            paint.setColor(muted());
            paint.setStrokeWidth(dp(1));
            canvas.drawLine(dp(5), base, w - dp(5), base, paint);
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM", Locale.getDefault());
            SimpleDateFormat dayFormat = new SimpleDateFormat("EE", Locale.getDefault());
            for (int i = 0; i < 7; i++) {
                float left = leftPad + i * (barW + gap);
                float bh = (float)(((double)values[i] / (double)max) * maxHeight);
                paint.setColor(accent());
                canvas.drawRoundRect(left, base - bh, left + barW, base, dp(5), dp(5), paint);
                paint.setColor(fg());
                paint.setTextSize(dp(9));
                paint.setTextAlign(Paint.Align.CENTER);
                String amount = values[i] > 0 ? compactMoney(values[i]) : "0";
                canvas.drawText(amount, left + barW / 2, Math.max(dp(12), base - bh - dp(5)), paint);
                paint.setTextSize(dp(9));
                canvas.drawText(dayFormat.format(days[i].getTime()), left + barW / 2, h - dp(27), paint);
                canvas.drawText(dateFormat.format(days[i].getTime()), left + barW / 2, h - dp(10), paint);
            }
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private String compactMoney(long value) {
        if (value >= 1000000) return String.format(Locale.US, "%.1fм", value / 1000000.0);
        if (value >= 1000) return String.format(Locale.US, "%.0fк", value / 1000.0);
        return String.valueOf(value);
    }

    private void showThemes() {
        heading("Оформление", "Цветовая тема приложения");

        String[] names = {
            "Неон", "Индиго", "Циан", "Красная",
            "Зелёная", "Золото", "Синяя", "Бирюза",
            "Розовая", "Оранж"
        };

        for (int i = 0; i < names.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(shape(cardColor(), 16));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(58));
            rp.topMargin = dp(8);
            content.addView(row, rp);

            View swatch = new View(this);
            swatch.setBackground(shape(accents[i], 30));
            row.addView(swatch, new LinearLayout.LayoutParams(dp(32), dp(32)));

            TextView name = text(names[i], 15, fg(), true);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1);
            np.leftMargin = dp(14);
            row.addView(name, np);

            TextView selected = text(prefs.getInt("theme", 0) == i ? "✓" : "›", 22, accents[i], true);
            row.addView(selected);

            row.setOnClickListener(v -> {
                prefs.edit().putInt("theme", index).apply();
                rebuildBase();
            });
        }
    }

    private void showSettings() {
        heading("Ещё", "Настройки и оформление");

        LinearLayout themeCard = card();
        themeCard.addView(text("ОФОРМЛЕНИЕ", 12, muted(), true));
        themeCard.addView(text("Выбери цветовую тему приложения", 13, muted(), false));
        Button openThemes = button("ВЫБРАТЬ ТЕМУ", accent());
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, dp(48));
        tp.topMargin = dp(8);
        themeCard.addView(openThemes, tp);
        openThemes.setOnClickListener(v -> { page = "theme"; rebuildBottomNav(); renderPage(); });

        LinearLayout sound = card();
        sound.addView(text("ЗВУК И ВИБРАЦИЯ", 12, muted(), true));
        addSwitch(sound, "Звуки", "sound_enabled", true);
        addSwitch(sound, "Вибрация", "vibration_enabled", true);
        addSwitch(sound, "Тихий режим", "silent_mode", false);
        addSeekBar(sound, "Громкость", "sound_volume", 0, 100, 70);

        LinearLayout panel = card();
        panel.addView(text("ПЛАВАЮЩАЯ ПАНЕЛЬ", 12, muted(), true));
        addSeekBar(panel, "Размер", "panel_size", 70, 130, 100);
        addSeekBar(panel, "Прозрачность", "panel_opacity", 30, 100, 100);
        addSwitch(panel, "Показывать панель", "panel_enabled", false);

        LinearLayout currency = card();
        currency.addView(text("ФОРМАТ", 12, muted(), true));
        addSwitch(currency, "Компактные суммы (к, м)", "currency_compact", false);

        LinearLayout notif = card();
        notif.addView(text("УВЕДОМЛЕНИЯ", 12, muted(), true));
        addSwitch(notif, "Показывать", "notifications_enabled", true);
        addSwitch(notif, "О завершении рейса", "trip_finish_notifications", true);
        addSwitch(notif, "О начале смены", "shift_start_notifications", false);

        LinearLayout home = card();
        home.addView(text("ГЛАВНЫЙ ЭКРАН", 12, muted(), true));
        Button customize = button("НАСТРОИТЬ БЛОКИ", accent());
        home.addView(customize, new LinearLayout.LayoutParams(-1, dp(48)));
        customize.setOnClickListener(v -> showHomeCustomization());

        LinearLayout backup = card();
        backup.addView(text("ДАННЫЕ", 12, muted(), true));
        Button exportButton = button("ЭКСПОРТ", cardColor());
        backup.addView(exportButton, new LinearLayout.LayoutParams(-1, dp(46)));
        exportButton.setOnClickListener(v -> exportData());

        LinearLayout reset = card();
        reset.addView(text("СБРОС", 12, muted(), true));
        Button resetButton = button("СБРОСИТЬ НАСТРОЙКИ", cardColor());
        resetButton.setBackground(outline(cardColor(), 14, 0xFFB84040));
        resetButton.setTextColor(0xFFEF9A9A);
        reset.addView(resetButton, new LinearLayout.LayoutParams(-1, dp(46)));
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

            android.content.ClipboardManager cm = (android.content.ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("TruckLog Export", data.toString(2)));
            infoDialog("Экспорт готов", "Копия статистики скопирована в буфер обмена.");
        } catch (Exception e) {
            infoDialog("Экспорт не выполнен", "Не удалось сформировать копию данных.");
        }
    }

    private void addSwitch(LinearLayout parent, String title, String key, boolean defaultValue) {
        Switch sw = new Switch(this);
        sw.setText(title);
        sw.setTextColor(fg());
        sw.setTextSize(14);
        sw.setChecked(prefs.getBoolean(key, defaultValue));
        sw.setOnCheckedChangeListener((button, checked) -> {
            if ("panel_enabled".equals(key)) setOverlayEnabled(checked);
            else prefs.edit().putBoolean(key, checked).apply();
        });
        parent.addView(sw, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void addSeekBar(LinearLayout parent, String title, String key, int min, int max, int defaultValue) {
        TextView label = text(title + ": " + prefs.getInt(key, defaultValue), 13, fg(), true);
        parent.addView(label);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(Math.max(0, Math.min(max - min, prefs.getInt(key, defaultValue) - min)));
        seek.setProgressTintList(ColorStateList.valueOf(accent()));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int value = min + progress;
                label.setText(title + ": " + value);
                if (fromUser) prefs.edit().putInt(key, value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        parent.addView(seek);
    }

    // ---------------- СБРОС НАСТРОЕК (ИСПРАВЛЕНО) ----------------

    private void confirmSettingsReset() {
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Сбросить настройки?")
            .setMessage("Тема, настройки панели, звука, вибрации, уведомлений и главного экрана вернутся к стандартным. Статистика и история останутся.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сбросить", (d, w) -> {
                // Пытаемся остановить сервис, но не падаем, если не получилось
                try {
                    stopService(new Intent(this, FloatingPanelService.class));
                } catch (Exception ignored) {}

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

                // ВАЖНО: commit(), а не apply() — чтобы изменения записались сразу
                boolean saved = e.commit();

                if (!saved) {
                    infoDialog("Сброс не выполнен", "Не удалось сохранить настройки. Попробуй ещё раз.");
                    return;
                }

                // Принудительно перестраиваем интерфейс с темой по умолчанию
                rebuildBase();
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
            checks[i].setButtonTintList(ColorStateList.valueOf(accent()));
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
                    e.putBoolean("home_block_" + blockKeys[i], checks[i].isChecked());
                }
                e.apply();
                renderPage();
                showInline("Главный экран обновлён");
            })
            .create();
        showThemedDialog(homeCustomizationDialog);
    }

    private long safeAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        if (b < 0 && a < Long.MIN_VALUE - b) return Long.MIN_VALUE;
        return a + b;
    }

    private long safeMultiply(long a, long b) {
        if (a <= 0 || b <= 0) return 0;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
        return a * b;
    }

    private String formatTime(long sec) {
        return String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
    }

    private void setOverlayEnabled(boolean enabled) {
        if (enabled) {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                infoDialog("Разрешение", "Разреши отображение поверх других окон, затем включи панель снова.");
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName()));
                startActivity(i);
                return;
            }
            try {
                startService(new Intent(this, FloatingPanelService.class));
                prefs.edit().putBoolean("panel_enabled", true).apply();
            } catch (Exception e) {
                infoDialog("Панель не включена", "Не удалось включить плавающую панель.");
            }
        } else {
            stopService(new Intent(this, FloatingPanelService.class));
            prefs.edit().putBoolean("panel_enabled", false).apply();
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
