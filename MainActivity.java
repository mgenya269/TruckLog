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
        header.addView(menu, new LinearLayout.LayoutParams(dp(62), dp(46)));
        menu.setOnClickListener(v -> toggleDrawer());

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(1);
        labels.setPadding(dp(12), 0, 0, 0);
        labels.addView(text("TruckLog", 11, muted(), true));
        labels.addView(text("TruckLog", 20, fg(), true));

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
        addDrawerItem("🎨", "Оформление", "theme");
        addDrawerItem("⚙", "Настройки", "settings");

        View space = new View(this);
        drawer.addView(
            space,
            new LinearLayout.LayoutParams(1, 0, 1)
        );

        Button close = button("ЗАКРЫТЬ МЕНЮ", accent());
        drawer.addView(
            close,
            new LinearLayout.LayoutParams(-1, dp(46))
        );
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

        Button finishShift = button(
            running ? "СНАЧАЛА ЗАВЕРШИ ТЕКУЩИЙ РЕЙС"
                    : "ЗАВЕРШИТЬ СМЕНУ",
            accent()
        );

        LinearLayout.LayoutParams shiftParams =
            new LinearLayout.LayoutParams(-1, dp(52));
        shiftParams.topMargin = dp(12);
        content.addView(finishShift, shiftParams);

        finishShift.setEnabled(!running);
        finishShift.setOnClickListener(v -> confirmFinishShift());

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
            /*
             * Сначала получаем или создаём ключ текущего завершения.
             * Он сохраняется ДО записи в SQLite.
             * Если приложение закроется после сохранения смены,
             * при следующей попытке будет использован тот же ключ.
             */

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

            /*
             * Идемпотентное сохранение:
             * повторный вызов с тем же ключом вернёт ID существующей
             * записи, а не создаст дубликат.
             */

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

            /*
             * Сначала смена успешно сохраняется в базе.
             * Только после этого одним commit сбрасываются показатели
             * и очищается история отмены кругов старой смены.
             *
             * Если commit не удастся, pending_shift_key останется.
             * Повторное сохранение не создаст дубликат смены.
             */

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
                "Не удалось сохранить рейс. Статистика не изменена.",
                Toast.LENGTH_LONG
            ).show();
            return;
        }

        balance += amount;
        trips++;
        seconds += elapsed;

        prefs.edit()
            .putLong("balance", balance)
            .putLong("trips", trips)
            .putLong("seconds", seconds)
            .putLong("shiftEarned", shiftEarned + amount)
            .putLong("shiftTrips", shiftTrips + 1)
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

    private void showStats() {
        heading("Статистика", "Твои результаты и личные рекорды");

        long today = 0;
        long week = 0;
        long todayTrips = 0;
        long weekTrips = 0;
        long bestDay = 0;
        long bestDayTime = 0;
        long totalAmount = 0;
        long totalCount = 0;

        HashMap<String, Long> daily = new HashMap<>();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long todayStart = cal.getTimeInMillis();
        Calendar weekStart = (Calendar)cal.clone();
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

            while (cursor.moveToNext()) {
                long time = cursor.getLong(timeColumn);
                long amount = cursor.getLong(amountColumn);

                totalAmount = safeAdd(totalAmount, amount);
                totalCount++;

                Calendar itemDay = Calendar.getInstance();
                itemDay.setTimeInMillis(time);
                String key = dayKey.format(itemDay.getTime());

                long value = safeAdd(
                    daily.containsKey(key) ? daily.get(key) : 0L,
                    amount
                );
                daily.put(key, value);

                if (time >= todayStart) {
                    today = safeAdd(today, amount);
                    todayTrips++;
                }

                if (time >= weekStartTime) {
                    week = safeAdd(week, amount);
                    weekTrips++;
                }
            }

            for (String key : daily.keySet()) {
                long value = daily.get(key);
                if (value > bestDay) {
                    bestDay = value;
                    bestDayTime = new SimpleDateFormat(
                        "yyyyMMdd", Locale.US
                    ).parse(key).getTime();
                }
            }

        } catch (Exception e) {
            Toast.makeText(
                this, "Не удалось загрузить статистику",
                Toast.LENGTH_SHORT
            ).show();
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

        LinearLayout records = card();
        records.addView(text("ЛИЧНЫЕ РЕКОРДЫ", 15, fg(), true));

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

        long bestShift = 0;
        Cursor shifts = null;

        try {
            shifts = repository.getShiftsHistory();

            if (shifts != null) {
                int earnedColumn = shifts.getColumnIndex("earned");

                if (earnedColumn >= 0) {
                    while (shifts.moveToNext()) {
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

        records.addView(text(
            "Лучший заработок за смену: " +
            money(bestShift) + " ₽",
            14, accent(), true
        ));
    }

    private void showHistory() {
        heading("История кругов", "Сохранённые записи рейсов");

        Cursor cursor = null;

        try {
            cursor = repository.getTripsHistory();

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

        new AlertDialog.Builder(this)
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
                    Toast.makeText(
                        this,
                        "Проверь название, сумму и количество кругов",
                        Toast.LENGTH_LONG
                    ).show();
                }
            })
            .show();
    }

    private void confirmDeleteGoal(int index) {
        new AlertDialog.Builder(this)
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
            .show();
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
                                values[i] = safeAdd(values[i], amount);
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
            "Фиолетовая", "Белая", "Чёрная", "Красная",
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
                renderPage();
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
        new AlertDialog.Builder(this)
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
                e.remove("panel_size");
                e.remove("panel_opacity");
                e.remove("notifications_enabled");

                e.remove("home_block_balance");
                e.remove("home_block_rounds");
                e.remove("home_block_payout");
                e.remove("home_block_timer");
                e.remove("home_block_overlay");
                e.remove("home_block_order");

                e.apply();

                renderPage();

                Toast.makeText(
                    this,
                    "Настройки сброшены",
                    Toast.LENGTH_SHORT
                ).show();
            })
            .show();
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
            checks[i].setChecked(blockVisible(blockKeys[i]));
            box.addView(checks[i]);
        }

        new AlertDialog.Builder(this)
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

                Toast.makeText(
                    this,
                    "Главный экран обновлён",
                    Toast.LENGTH_SHORT
                ).show();
            })
            .show();
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

                Toast.makeText(
                    this,
                    "Разреши отображение поверх других окон",
                    Toast.LENGTH_LONG
                ).show();

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
                Toast.makeText(
                    this,
                    "Не удалось включить плавающую панель",
                    Toast.LENGTH_LONG
                ).show();
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
