package com.gathin.tvhome;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mosaico, an editorial Home: the day in large type and the weather with a bar per day on the left; on the
 * right a mosaic of 16:9 tiles, the shape of a TV app's banner: the first favourite twice the size, the first
 * HDMI input a wide one, and the rest of the apps and All apps after them.
 */
final class MosaicHomeLayout implements CustomHomeLayout {
    private final Context ctx;
    private final HomeStyle style;
    private final FrameLayout root;
    private final LinearLayout weatherCard;
    private final TextView weatherNow;
    private final LinearLayout bars;
    private final LinearLayout grid;
    private final View btnInputs;
    private final View btnSettings;
    private final List<View> tiles = new ArrayList<>();
    private HomeLayoutHost host;
    private View lastTile;

    MosaicHomeLayout(Context ctx) {
        this(ctx, HomeStyle.load(ctx));
    }

    MosaicHomeLayout(Context ctx, HomeStyle style) {
        this.ctx = ctx;
        this.style = style;
        int accent = style.baseAccent();
        root = new FrameLayout(ctx);
        View dim = new View(ctx);
        dim.setBackgroundColor(0x9E06070D);
        root.addView(dim, match());

        LinearLayout across = new LinearLayout(ctx);
        across.setOrientation(LinearLayout.HORIZONTAL);
        across.setClipChildren(false);
        across.setClipToPadding(false);
        across.setPadding(dp(44), dp(30), dp(44), dp(30));
        root.addView(across, match());

        LinearLayout left = new LinearLayout(ctx);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setClipChildren(false);
        across.addView(left, new LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.MATCH_PARENT));
        // Brand on the left, Inputs and Settings on the right: OK on Settings opens the TV's settings and holding
        // it opens BuzzTV's Ajustes, as in every other layout, so this one is never a dead end.
        LinearLayout topRow = new LinearLayout(ctx);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setClipChildren(false);
        topRow.setClipToPadding(false);
        LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        topLp.bottomMargin = dp(14);
        left.addView(topRow, topLp);
        topRow.addView(Buzz.brand(ctx, 20, 14, 0.85f), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnInputs = Buzz.iconButton(ctx, R.drawable.ic_input, R.string.inputs, accent);
        btnSettings = Buzz.iconButton(ctx, R.drawable.ic_settings, R.string.settings, accent);
        for (View b : new View[]{btnInputs, btnSettings}) {
            b.setId(View.generateViewId());
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(40), dp(40));
            bl.setMarginStart(dp(10));
            topRow.addView(b, bl);
        }
        btnInputs.setOnClickListener(v -> host.openInputs(v));
        btnSettings.setOnClickListener(v -> host.openSettings());
        btnSettings.setOnLongClickListener(v -> {
            host.openLauncherSettings();
            return true;
        });
        TextClock weekday = Buzz.date(ctx, 13, accent, R.string.font_medium);
        weekday.setFormat12Hour("EEEE");
        weekday.setFormat24Hour("EEEE");
        weekday.setAllCaps(true);
        weekday.setLetterSpacing(0.22f);
        left.addView(weekday);
        TextClock day = Buzz.date(ctx, 92 * style.scale(), Buzz.TEXT, R.string.font_medium);
        day.setFormat12Hour("d");
        day.setFormat24Hour("d");
        day.setLetterSpacing(-0.03f);
        left.addView(day);
        LinearLayout monthRow = new LinearLayout(ctx);
        monthRow.setOrientation(LinearLayout.HORIZONTAL);
        TextClock month = Buzz.date(ctx, 17, Buzz.TEXT_2, R.string.font_light);
        month.setFormat12Hour("MMMM · ");
        month.setFormat24Hour("MMMM · ");
        monthRow.addView(month);
        monthRow.addView(Buzz.clock(ctx, 17, Buzz.TEXT_2, R.string.font_light));
        left.addView(monthRow);

