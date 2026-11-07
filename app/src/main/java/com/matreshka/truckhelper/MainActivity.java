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
import android.media.AudioManager;
import android.media.ToneGenerator;

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
    private long selectedStatsDay = 0L;

    private TextView timerView;

    private long balance, trips, seconds, startAt;
    private boolean running;

    private Handler handler = new Handler();

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
        0xFF211735, 0xFFFFFFFF, 0xFF1B1B1B, 0xFF2A151C,
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

    private void playUiFeedback(boolean success) {
        if (prefs == null) return;

        if (prefs.getBoolean("sound_enabled", true)) {
            int volume = Math.max(0, Math.min(100,
                prefs.getInt("sound_volume", 70)));
            if (volume > 0) {
                try {
                    ToneGenerator generator = new ToneGenerator(
                        AudioManager.STREAM_NOTIFICATION, volume
                    );
                    generator.startTone(
                        success ? ToneGenerator.TONE_PROP_ACK : ToneGenerator.TONE_PROP_NACK,
                        success ? 85 : 140
                    );
                    handler.postDelayed(() -> {
                        try { generator.release(); } catch (Exception ignored) {}
                    }, 240);
                } catch (Exception ignored) {}
            }
        }

        if (prefs.getBoolean("vibration_enabled", true)) {
            try {
                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= 26) {
                        vibrator.vibrate(VibrationEffect.createOneShot(
                            success ? 30 : 70,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        ));
                    } else {
                        vibrator.vibrate(success ? 30 : 70);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private Button button(String s, int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(fg());
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTypeface(null, Typeface.BOLD);
        b.setBackground(shape(color, 14));
        b.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && view.isEnabled()) {
                playUiFeedback(true);
            }
            return false;
        });
        return b;
    }

    private EditText edit(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setInputType(inputType);
        e.setHint(hint);
        e.setTextColor(fg());
        e.setHintTextColor(muted());
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackground(shape(cardColor(), 12));
        return e;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        prefs = getSharedPreferences("truck_stats", MODE_PRIVATE);
        repository = DataRepository.getInstance(this);

        if (!repository.initialize()) {
            Toast.makeText(
                this,
                "Не удалось перенести историю. Старые данные сохранены.",
                Toast.LENGTH_LONG
            ).show();
        }

        listener = (p, key) -> {
            if (key == null) return;
            runOnUiThread(() -> {
                syncData();
                if ("theme".equals(key) && root != null) buildBase();
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
        menuParams.topMargin = dp(14);
        header.addView(menu, menuParams);

        menu.setOnClickListener(v -> toggleDrawer());

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(1);
        labels.setPadding(dp(12), 0, 0, 0);
        labels.addView(text("MATRESHKA", 11, muted(), true));
        labels.addView(text("TRUCK HELPER", 20, fg(), true));

        header.addView(labels);
        root.addView(header);

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
        closeParams.bottomMargin = dp(16);
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

        Button finish = button(
            "ЗАВЕРШИТЬ РЕЙС  +" + money(pay()) + " ₽",
            cardColor()
        );

        LinearLayout.LayoutParams p2 =
            new LinearLayout.LayoutParams(-1, dp(52));
        p2.topMargin = dp(8);
        content.addView(finish, p2);

        finish.setEnabled(running);
        finish.setOnClickListener(v -> finishTrip());

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

    private void confirmFinishShift() {
        if (running) {
            Toast.makeText(
                this,
                "Сначала заверши текущий рейс",
                Toast.LENGTH_SHORT
            ).show();
            return;
        }

        long earned = Math.max(0L, read("shiftEarned"));
        long shiftTrips = Math.max(0L, read("shiftTrips"));

        if (earned <= 0 && shiftTrips <= 0) {
            Toast.makeText(
                this,
                "В текущей смене пока нет кругов",
                Toast.LENGTH_SHORT
            ).show();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Завершить смену?")
            .setMessage(
                "Заработок: " + money(earned) + " ₽\n" +
                "Кругов: " + shiftTrips + "\n" +
                "Время: " + formatTime(seconds) + "\n\n" +
                "Результаты будут сохранены в историю смен. " +
                "Общий баланс и история кругов останутся без изменений."
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Завершить", (dialog, which) ->
                finishShift()
            )
            .show();
    }

    private void finishShift() {
        if (running) return;

        long earned = Math.max(0L, read("shiftEarned"));
        long shiftTrips = Math.max(0L, read("shiftTrips"));
        long duration = Math.max(0L, read("seconds"));

        if (earned <= 0 && shiftTrips <= 0) {
            Toast.makeText(
                this,
                "В текущей смене пока нет кругов",
                Toast.LENGTH_SHORT
            ).show();
            return;
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
                    Toast.makeText(
                        this,
                        "Не удалось подготовить сохранение смены. Повтори попытку.",
                        Toast.LENGTH_LONG
                    ).show();
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
                Toast.makeText(
                    this,
                    "Не удалось сохранить смену. Текущие показатели не сброшены.",
                    Toast.LENGTH_LONG
                ).show();
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
                Toast.makeText(
                    this,
                    "Смена сохранена. Не удалось обновить текущие показатели. Повтори завершение — повторная запись не появится.",
                    Toast.LENGTH_LONG
                ).show();
                return;
            }

            seconds = 0;
            startAt = 0;
            running = false;

            handler.removeCallbacks(ticker);
            renderPage();

            new AlertDialog.Builder(this)
                .setTitle("Смена завершена!")
                .setMessage(
                    "Смена сохранена в историю.\n\n" +
                    "Заработок: " + money(earned) + " ₽\n" +
                    "Кругов: " + shiftTrips + "\n" +
                    "Время: " + formatTime(duration) + "\n\n" +
                    "Можешь начинать следующую смену."
                )
                .setPositiveButton("Отлично", null)
                .show();

        } catch (Exception e) {
            Toast.makeText(
                this,
                "Не удалось сохранить смену. Текущие показатели не сброшены.",
                Toast.LENGTH_LONG
            ).show();
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

    private void finishTrip() {
        if (!running) return;

        long elapsed = Math.max(
            1L,
            (System.currentTimeMillis() - startAt) / 1000
        );

        long amount = pay();
        long shiftEarned = read("shiftEarned");
        long shiftTrips = read("shiftTrips");

        if (balance > Long.MAX_VALUE - amount ||
            trips == Long.MAX_VALUE ||
            seconds > Long.MAX_VALUE - elapsed ||
            shiftEarned > Long.MAX_VALUE - amount ||
            shiftTrips == Long.MAX_VALUE) {
            Toast.makeText(
                this, "Достигнут предел счётчика",
                Toast.LENGTH_LONG
            ).show();
            return;
        }

        long finishTime = System.currentTimeMillis();
        long shiftStartedAt = prefs.getLong("shiftStartedAt", 0);

        if (shiftStartedAt <= 0) shiftStartedAt = startAt;

        long recordId = repository.addTrip(
            amount, finishTime, elapsed, "", "", "manual"
        );

        if (recordId <= 0) {
            Toast.makeText(
                this,
                "Не удалось сохранить рейс. Данные не изменены.",
                Toast.LENGTH_LONG
            ).show();
            return;
        }

        seconds += elapsed;
        balance += amount;
        trips += 1;
        shiftEarned += amount;
        shiftTrips += 1;

        prefs.edit()
            .putLong("balance", balance)
            .putLong("trips", trips)
            .putLong("seconds", seconds)
            .putLong("shiftEarned", shiftEarned)
            .putLong("shiftTrips", shiftTrips)
            .putLong("shiftStartedAt", shiftStartedAt)
            .putBoolean("running", false)
            .putLong("startAt", 0)
            .apply();

        running = false;
        startAt = 0;

        handler.removeCallbacks(ticker);
        renderPage();

        new AlertDialog.Builder(this)
            .setTitle("Рейс завершён")
            .setMessage(
                "+" + money(amount) + " ₽\nВремя: " +
                formatTime(elapsed)
            )
            .setPositiveButton("Отлично", null)
            .show();
    }
