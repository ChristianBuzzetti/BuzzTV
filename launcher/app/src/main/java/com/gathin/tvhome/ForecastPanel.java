package com.gathin.tvhome;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The extended forecast, opened from any weather chip: today's conditions large on top, the next seven days as
 * cards below. Moving across the days puts the focused one in the large area. The data comes from the Weather
 * Sync add-on through LiveWeather; the launcher itself still never touches the network.
 */
final class ForecastPanel {
    private final Context ctx;
    private final Dialog dialog;
    private final LiveWeather.Reading now;
    private final int accent;
    private final WeatherIconView heroIcon;
    private final TextView heroDay;
    private final TextView heroTemp;
    private final TextView heroLow;
    private final TextView heroCondition;
    private final TextView heroDetails;
    private final LinearLayout days;
    private final TextView status;
    private final List<LiveWeather.Day> loaded = new ArrayList<>();

    private ForecastPanel(Context ctx, LiveWeather.Reading now, int accent) {
        this.ctx = ctx;
        this.now = now;
        this.accent = accent == 0 ? Buzz.HONEY : accent;
        dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);

        FrameLayout root = new FrameLayout(ctx);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xE6070A11, 0xF0070A11, 0xFA070A11}));
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        int padH = dp(64);
        col.setPadding(padH, dp(36), padH, dp(28));
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView city = Buzz.text(ctx, 14, this.accent, R.string.font_medium);
        city.setAllCaps(true);
        city.setLetterSpacing(0.22f);
        city.setText(now != null && !now.city.isEmpty() ? now.city : ctx.getString(R.string.forecast_title));
        col.addView(city);
        TextView title = Buzz.text(ctx, 30, Buzz.TEXT, R.string.font_medium);
        title.setText(R.string.forecast_title);
        LinearLayout.LayoutParams tp = wrap();
        tp.topMargin = dp(6);
        col.addView(title, tp);

        // The large area: icon, temperature, condition, details.
        LinearLayout hero = new LinearLayout(ctx);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        col.addView(hero, hp);
        heroIcon = new WeatherIconView(ctx);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(96), dp(96));
        ip.setMarginEnd(dp(28));
        hero.addView(heroIcon, ip);
        LinearLayout tempCol = new LinearLayout(ctx);
        tempCol.setOrientation(LinearLayout.VERTICAL);
        hero.addView(tempCol, wrap());
        heroDay = Buzz.text(ctx, 16, Buzz.TEXT_2, R.string.font_medium);
        heroDay.setAllCaps(true);
        heroDay.setLetterSpacing(0.14f);
        tempCol.addView(heroDay);
        LinearLayout tempRow = new LinearLayout(ctx);
        tempRow.setOrientation(LinearLayout.HORIZONTAL);
        tempRow.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams trp = wrap();
        trp.topMargin = dp(4);
        tempCol.addView(tempRow, trp);
        heroTemp = Buzz.text(ctx, 76, Buzz.TEXT, R.string.font_extralight);
        tempRow.addView(heroTemp);
        heroLow = Buzz.text(ctx, 34, Buzz.TEXT_3, R.string.font_light);
        LinearLayout.LayoutParams lp = wrap();
        lp.setMarginStart(dp(10));
        lp.bottomMargin = dp(10);
        tempRow.addView(heroLow, lp);
        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tcp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tcp.setMarginStart(dp(40));
        hero.addView(textCol, tcp);
        heroCondition = Buzz.text(ctx, 30, Buzz.TEXT, R.string.font_regular);
        textCol.addView(heroCondition);
        heroDetails = Buzz.text(ctx, 17, Buzz.TEXT_2, R.string.font_regular);
        heroDetails.setSingleLine(false);
        heroDetails.setMaxLines(2);
        heroDetails.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams detailsLp = wrap();
        detailsLp.topMargin = dp(10);
        textCol.addView(heroDetails, detailsLp);

        days = new LinearLayout(ctx);
        days.setOrientation(LinearLayout.HORIZONTAL);
        days.setClipChildren(false);
        days.setClipToPadding(false);
        col.addView(days, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        status = Buzz.text(ctx, 18, Buzz.TEXT_2, R.string.font_regular);
        status.setSingleLine(false);
        status.setGravity(Gravity.CENTER);
        status.setText(R.string.forecast_loading);
        status.setMinHeight(dp(150));
        days.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = Buzz.text(ctx, 14, Buzz.TEXT_3, R.string.font_regular);
        hint.setText(R.string.forecast_hint);
        LinearLayout.LayoutParams hip = wrap();
        hip.topMargin = dp(16);
        col.addView(hint, hip);

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        showNow();
    }

    /** Opens the panel; the days fill in as soon as the add-on answers. */
    static Dialog show(Context ctx, LiveWeather weather, LiveWeather.Reading now, int accent) {
        ForecastPanel panel = new ForecastPanel(ctx, now, accent);
        panel.dialog.show();
        if (weather != null) weather.readForecast(panel::fill);
        else panel.fill(new ArrayList<>());
        return panel.dialog;
    }

    private void fill(List<LiveWeather.Day> list) {
        if (!dialog.isShowing()) return;
        days.removeAllViews();
        loaded.clear();
        loaded.addAll(list);
        if (list.isEmpty()) {
            status.setText(R.string.forecast_missing);
            days.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        int weekMin = Integer.MAX_VALUE, weekMax = Integer.MIN_VALUE;
        for (LiveWeather.Day d : list) {
            weekMin = Math.min(weekMin, d.minC);
            weekMax = Math.max(weekMax, d.maxC);
        }
        LocalDate today = LocalDate.now();
        Locale locale = Locale.getDefault();
        String todayWord = ctx.getString(R.string.forecast_today);
        String tomorrowWord = ctx.getString(R.string.forecast_tomorrow);
        View first = null;
        for (int i = 0; i < list.size(); i++) {
            LiveWeather.Day d = list.get(i);
            View card = dayCard(d, LiveWeather.dayLabel(d.date, today, todayWord, tomorrowWord, locale),
                    LiveWeather.rangeFractions(d.minC, d.maxC, weekMin, weekMax), i);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            int m = dp(6);
            p.setMargins(m, m, m, m);
            days.addView(card, p);
            if (first == null) first = card;
        }
        showNow();
        if (first != null) first.requestFocus();
    }

    private View dayCard(LiveWeather.Day d, String label, float[] range, int index) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setFocusable(true);
        card.setClickable(true);
        int pad = dp(12);
        card.setPadding(pad, dp(14), pad, dp(12));
        float r = dp(18);
        card.setBackground(Buzz.states(Buzz.round(0x14FFFFFF, r, Math.max(1, dp(1)), 0x1FFFFFFF),
                Buzz.round(Buzz.withAlpha(accent, 0x38), r, dp(2), accent)));

        TextView name = Buzz.text(ctx, 17, Buzz.TEXT, R.string.font_medium);
        name.setText(label);
        card.addView(name, wrap());
        WeatherIconView icon = new WeatherIconView(ctx);
        icon.setKind(LiveWeather.iconKind(d.weatherCode, true));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(38), dp(38));
        ip.topMargin = dp(10);
        ip.bottomMargin = dp(8);
        card.addView(icon, ip);
        TextView max = Buzz.text(ctx, 22, Buzz.TEXT, R.string.font_medium);
        max.setText(ctx.getString(R.string.degrees, d.maxC));
        card.addView(max, wrap());
        TextView min = Buzz.text(ctx, 15, Buzz.TEXT_3, R.string.font_regular);
        min.setText(ctx.getString(R.string.degrees, d.minC));
        LinearLayout.LayoutParams mp = wrap();
        mp.topMargin = dp(2);
        card.addView(min, mp);
        RangeBar bar = new RangeBar(ctx, range[0], range[1], d.minC, d.maxC);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        bp.topMargin = dp(10);
        card.addView(bar, bp);
        if (d.precipPct >= 0) {
            TextView rain = Buzz.text(ctx, 13, 0xFF7CC4FF, R.string.font_medium);
            rain.setText(ctx.getString(R.string.percent, d.precipPct));
            LinearLayout.LayoutParams rp = wrap();
            rp.topMargin = dp(8);
            card.addView(rain, rp);
        }
        card.setContentDescription(label + ". " + ctx.getString(LiveWeather.conditionRes(d.weatherCode)) + ". "
                + ctx.getString(R.string.forecast_max, d.maxC) + ", " + ctx.getString(R.string.forecast_min, d.minC));
        Buzz.lift(card, 1.06f, (v, focused) -> {
            if (focused) showDay(index);
        });
        return card;
    }

    /** The large area for "now": the live reading with its feels-like, humidity and wind. */
    private void showNow() {
        if (now == null) {
            heroDay.setText(R.string.forecast_today);
            heroTemp.setText("--");
            heroLow.setText("");
            heroCondition.setText("");
            heroDetails.setText("");
            return;
        }
        heroIcon.setKind(LiveWeather.iconKind(now.weatherCode, now.isDay));
        heroDay.setText(R.string.forecast_now);
        heroTemp.setText(ctx.getString(R.string.degrees, now.tempC));
        heroLow.setText("");
        heroCondition.setText(LiveWeather.conditionRes(now.weatherCode));
        heroDetails.setText(details(now, loaded.isEmpty() ? null : loaded.get(0)));
    }

    private void showDay(int index) {
        if (index < 0 || index >= loaded.size()) return;
        LiveWeather.Day d = loaded.get(index);
        if (index == 0 && now != null && d.date.equals(LocalDate.now())) {
            showNow();
            return;
        }
        Locale locale = Locale.getDefault();
        String day = d.date.getDayOfWeek().getDisplayName(TextStyle.FULL, locale) + " " + d.date.getDayOfMonth();
        heroDay.setText(day);
        heroIcon.setKind(LiveWeather.iconKind(d.weatherCode, true));
        heroTemp.setText(ctx.getString(R.string.degrees, d.maxC));
        heroLow.setText("/ " + ctx.getString(R.string.degrees, d.minC));
        heroCondition.setText(LiveWeather.conditionRes(d.weatherCode));
        heroDetails.setText(d.precipPct >= 0 ? ctx.getString(R.string.forecast_rain, d.precipPct) : "");
    }

    private String details(LiveWeather.Reading r, LiveWeather.Day today) {
        StringBuilder sb = new StringBuilder();
        if (today != null) {
            append(sb, ctx.getString(R.string.forecast_max, today.maxC));
            append(sb, ctx.getString(R.string.forecast_min, today.minC));
        }
        if (r.feelsC != LiveWeather.UNKNOWN) append(sb, ctx.getString(R.string.forecast_feels, r.feelsC));
        if (r.humidity != LiveWeather.UNKNOWN) append(sb, ctx.getString(R.string.forecast_humidity, r.humidity));
        if (r.windKmh != LiveWeather.UNKNOWN) append(sb, ctx.getString(R.string.forecast_wind, r.windKmh));
        if (today != null && today.precipPct >= 0) append(sb, ctx.getString(R.string.forecast_rain, today.precipPct));
        return sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
        if (sb.length() > 0) sb.append("  ·  ");
        sb.append(part);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float v) {
        return Buzz.dp(ctx, v);
    }

    /** A day's low-to-high span on the week's temperature scale, coloured cool to warm. */
    static final class RangeBar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float start, end;
        private final int minC, maxC;

        RangeBar(Context c, float start, float end, int minC, int maxC) {
            super(c);
            this.start = start;
            this.end = end;
            this.minC = minC;
            this.maxC = maxC;
            track.setColor(0x26FFFFFF);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float a = start * w, b = Math.max(a + h, end * w);
            fill.setShader(new LinearGradient(a, 0, b, 0, tempColor(minC), tempColor(maxC), Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(), r = h / 2f;
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, r, r, track);
            float a = start * w, b = Math.max(a + h, end * w);
            rect.set(a, 0, Math.min(w, b), h);
            canvas.drawRoundRect(rect, r, r, fill);
        }
    }

    /** Blue at freezing, teal at 12°, honey at 24°, red at 34° and above. */
    static int tempColor(int c) {
        int[] stops = {0xFF5AA9FF, 0xFF3DD6C6, 0xFFFFC53D, 0xFFFF5A4E};
        float[] at = {0f, 12f, 24f, 34f};
        if (c <= at[0]) return stops[0];
        for (int i = 1; i < at.length; i++) {
            if (c <= at[i]) return Buzz.blend(stops[i - 1], stops[i], (c - at[i - 1]) / (at[i] - at[i - 1]));
        }
        return stops[stops.length - 1];
    }
}
