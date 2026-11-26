package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Órbita: a large thin clock in the middle and the apps on a gentle arc along the bottom, the focused one at
 * the centre, bigger and upright. Left and Right turn the arc; OK opens. The arc is one focus stop that moves
 * its tiles with view-property animations, so turning it never relayouts anything.
 */
final class OrbitHomeLayout implements CustomHomeLayout {
    private static final int MAX_APPS = 14;
    private static final float STEP_DEG = 13f;
    private static final int VISIBLE = 3;

    private final Context ctx;
    private final HomeStyle style;
    private final FrameLayout root;
    private final FrameLayout dial;
    private final TextView name;
    private final WeatherChip chip;
    private final View btnInputs;
    private final View btnSettings;
    private final List<FrameLayout> tiles = new ArrayList<>();
    private final List<AppAdapter.Entry> entries = new ArrayList<>();
    private Drawable ring;
    private Drawable rest;
    private HomeLayoutHost host;
    private int index;

    OrbitHomeLayout(Context ctx) {
        this(ctx, HomeStyle.load(ctx));
    }

    OrbitHomeLayout(Context ctx, HomeStyle style) {
        this.ctx = ctx;
        this.style = style;
        root = new FrameLayout(ctx);
        root.setClipChildren(false);
        View under = new View(ctx);
        GradientDrawable shade = new GradientDrawable();
        shade.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        shade.setGradientCenter(0.5f, 1.05f);
        shade.setColors(new int[]{0xEB05070C, 0x5905070C, 0x0005070C});
        under.setBackground(shade);
        under.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> shade.setGradientRadius((r - l) * 0.62f));
        root.addView(under, match());
        View top = new View(ctx);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x8005070C, 0x0005070C}));
        root.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200)));

        btnInputs = Buzz.iconButton(ctx, R.drawable.ic_input, R.string.inputs, style.baseAccent());
        btnInputs.setId(View.generateViewId());
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.START);
        il.leftMargin = dp(44);
        il.topMargin = dp(26);
        root.addView(btnInputs, il);
        btnSettings = Buzz.iconButton(ctx, R.drawable.ic_settings, R.string.settings, style.baseAccent());
        btnSettings.setId(View.generateViewId());
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.END);
        sl.rightMargin = dp(44);
        sl.topMargin = dp(26);
        root.addView(btnSettings, sl);
        btnInputs.setOnClickListener(v -> host.openInputs(v));
        btnSettings.setOnClickListener(v -> host.openSettings());
        btnSettings.setOnLongClickListener(v -> {
            host.openLauncherSettings();
            return true;
        });

        LinearLayout center = new LinearLayout(ctx);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        center.setClipChildren(false);
        TextView clock = Buzz.clock(ctx, 104 * style.scale(), Buzz.TEXT, R.string.font_extralight);
        Buzz.shadow(clock);
        center.addView(clock, wrap());
        TextView date = Buzz.date(ctx, 17, Buzz.TEXT, R.string.font_light);
        Buzz.shadow(date);
        LinearLayout.LayoutParams dl = wrap();
        dl.topMargin = dp(4);
        center.addView(date, dl);
        chip = new WeatherChip(ctx, 14, Buzz.TEXT, Buzz.glassPill(ctx, 20, style.baseAccent()), true);
        chip.setId(View.generateViewId());
        chip.setOnClickListener(v -> host.openForecast(style.baseAccent()));
        LinearLayout.LayoutParams cl = wrap();
        cl.topMargin = dp(10);
        center.addView(chip, cl);
        FrameLayout.LayoutParams ctl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        ctl.topMargin = dp(46);
        root.addView(center, ctl);

        dial = new FrameLayout(ctx);
        dial.setId(View.generateViewId());
        dial.setFocusable(true);
        dial.setClipChildren(false);
        dial.setOnKeyListener(this::onDialKey);
        dial.setOnFocusChangeListener((v, f) -> place(true));
        dial.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) place(false);
        });
        root.addView(dial, match());

        LinearLayout foot = new LinearLayout(ctx);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setGravity(Gravity.CENTER_HORIZONTAL);
        name = Buzz.text(ctx, 22, Buzz.TEXT, R.string.font_medium);
        Buzz.shadow(name);
        foot.addView(name, wrap());
        TextView hint = Buzz.text(ctx, 12, Buzz.TEXT_2, R.string.font_medium);
        hint.setAllCaps(true);
        hint.setLetterSpacing(0.2f);
        hint.setText(R.string.orbit_hint);
        LinearLayout.LayoutParams hl = wrap();
        hl.topMargin = dp(4);
        foot.addView(hint, hl);
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fl.bottomMargin = dp(22);
        root.addView(foot, fl);

        chip.setNextFocusDownId(dial.getId());
        btnInputs.setNextFocusDownId(dial.getId());
        btnSettings.setNextFocusDownId(dial.getId());
        dial.setNextFocusUpId(chip.getId());
    }

    private boolean onDialKey(View v, int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (e.getAction() == KeyEvent.ACTION_DOWN) {
                    if (!tiles.isEmpty()) {
                        index = Math.floorMod(index + (code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1), tiles.size());
                        place(true);
                    }
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getAction() == KeyEvent.ACTION_UP && !tiles.isEmpty()) {
                    AppAdapter.Entry app = entries.get(index);
                    if (app == null) host.openAllApps();
                    else host.launchApp(app);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (e.getAction() == KeyEvent.ACTION_DOWN) {
                    View up = chip.getVisibility() == View.VISIBLE ? chip : btnSettings;
                    up.requestFocus();
                }
                return true;
            default:
                return false;
        }
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
        String keep = index < entries.size() && entries.get(index) != null
                ? entries.get(index).component.flattenToString() : null;
        dial.removeAllViews();
        tiles.clear();
        entries.clear();
        float r = dp(18);
        ring = Buzz.round(0, r, dp(3), Buzz.TEXT);
        rest = Buzz.round(0, r, Math.max(1, dp(1)), 0x26FFFFFF);
        index = 0;
        List<AppAdapter.Entry> apps = HomeApps.pickFilled(host, MAX_APPS);
        apps.add(null);
        for (int i = 0; i < apps.size(); i++) {
            AppAdapter.Entry e = apps.get(i);
            FrameLayout t = Buzz.appCard(ctx, e, e == null ? style.baseAccent() : style.accentFor(e), 18, false);
            t.setFocusable(false);
            t.setClickable(false);
            t.setForeground(rest);
            int w = dp(150 * style.scale());
            dial.addView(t, new FrameLayout.LayoutParams(w, Math.round(w * 0.5625f)));
            tiles.add(t);
            entries.add(e);
            if (e != null && e.component.flattenToString().equals(keep)) index = i;
        }
        place(false);
        if (root.findFocus() == null) root.post(dial::requestFocus);
    }

    /** Lays the tiles on the arc around the focused one; animated when the arc turns. */
    private void place(boolean animate) {
        int w = root.getWidth(), h = root.getHeight();
        if (w <= 0 || h <= 0 || tiles.isEmpty()) return;
        float radius = w * 0.75f;
        float cx = w / 2f, cy = h * 2.01f;
        boolean focused = dial.hasFocus();
        int n = tiles.size();
        for (int i = 0; i < n; i++) {
            FrameLayout t = tiles.get(i);
            // Circular: the arc turns for ever, with apps on both sides of the focused one.
            int k = Math.floorMod(i - index + n / 2, n) - n / 2;
            double ang = Math.toRadians(k * STEP_DEG);
            float scale = k == 0 ? 1.3f : Math.max(0.6f, 0.92f - Math.abs(k) * 0.09f);
            float tx = (float) (cx + radius * Math.sin(ang)) - t.getLayoutParams().width / 2f;
            float ty = (float) (cy - radius * Math.cos(ang)) - t.getLayoutParams().height / 2f - dp(14) - (k == 0 ? dp(10) : 0);
            float alpha = Math.abs(k) > VISIBLE ? 0f : k == 0 ? 1f : 1f - Math.abs(k) * 0.2f;
            boolean wrapJump = Math.abs(t.getRotation() - k * STEP_DEG) > STEP_DEG * 1.5f;
            t.setForeground(k == 0 && focused ? ring : rest);
            t.setTranslationZ(k == 0 ? dp(10) : -Math.abs(k));
            if (animate && !wrapJump) {
                t.animate().translationX(tx).translationY(ty).rotation(k * STEP_DEG).scaleX(scale).scaleY(scale)
                        .alpha(alpha).setDuration(240).start();
            } else {
                t.animate().cancel();
                t.setTranslationX(tx);
                t.setTranslationY(ty);
                t.setRotation(k * STEP_DEG);
                t.setScaleX(scale);
                t.setScaleY(scale);
                t.setAlpha(alpha);
            }
        }
        AppAdapter.Entry e = entries.get(index);
        name.setText(e == null ? ctx.getString(R.string.all_apps_short) : e.label);
        dial.setContentDescription(name.getText());
    }

    @Override
    public void onWeather(LiveWeather.Reading reading) {
        chip.set(reading);
    }

    @Override
    public void onWifiChanged(String label) {
    }

    @Override
    public boolean handleBack() {
        if (!dial.hasFocus()) {
            dial.requestFocus();
            return true;
        }
        if (index != 0) {
            index = 0;
            place(true);
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