        weatherCard = new LinearLayout(ctx);
        weatherCard.setId(View.generateViewId());
        weatherCard.setOrientation(LinearLayout.VERTICAL);
        weatherCard.setFocusable(true);
        weatherCard.setClickable(true);
        weatherCard.setPadding(dp(16), dp(14), dp(16), dp(12));
        float r = dp(18);
        weatherCard.setBackground(Buzz.states(Buzz.round(0xC70E101A, r, Math.max(1, dp(1)), 0x24FFFFFF),
                Buzz.round(0xE00E101A, r, dp(2), accent)));
        weatherCard.setOnClickListener(v -> host.openForecast(accent));
        weatherCard.setNextFocusUpId(btnSettings.getId());
        btnInputs.setNextFocusDownId(weatherCard.getId());
        btnSettings.setNextFocusDownId(weatherCard.getId());
        Buzz.lift(weatherCard, 1.03f, null);
        weatherNow = Buzz.text(ctx, 15, Buzz.TEXT, R.string.font_regular);
        weatherNow.setText(R.string.panel_weather_off);
        weatherCard.addView(weatherNow);
        bars = new LinearLayout(ctx);
        bars.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        bp.topMargin = dp(8);
        weatherCard.addView(bars, bp);
        TextView more = Buzz.text(ctx, 12, accent, R.string.font_regular);
        more.setText(R.string.mosaic_week);
        weatherCard.addView(more);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        wp.topMargin = dp(16);
        left.addView(weatherCard, wp);

        grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        gp.setMarginStart(dp(22));
        across.addView(grid, gp);
    }

    @Override
    public View rootView() {
        return root;
    }

    @Override
    public void bind(HomeLayoutHost host) {
        this.host = host;
        onAppsChanged();
    }

    @Override
    public void onAppsChanged() {
        if (host == null) return;
        String focusedKey = lastTile != null ? (String) lastTile.getTag() : null;
        grid.removeAllViews();
        tiles.clear();
        lastTile = null;
        List<HomeLayoutHost.HomeInput> ins = style.inputsRow ? host.inputs(1) : new ArrayList<>();
        // Every tile keeps the 16:9 shape of a TV app's banner: three equal columns and four equal row units, so a
        // small tile is one column by one unit and the big one two by two, the same shape twice the size.
        List<AppAdapter.Entry> apps = HomeApps.pickFilled(host, ins.isEmpty() ? 8 : 7);
        int next = 0;
        View restore = null;
        LinearLayout top = line(2f);
        View hero = apps.isEmpty() ? tile(null, false) : tile(apps.get(next++), true);
        top.addView(hero, cell(2f));
        tiles.add(hero);
        LinearLayout side = new LinearLayout(ctx);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setClipChildren(false);
        top.addView(side, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        for (int r = 0; r < 2; r++) {
            AppAdapter.Entry e = next < apps.size() ? apps.get(next++) : null;
            View t = tile(e, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            int m = dp(7);
            lp.setMargins(m, m, m, m);
            side.addView(t, lp);
            tiles.add(t);
        }
        for (int row = 0; row < 2; row++) {
            LinearLayout line = line(1f);
            int slots = 3;
            if (row == 0 && !ins.isEmpty()) {
                View in = inputTile(ins.get(0));
                line.addView(in, cell(2f));
                tiles.add(in);
                slots = 1;
            }
            for (int c = 0; c < slots; c++) {
                boolean last = row == 1 && c == slots - 1;
                AppAdapter.Entry e = !last && next < apps.size() ? apps.get(next++) : null;
                View t = tile(e, false);
                line.addView(t, cell(1f));
                tiles.add(t);
            }
        }
        for (View t : tiles) {
            Object key = t.getTag();
            if (key != null && key.equals(focusedKey)) restore = t;
        }
        View target = restore != null ? restore : tiles.get(0);
        lastTile = target;
        pointAt(target);
        if (root.findFocus() == null || restore != null) root.post(target::requestFocus);
        host.readForecast(this::fillBars);
    }

    /** Right from the weather card and from Settings returns to the tile last in focus. */
    private void pointAt(View tile) {
        weatherCard.setNextFocusRightId(tile.getId());
        btnSettings.setNextFocusRightId(tile.getId());
    }

    private LinearLayout line(float weight) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setClipChildren(false);
        grid.addView(l, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, weight));
        return l;
    }

    private LinearLayout.LayoutParams cell(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        int m = dp(7);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    private View tile(AppAdapter.Entry e, boolean hero) {
        int a = e == null ? style.baseAccent() : style.accentFor(e);
        FrameLayout t = Buzz.appCard(ctx, e, a, hero ? 22 : 16, false);
        t.setOnClickListener(v -> {
            if (e == null) host.openAllApps();
            else host.launchApp(e);
        });
        Buzz.holdForInfo(t, e, host);
        Buzz.lift(t, hero ? 1.04f : 1.08f, (v, f) -> {
            if (f) {
                lastTile = v;
                pointAt(v);
            }
        });
        return t;
    }

    private View inputTile(HomeLayoutHost.HomeInput in) {
        LinearLayout t = new LinearLayout(ctx);
        t.setId(View.generateViewId());
        t.setOrientation(LinearLayout.HORIZONTAL);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        float r = dp(16);
        t.setBackground(Buzz.states(Buzz.round(0xD914182A, r, Math.max(1, dp(1)), 0x24FFFFFF),
                Buzz.round(0xF014182A, r, dp(3), Buzz.TEXT)));
        android.widget.ImageView i = new android.widget.ImageView(ctx);
        i.setImageResource(R.drawable.ic_input);
        t.addView(i, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView n = Buzz.text(ctx, 16, Buzz.TEXT, R.string.font_medium);
        n.setText(in.label);
        n.setAllCaps(true);
        n.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.setMarginStart(dp(12));
        t.addView(n, nl);
        View dot = new View(ctx);
        dot.setBackground(Buzz.round(in.connected ? 0xFF3DD68C : 0x59FFFFFF, dp(4), 0, 0));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(8), dp(8));
        dl.setMarginStart(dp(10));
        t.addView(dot, dl);
        t.setContentDescription(in.label);
        t.setOnClickListener(v -> host.openInput(in));
        Buzz.lift(t, 1.05f, (v, f) -> {
            if (f) lastTile = v;
        });
        return t;
    }

    private void fillBars(List<LiveWeather.Day> days) {
        bars.removeAllViews();
        if (days.isEmpty()) return;
        int n = Math.min(5, days.size());
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            lo = Math.min(lo, days.get(i).minC);
            hi = Math.max(hi, days.get(i).maxC);
        }
        LocalDate today = LocalDate.now();
        String todayWord = ctx.getString(R.string.forecast_today);
        for (int i = 0; i < n; i++) {
            LiveWeather.Day d = days.get(i);
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = Buzz.text(ctx, 13, Buzz.TEXT_2, R.string.font_regular);
            name.setText(i == 0 ? todayWord : LiveWeather.dayLabel(d.date, today.minusDays(30), todayWord, todayWord, Locale.getDefault()));
            line.addView(name, new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView min = Buzz.text(ctx, 13, Buzz.TEXT_3, R.string.font_regular);
            min.setText(ctx.getString(R.string.degrees, d.minC));
            min.setGravity(Gravity.END);
            line.addView(min, new LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.WRAP_CONTENT));
            float[] f = LiveWeather.rangeFractions(d.minC, d.maxC, lo, hi);
            ForecastPanel.RangeBar bar = new ForecastPanel.RangeBar(ctx, f[0], f[1], d.minC, d.maxC);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, dp(5), 1f);
            bl.setMargins(dp(8), 0, dp(8), 0);
            line.addView(bar, bl);
            TextView max = Buzz.text(ctx, 13, Buzz.TEXT, R.string.font_medium);
            max.setText(ctx.getString(R.string.degrees, d.maxC));
            line.addView(max, new LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            bars.addView(line, lp);
        }
    }

    @Override
    public void onWeather(LiveWeather.Reading r) {
        if (r == null) return;
        String condition = ctx.getString(LiveWeather.conditionRes(r.weatherCode));
        weatherNow.setText(LiveWeather.formatHeader(r.tempC, condition, r.city));
        if (host != null) host.readForecast(this::fillBars);
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
}
