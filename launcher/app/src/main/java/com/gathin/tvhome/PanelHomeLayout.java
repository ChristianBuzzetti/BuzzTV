package com.gathin.tvhome;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Panel: a dashboard. A rail on the left (Home, Apps, Inputs, Weather, Settings), then two widgets on top, the
 * clock and the weather with the whole week in it, and the apps as a grid of five columns below.
 */
final class PanelHomeLayout implements CustomHomeLayout {
    private static final int COLUMNS = 5;
    private static final int ROWS = 2;

    private final Context ctx;
    private final HomeStyle style;
    private final FrameLayout root;
    private final TextView greeting;
    private final LinearLayout weatherCard;
    private final WeatherIconView weatherIcon;
    private final TextView weatherTemp;
    private final TextView weatherLine;
    private final TextView weatherDetails;
    private final LinearLayout week;
    private final LinearLayout grid;
    private final View[] rail;
    private final List<View> tiles = new ArrayList<>();
    private HomeLayoutHost host;
    private View lastTile;

    PanelHomeLayout(Context ctx) {
        this(ctx, HomeStyle.load(ctx));
    }

    PanelHomeLayout(Context ctx, HomeStyle style) {
        this.ctx = ctx;
        this.style = style;
        int accent = style.baseAccent();
        root = new FrameLayout(ctx);
        View dim = new View(ctx);
        dim.setBackgroundColor(0x73060810);
        root.addView(dim, match());

        LinearLayout across = new LinearLayout(ctx);
        across.setOrientation(LinearLayout.HORIZONTAL);
        across.setClipChildren(false);
        root.addView(across, match());

        // The rail.
        LinearLayout nav = new LinearLayout(ctx);
        nav.setOrientation(LinearLayout.VERTICAL);
        nav.setGravity(Gravity.CENTER_HORIZONTAL);
        nav.setPadding(0, dp(26), 0, dp(26));
        nav.setBackgroundColor(0xB8080A12);
        across.addView(nav, new LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView mark = Buzz.text(ctx, 15, Buzz.TEXT, R.string.font_medium);
        mark.setText(Buzz.wordmark("b", accent));
        LinearLayout.LayoutParams mp = wrap();
        mp.bottomMargin = dp(22);
        nav.addView(mark, mp);
        View home = railButton(R.drawable.ic_home, R.string.panel_home, accent, true);
        View apps = railButton(R.drawable.ic_apps, R.string.panel_apps, accent, false);
        View ins = railButton(R.drawable.ic_input, R.string.inputs, accent, false);
        View weather = railButton(R.drawable.ic_cloud, R.string.panel_weather, accent, false);
        View settings = railButton(R.drawable.ic_settings, R.string.settings, accent, false);
        rail = new View[]{home, apps, ins, weather, settings};
        for (View v : rail) nav.addView(v);
        home.setOnClickListener(v -> {
            if (lastTile != null) lastTile.requestFocus();
        });
        apps.setOnClickListener(v -> host.openAllApps());
        ins.setOnClickListener(v -> host.openInputs(v));
        weather.setOnClickListener(v -> host.openForecast(accent));
        settings.setOnClickListener(v -> host.openSettings());
        settings.setOnLongClickListener(v -> {
            host.openLauncherSettings();
            return true;
        });

        LinearLayout main = new LinearLayout(ctx);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setClipChildren(false);
        main.setClipToPadding(false);
        main.setPadding(dp(30), dp(26), dp(40), dp(24));
        across.addView(main, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        LinearLayout widgets = new LinearLayout(ctx);
        widgets.setOrientation(LinearLayout.HORIZONTAL);
        widgets.setClipChildren(false);
        main.addView(widgets, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(166 * style.scale())));

        LinearLayout clockCard = card(false, accent);
        clockCard.setGravity(Gravity.CENTER_VERTICAL);
        greeting = Buzz.text(ctx, 12, Buzz.TEXT_2, R.string.font_medium);
        greeting.setAllCaps(true);
        greeting.setLetterSpacing(0.2f);
        clockCard.addView(greeting);
        TextView clock = Buzz.clock(ctx, 64 * style.scale(), Buzz.TEXT, R.string.font_extralight);
        LinearLayout.LayoutParams clp = wrap();
        clp.topMargin = dp(6);
        clockCard.addView(clock, clp);
        TextView date = Buzz.date(ctx, 15, Buzz.TEXT_2, R.string.font_light);
        LinearLayout.LayoutParams dlp = wrap();
        dlp.topMargin = dp(6);
        clockCard.addView(date, dlp);
        widgets.addView(clockCard, new LinearLayout.LayoutParams(dp(230), ViewGroup.LayoutParams.MATCH_PARENT));

        weatherCard = card(true, accent);
        weatherCard.setId(View.generateViewId());
        weatherCard.setOnClickListener(v -> host.openForecast(accent));
        Buzz.lift(weatherCard, 1.03f, null);
        LinearLayout now = new LinearLayout(ctx);
        now.setOrientation(LinearLayout.HORIZONTAL);
        now.setGravity(Gravity.CENTER_VERTICAL);
        weatherCard.addView(now, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        weatherIcon = new WeatherIconView(ctx);
        now.addView(weatherIcon, new LinearLayout.LayoutParams(dp(36), dp(36)));
        weatherTemp = Buzz.text(ctx, 34, Buzz.TEXT, R.string.font_light);
        LinearLayout.LayoutParams wtp = wrap();
        wtp.setMarginStart(dp(10));
        wtp.setMarginEnd(dp(14));
        now.addView(weatherTemp, wtp);
        LinearLayout lines = new LinearLayout(ctx);
        lines.setOrientation(LinearLayout.VERTICAL);
        now.addView(lines, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        weatherLine = Buzz.text(ctx, 15, Buzz.TEXT, R.string.font_regular);
        lines.addView(weatherLine);
        weatherDetails = Buzz.text(ctx, 12, Buzz.TEXT_2, R.string.font_regular);
        LinearLayout.LayoutParams wdp = wrap();
        wdp.topMargin = dp(2);
        lines.addView(weatherDetails, wdp);
        week = new LinearLayout(ctx);
        week.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams wkp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        wkp.topMargin = dp(10);
        weatherCard.addView(week, wkp);
        weatherLine.setText(R.string.panel_weather_off);
        LinearLayout.LayoutParams wcp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        wcp.setMarginStart(dp(16));
        widgets.addView(weatherCard, wcp);

        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(20 * style.scale());
        hp.bottomMargin = dp(10);
        main.addView(head, hp);
        TextView appsLabel = Buzz.text(ctx, 18, Buzz.TEXT, R.string.font_medium);
        appsLabel.setText(R.string.panel_apps);
        head.addView(appsLabel);

        grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        main.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout card(boolean focusable, int accent) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(16), dp(20), dp(14));
        float r = dp(20);
        if (focusable) {
            c.setFocusable(true);
            c.setClickable(true);
            c.setBackground(Buzz.states(Buzz.round(0xAD0C0F18, r, Math.max(1, dp(1)), 0x1FFFFFFF),
                    Buzz.round(0xCC0C0F18, r, dp(2), accent)));
        } else {
            c.setBackground(Buzz.round(0xAD0C0F18, r, Math.max(1, dp(1)), 0x1FFFFFFF));
        }
        return c;
    }

    private View railButton(int icon, int label, int accent, boolean selected) {
        LinearLayout b = new LinearLayout(ctx);
        b.setId(View.generateViewId());
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        float r = dp(14);
        b.setBackground(Buzz.states(Buzz.round(selected ? 0x26FFFFFF : 0, r, 0, 0), Buzz.round(accent, r, 0, 0)));
        ImageView i = new ImageView(ctx);
        i.setImageResource(icon);
        b.addView(i, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView t = Buzz.text(ctx, 10, Buzz.TEXT, R.string.font_medium);
        t.setText(label);
        LinearLayout.LayoutParams tl = wrap();
        tl.topMargin = dp(4);
        b.addView(t, tl);
        b.setContentDescription(ctx.getString(label));
        Buzz.lift(b, 1.06f, (v, f) -> {
            int ink = f ? 0xFF070A11 : Buzz.TEXT;
            t.setTextColor(ink);
            if (f) i.setColorFilter(ink);
            else i.clearColorFilter();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(58), dp(54));
        lp.bottomMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    public View rootView() {
        return root;
    }

    @Override
    public void bind(HomeLayoutHost host) {
        this.host = host;
        onShown();
        onAppsChanged();
    }

    @Override
    public void onShown() {
        if (!style.greeting) {
            greeting.setVisibility(View.GONE);
            return;
        }
        String base = ctx.getString(Buzz.greetingRes(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)));
        String name = host == null ? "" : host.greetingName();
        greeting.setText(name.isEmpty() ? base : ctx.getString(R.string.greeting_with_name, base, name));
    }

    @Override
    public void onAppsChanged() {
        if (host == null) return;
        String focusedKey = lastTile != null ? (String) lastTile.getTag() : null;
        grid.removeAllViews();
        tiles.clear();
        lastTile = null;
        List<AppAdapter.Entry> apps = HomeApps.pickFilled(host, COLUMNS * ROWS - 1);
        View restore = null;
        LinearLayout line = null;
        int total = apps.size() + 1;
        for (int i = 0; i < total; i++) {
            if (i % COLUMNS == 0) {
                line = new LinearLayout(ctx);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setClipChildren(false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(84 * style.scale()));
                lp.bottomMargin = dp(14);
                grid.addView(line, lp);
            }
            AppAdapter.Entry e = i < apps.size() ? apps.get(i) : null;
            View t = Buzz.appCard(ctx, e, e == null ? style.baseAccent() : style.accentFor(e), 14, false);
            t.setOnClickListener(v -> {
                if (e == null) host.openAllApps();
                else host.launchApp(e);
            });
            Buzz.holdForInfo(t, e, host);
            Buzz.lift(t, 1.1f, (v, f) -> {
                if (f) {
                    lastTile = v;
                    weatherCard.setNextFocusDownId(v.getId());
                }
            });
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            tp.setMarginEnd(i % COLUMNS == COLUMNS - 1 ? 0 : dp(14));
            line.addView(t, tp);
            tiles.add(t);
            if (e != null && e.component.flattenToString().equals(focusedKey)) restore = t;
        }
        // Fill the last row so the tiles keep their width.
        for (int i = total; i % COLUMNS != 0; i++) {
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            tp.setMarginEnd(i % COLUMNS == COLUMNS - 1 ? 0 : dp(14));
            line.addView(Buzz.spacer(ctx), tp);
        }
        for (int i = 0; i < tiles.size(); i++) {
            if (i % COLUMNS == 0) tiles.get(i).setNextFocusLeftId(rail[0].getId());
        }
        View target = restore != null ? restore : tiles.get(0);
        lastTile = target;
        weatherCard.setNextFocusDownId(target.getId());
        if (root.findFocus() == null || restore != null) root.post(target::requestFocus);
        host.readForecast(this::fillWeek);
    }

    private void fillWeek(List<LiveWeather.Day> days) {
        week.removeAllViews();
        if (days.isEmpty()) return;
        LocalDate today = LocalDate.now();
        String todayWord = ctx.getString(R.string.forecast_today);
        for (int i = 0; i < Math.min(7, days.size()); i++) {
            LiveWeather.Day d = days.get(i);
            LinearLayout c = new LinearLayout(ctx);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            c.setBackground(Buzz.round(0x0DFFFFFF, dp(10), 0, 0));
            // Today by name, then short weekdays: "Mañana" would not fit the narrow column.
            String label = i == 0 ? todayWord
                    : LiveWeather.dayLabel(d.date, today.minusDays(30), todayWord, todayWord, Locale.getDefault());
            TextView day = Buzz.text(ctx, 11, Buzz.TEXT_2, R.string.font_regular);
            day.setText(label);
            c.addView(day);
            TextView max = Buzz.text(ctx, 14, Buzz.TEXT, R.string.font_medium);
            max.setText(ctx.getString(R.string.degrees, d.maxC));
            c.addView(max);
            TextView min = Buzz.text(ctx, 11, Buzz.TEXT_3, R.string.font_regular);
            min.setText(ctx.getString(R.string.degrees, d.minC));
            c.addView(min);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            lp.setMarginEnd(i == 6 ? 0 : dp(6));
            week.addView(c, lp);
        }
    }

    @Override
    public void onWeather(LiveWeather.Reading r) {
        if (r == null) return;
        weatherIcon.setKind(LiveWeather.iconKind(r.weatherCode, r.isDay));
        weatherTemp.setText(ctx.getString(R.string.degrees, r.tempC));
        String condition = ctx.getString(LiveWeather.conditionRes(r.weatherCode));
        weatherLine.setText(r.city.isEmpty() ? condition : condition + " · " + r.city);
        StringBuilder sb = new StringBuilder();
        if (r.feelsC != LiveWeather.UNKNOWN) sb.append(ctx.getString(R.string.forecast_feels, r.feelsC));
        if (r.humidity != LiveWeather.UNKNOWN) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(ctx.getString(R.string.forecast_humidity, r.humidity));
        }
        weatherDetails.setText(sb.toString());
        if (host != null) host.readForecast(this::fillWeek);
    }

    @Override
    public void onWifiChanged(String label) {
    }

    @Override
    public boolean handleBack() {
        View focused = root.findFocus();
        if (focused != null && !tiles.contains(focused) && lastTile != null) {
            lastTile.requestFocus();
            return true;
        }
        if (!tiles.isEmpty() && focused != tiles.get(0)) {
            tiles.get(0).requestFocus();
            return true;
        }
        return false;
    }

    private int dp(float v) {
        return Buzz.dp(ctx, v);
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
