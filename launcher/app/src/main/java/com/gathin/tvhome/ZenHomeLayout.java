package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Zen, the quiet Home: no cards and no bars, only type over the wallpaper. A large thin clock sits at the bottom
 * left with the date and the weather; the apps are a column of names on the right, dimmed until focused, when the
 * name brightens, steps forward and shows its icon. Inputs and Settings are two small words at the top.
 */
final class ZenHomeLayout implements CustomHomeLayout {
    private static final int MAX_APPS = 8;
    private static final float DIM = 0.5f;

    private final Context ctx;
    private final FrameLayout root;
    private final WeatherChip chip;
    private final LinearLayout list;
    private final ScrollView scroll;
    private final View[] links;
    private final List<View> items = new ArrayList<>();
    private HomeLayoutHost host;
    private View lastItem;

    ZenHomeLayout(Context ctx) {
        this.ctx = ctx;
        root = new FrameLayout(ctx);
        View scrim = new View(ctx);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x8C000000, 0x14000000, 0x14000000, 0x99000000}));
        root.addView(scrim, match());
        View floor = new View(ctx);
        floor.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x80000000}));
        root.addView(floor, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260),
                Gravity.BOTTOM));

        // Bottom left: time, date, weather.
        LinearLayout time = new LinearLayout(ctx);
        time.setOrientation(LinearLayout.VERTICAL);
        time.setClipChildren(false);
        TextView clock = Buzz.clock(ctx, 120, Buzz.TEXT, R.string.font_extralight);
        Buzz.shadow(clock);
        time.addView(clock, wrap());
        TextView date = Buzz.date(ctx, 22, Buzz.TEXT_2, R.string.font_light);
        Buzz.shadow(date);
        LinearLayout.LayoutParams dl = wrap();
        dl.setMarginStart(dp(6));
        time.addView(date, dl);
        chip = new WeatherChip(ctx, 18, Buzz.TEXT_2, null, true);
        chip.shadow();
        chip.setId(View.generateViewId());
        chip.setOnClickListener(v -> host.openForecast(Buzz.HONEY));
        LinearLayout.LayoutParams cl = wrap();
        cl.topMargin = dp(8);
        time.addView(chip, cl);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        tl.setMarginStart(dp(58));
        tl.bottomMargin = dp(44);
        root.addView(time, tl);

        // Top right: two small words.
        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        View inputs = link(R.string.inputs);
        View settings = link(R.string.settings);
        links = new View[]{inputs, settings};
        top.addView(inputs);
        top.addView(settings);
        inputs.setOnClickListener(v -> host.openInputs(v));
        settings.setOnClickListener(v -> host.openSettings());
        settings.setOnLongClickListener(v -> {
            host.openLauncherSettings();
            return true;
        });
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        topLp.topMargin = dp(30);
        topLp.setMarginEnd(dp(46));
        root.addView(top, topLp);

        // Right: the apps as a column of names.
        scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(true);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(dp(20), dp(12), 0, dp(12));
        scroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(dp(440), ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END);
        sl.topMargin = dp(84);
        sl.bottomMargin = dp(40);
        sl.setMarginEnd(dp(58));
        root.addView(scroll, sl);
    }

    private View link(int label) {
        TextView t = Buzz.text(ctx, 14, Buzz.TEXT_2, R.string.font_medium);
        t.setId(View.generateViewId());
        t.setText(label);
        t.setAllCaps(true);
        t.setLetterSpacing(0.22f);
        t.setFocusable(true);
        t.setClickable(true);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        t.setBackground(Buzz.states(Buzz.round(0, dp(18), 0, 0), Buzz.round(0x33FFFFFF, dp(18), 0, 0)));
        Buzz.shadow(t);
        Buzz.lift(t, 1.06f, (v, f) -> ((TextView) v).setTextColor(f ? Buzz.TEXT : Buzz.TEXT_2));
        return t;
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
        String focusedKey = lastItem != null ? (String) lastItem.getTag() : null;
        list.removeAllViews();
        items.clear();
        lastItem = null;
        View restore = null;
        for (AppAdapter.Entry e : HomeApps.pick(host, MAX_APPS)) {
            View item = item(e);
            if (e.component.flattenToString().equals(focusedKey)) restore = item;
        }
        item(null);
        View first = restore != null ? restore : items.isEmpty() ? null : items.get(0);
        if (first != null) {
            lastItem = first;
            pointAt(first);
            if (root.findFocus() == null || restore != null) root.post(first::requestFocus);
        }
    }

    /** One line in the column: the name, with the icon fading in before it on focus; null for every app. */
    private View item(AppAdapter.Entry e) {
        LinearLayout row = new LinearLayout(ctx);
        row.setId(View.generateViewId());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(8), dp(4), dp(4), dp(4));
        ImageView icon = new ImageView(ctx);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImageDrawable(e == null ? ctx.getDrawable(R.drawable.ic_apps) : Buzz.iconArt(e));
        icon.setAlpha(0f);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(30), dp(30));
        il.setMarginEnd(dp(14));
        row.addView(icon, il);
        TextView name = Buzz.text(ctx, 30, Buzz.TEXT, R.string.font_light);
        String label = e == null ? ctx.getString(R.string.all_apps_short) : e.label;
        name.setText(label);
        name.setAlpha(DIM);
        Buzz.shadow(name);
        row.addView(name, wrap());
        View dot = new View(ctx);
        dot.setBackground(Buzz.round(Buzz.HONEY, dp(3), 0, 0));
        dot.setAlpha(0f);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(6), dp(6));
        dotLp.setMarginStart(dp(14));
        row.addView(dot, dotLp);
        row.setContentDescription(label);
        row.setTag(e == null ? null : e.component.flattenToString());
        row.setOnClickListener(v -> {
            if (e == null) host.openAllApps();
            else host.launchApp(e);
        });
        Buzz.holdForInfo(row, e, host);
        row.setOnFocusChangeListener((v, focused) -> {
            long ms = Buzz.FOCUS_MS;
            name.animate().alpha(focused ? 1f : DIM).setDuration(ms).start();
            icon.animate().alpha(focused ? 1f : 0f).setDuration(ms).start();
            dot.animate().alpha(focused ? 1f : 0f).setDuration(ms).start();
            v.animate().translationX(focused ? -dp(12) : 0f).setDuration(ms).start();
            name.setTypeface(Buzz.font(ctx, focused ? R.string.font_regular : R.string.font_light));
            if (focused) {
                lastItem = v;
                pointAt(v);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52));
        lp.gravity = Gravity.END;
        list.addView(row, lp);
        items.add(row);
        return row;
    }

    /** Left from the column goes to the weather, and back right returns to the same app. */
    private void pointAt(View item) {
        chip.setNextFocusRightId(item.getId());
        chip.setNextFocusUpId(item.getId());
        for (View l : links) l.setNextFocusDownId(item.getId());
        for (View i : items) i.setNextFocusLeftId(chip.getVisibility() == View.VISIBLE ? chip.getId() : i.getId());
    }

    @Override
    public void onWeather(LiveWeather.Reading reading) {
        chip.set(reading);
        if (lastItem != null) pointAt(lastItem);
    }

    @Override
    public void onWifiChanged(String label) {
    }

    @Override
    public boolean handleBack() {
        View focused = root.findFocus();
        if (focused != null && !items.contains(focused) && lastItem != null) {
            lastItem.requestFocus();
            return true;
        }
        if (!items.isEmpty() && focused != items.get(0)) {
            items.get(0).requestFocus();
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
