package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Arena, the game-room Home: a HUD line on top (clock, weather, date), a row of square app tiles, and the focused
 * app's name in huge type under it, over angled neon light that takes on the focused app's colour. The bottom bar
 * keeps what a console corner needs one press away: the HDMI inputs, the whole library and the settings.
 */
final class ArenaHomeLayout implements CustomHomeLayout {
    /** Neon cyan for focus, and violet for the second light. */
    static final int NEON = 0xFF39F3FF;
    static final int VIOLET = 0xFFB14DFF;
    private static final int MAX_APPS = 12;

    private final Context ctx;
    private final FrameLayout root;
    private final Backdrop backdrop;
    private final WeatherChip chip;
    private final TextView title;
    private final TextView subtitle;
    private final HorizontalScrollView scroll;
    private final LinearLayout tiles;
    private final View[] bottom;
    private final List<View> tileViews = new ArrayList<>();
    private HomeLayoutHost host;
    private View lastTile;
    private int accent = NEON;
    private ValueAnimator anim;

    ArenaHomeLayout(Context ctx) {
        this.ctx = ctx;
        root = new FrameLayout(ctx);
        root.setClipChildren(false);
        backdrop = new Backdrop(ctx);
        root.addView(backdrop, match());

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(dp(52), dp(28), dp(52), dp(26));
        root.addView(col, match());

        // HUD: clock | weather .......... date
        LinearLayout hud = new LinearLayout(ctx);
        hud.setOrientation(LinearLayout.HORIZONTAL);
        hud.setGravity(Gravity.CENTER_VERTICAL);
        hud.setClipChildren(false);
        col.addView(hud, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView clock = Buzz.clock(ctx, 30, Buzz.TEXT, R.string.font_medium);
        hud.addView(clock);
        View bar = new View(ctx);
        bar.setBackgroundColor(Buzz.withAlpha(NEON, 0x99));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(2), dp(22));
        barLp.setMargins(dp(16), 0, dp(12), 0);
        hud.addView(bar, barLp);
        chip = new WeatherChip(ctx, 15, Buzz.TEXT, Buzz.glassPill(ctx, 8, NEON), false);
        chip.setId(View.generateViewId());
        chip.setOnClickListener(v -> host.openForecast(NEON));
        hud.addView(chip, wrap());
        hud.addView(Buzz.spacer(ctx), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView date = Buzz.date(ctx, 15, Buzz.TEXT_2, R.string.font_medium);
        date.setAllCaps(true);
        date.setLetterSpacing(0.12f);
        hud.addView(date, wrap());

        // Tiles.
        scroll = new HorizontalScrollView(ctx);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        tiles = new LinearLayout(ctx);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.setGravity(Gravity.CENTER_VERTICAL);
        tiles.setClipChildren(false);
        tiles.setClipToPadding(false);
        tiles.setPadding(dp(14), dp(18), dp(60), dp(18));
        scroll.addView(tiles, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(26);
        sp.setMarginStart(-dp(14));
        col.addView(scroll, sp);

        // The focused app, big.
        title = Buzz.text(ctx, 54, Buzz.TEXT, R.string.font_medium);
        title.setAllCaps(true);
        title.setLetterSpacing(0.03f);
        Buzz.shadow(title);
        LinearLayout.LayoutParams tp = wrap();
        tp.topMargin = dp(10);
        col.addView(title, tp);
        LinearLayout hints = new LinearLayout(ctx);
        hints.setOrientation(LinearLayout.HORIZONTAL);
        hints.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hlp = wrap();
        hlp.topMargin = dp(12);
        col.addView(hints, hlp);
        hints.addView(keycap("OK"));
        subtitle = Buzz.text(ctx, 15, Buzz.TEXT, R.string.font_medium);
        subtitle.setAllCaps(true);
        subtitle.setLetterSpacing(0.14f);
        subtitle.setText(R.string.arena_play);
        LinearLayout.LayoutParams stp = wrap();
        stp.setMarginStart(dp(10));
        stp.setMarginEnd(dp(26));
        hints.addView(subtitle, stp);
        hints.addView(keycap("◀ ▶"));
        TextView nav = Buzz.text(ctx, 15, Buzz.TEXT_2, R.string.font_medium);
        nav.setAllCaps(true);
        nav.setLetterSpacing(0.14f);
        nav.setText(R.string.arena_browse);
        LinearLayout.LayoutParams nlp = wrap();
        nlp.setMarginStart(dp(10));
        hints.addView(nav, nlp);

        col.addView(Buzz.spacer(ctx), new LinearLayout.LayoutParams(1, 0, 1f));

        // Bottom bar: inputs (consoles live on HDMI), library, settings.
        LinearLayout bar2 = new LinearLayout(ctx);
        bar2.setOrientation(LinearLayout.HORIZONTAL);
        bar2.setClipChildren(false);
        col.addView(bar2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        View inputs = action(R.drawable.ic_input, R.string.arena_inputs);
        View library = action(R.drawable.ic_apps, R.string.arena_library);
        View settings = action(R.drawable.ic_settings, R.string.settings);
        bottom = new View[]{inputs, library, settings};
        for (View b : bottom) bar2.addView(b);
        inputs.setOnClickListener(v -> host.openInputs(v));
        library.setOnClickListener(v -> host.openAllApps());
        settings.setOnClickListener(v -> host.openSettings());
        settings.setOnLongClickListener(v -> {
            host.openLauncherSettings();
            return true;
        });
    }

    private TextView keycap(String text) {
        TextView k = Buzz.text(ctx, 12, NEON, R.string.font_medium);
        k.setText(text);
        k.setPadding(dp(8), dp(4), dp(8), dp(4));
        k.setBackground(Buzz.round(0x1A39F3FF, dp(5), Math.max(1, dp(1)), Buzz.withAlpha(NEON, 0xB3)));
        return k;
    }

    /** An angular pill for the bottom bar: icon and caps label, neon edge when focused. */
    private View action(int icon, int label) {
        LinearLayout b = new LinearLayout(ctx);
        b.setId(View.generateViewId());
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setFocusable(true);
        b.setClickable(true);
        b.setPadding(dp(16), 0, dp(20), 0);
        float r = dp(8);
        b.setBackground(Buzz.states(Buzz.round(0xB30A0E16, r, Math.max(1, dp(1)), 0x33FFFFFF),
                Buzz.round(0x3339F3FF, r, dp(2), NEON)));
        ImageView i = new ImageView(ctx);
        i.setImageResource(icon);
        b.addView(i, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView t = Buzz.text(ctx, 14, Buzz.TEXT, R.string.font_medium);
        t.setAllCaps(true);
        t.setLetterSpacing(0.12f);
        t.setText(label);
        LinearLayout.LayoutParams tl = wrap();
        tl.setMarginStart(dp(10));
        b.addView(t, tl);
        b.setContentDescription(ctx.getString(label));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        lp.setMarginEnd(dp(14));
        b.setLayoutParams(lp);
        Buzz.lift(b, 1.05f, null);
        return b;
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
        tiles.removeAllViews();
        tileViews.clear();
        lastTile = null;
        View restore = null;
        for (AppAdapter.Entry e : HomeApps.pickFilled(host, MAX_APPS)) {
            View t = tile(e);
            if (e.component.flattenToString().equals(focusedKey)) restore = t;
        }
        tile(null);
        View first = restore != null ? restore : tileViews.isEmpty() ? null : tileViews.get(0);
        if (first != null) {
            lastTile = first;
            pointAt(first);
            show(first);
            if (root.findFocus() == null || restore != null) root.post(first::requestFocus);
        }
    }

    private View tile(AppAdapter.Entry e) {
        float r = dp(18);
        FrameLayout t = new Buzz.RoundFrame(ctx, r);
        t.setId(View.generateViewId());
        t.setFocusable(true);
        t.setClickable(true);
        int tint = e == null ? VIOLET : Buzz.accentOf(e);
        t.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                Buzz.blend(tint, 0xFF080B12, 0.62f), Buzz.blend(tint, 0xFF080B12, 0.88f)}));
        ImageView icon = new ImageView(ctx);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImageDrawable(e == null ? ctx.getDrawable(R.drawable.ic_apps) : Buzz.iconArt(e));
        int size = e == null ? 40 : 64;
        t.addView(icon, new FrameLayout.LayoutParams(dp(size), dp(size), Gravity.CENTER));
        t.setForeground(Buzz.states(Buzz.round(0, r, Math.max(1, dp(1)), 0x26FFFFFF),
                Buzz.round(0, r, dp(3), NEON)));
        String label = e == null ? ctx.getString(R.string.all_apps_short) : e.label;
        t.setContentDescription(label);
        t.setTag(e == null ? null : e.component.flattenToString());
        t.setTag(R.id.homeLabel, label);
        t.setTag(R.id.homeAccent, tint);
        t.setOnClickListener(v -> {
            if (e == null) host.openAllApps();
            else host.launchApp(e);
        });
        Buzz.holdForInfo(t, e, host);
        Buzz.lift(t, 1.18f, (v, focused) -> {
            if (!focused) return;
            lastTile = v;
            pointAt(v);
            show(v);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(104), dp(104));
        lp.setMarginEnd(dp(22));
        tiles.addView(t, lp);
        tileViews.add(t);
        return t;
    }

    private void show(View tile) {
        Object label = tile.getTag(R.id.homeLabel);
        title.setText(label == null ? "" : label.toString());
        Object tint = tile.getTag(R.id.homeAccent);
        int target = tint instanceof Integer ? (Integer) tint : NEON;
        if (anim != null) anim.cancel();
        int from = accent;
        accent = target;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(320);
        anim.addUpdateListener(a -> backdrop.setLight(Buzz.blend(from, target, (float) a.getAnimatedValue())));
        anim.start();
    }

    /** Up from the bottom bar and down from the HUD return to the tile last in focus. */
    private void pointAt(View tile) {
        chip.setNextFocusDownId(tile.getId());
        for (View b : bottom) b.setNextFocusUpId(tile.getId());
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
        View focused = root.findFocus();
        if (focused != null && !tileViews.contains(focused) && lastTile != null) {
            lastTile.requestFocus();
            return true;
        }
        if (!tileViews.isEmpty() && focused != tileViews.get(0)) {
            scroll.smoothScrollTo(0, 0);
            tileViews.get(0).requestFocus();
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

    /**
     * The room's light: a dark wash from the left for the text, and three angled beams on the right, the first in
     * the focused app's colour, the others in neon violet and cyan. Drawn only when the colour changes.
     */
    static final class Backdrop extends View {
        private final Paint wash = new Paint();
        private final Paint beam = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private int light = NEON;

        Backdrop(Context c) {
            super(c);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setLight(int color) {
            light = color;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            wash.setShader(new LinearGradient(0, 0, w, 0, new int[]{0xF2060810, 0xB3060810, 0x40060810},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            canvas.drawRect(0, 0, w, h, wash);
            drawBeam(canvas, w, h, 0.60f, 0.16f, light, 0x59);
            drawBeam(canvas, w, h, 0.80f, 0.07f, VIOLET, 0x40);
            drawBeam(canvas, w, h, 0.90f, 0.035f, NEON, 0x4D);
            // A floor line under the bottom bar.
            beam.setShader(null);
            beam.setColor(Buzz.withAlpha(light, 0x66));
            canvas.drawRect(0, h - h * 0.012f, w, h, beam);
        }

        /** A slanted band from the top edge at x0 (fraction of width) down to the bottom, fading downwards. */
        private void drawBeam(Canvas c, float w, float h, float x0, float width, int color, int alpha) {
            float x = w * x0, bw = w * width, slant = h * 0.42f;
            path.reset();
            path.moveTo(x, 0);
            path.lineTo(x + bw, 0);
            path.lineTo(x + bw - slant, h);
            path.lineTo(x - slant, h);
            path.close();
            beam.setShader(new LinearGradient(0, 0, 0, h, Buzz.withAlpha(color, alpha), Buzz.withAlpha(color, 0),
                    Shader.TileMode.CLAMP));
            c.drawPath(path, beam);
        }
    }
}
