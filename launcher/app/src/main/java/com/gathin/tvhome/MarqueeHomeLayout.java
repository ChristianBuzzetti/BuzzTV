package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Marquesina, BuzzTV's default Home: the app in focus takes the stage. Its name stands large on the left with
 * Open and App info, a light in its colour rises behind it, and two rows sit below: the favourites, and the
 * TV's inputs (HDMI, the inputs menu, every app). The stage changes with a short cross-fade as the focus moves.
 */
final class MarqueeHomeLayout implements CustomHomeLayout {
    private static final int MAX_APPS = 14;

    private final Context ctx;
    private final HomeStyle style;
    private final FrameLayout root;
    private final GradientDrawable glow;
    private final TextView kicker;
    private final TextView title;
    private final TextView open;
    private final TextView info;
    private final WeatherChip chip;
    private final HorizontalScrollView scroll;
    private final LinearLayout row;
    private final LinearLayout inputs;
    private final List<View> cards = new ArrayList<>();
    private HomeLayoutHost host;
    private View lastCard;
    private AppAdapter.Entry stageApp;
    private int accent;
    private ValueAnimator anim;

    MarqueeHomeLayout(Context ctx) {
        this(ctx, HomeStyle.load(ctx));
    }

    MarqueeHomeLayout(Context ctx, HomeStyle style) {
        this.ctx = ctx;
        this.style = style;
        this.accent = style.baseAccent();
        root = new FrameLayout(ctx);
        root.setClipChildren(false);

        View side = new View(ctx);
        side.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xEB05070C, 0x8C05070C, 0x0005070C}));
        root.addView(side, new FrameLayout.LayoutParams(dp(760), ViewGroup.LayoutParams.MATCH_PARENT));
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        View glowView = new View(ctx);
        glowView.setBackground(glow);
        root.addView(glowView, match());
        glowView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> paintGlow(accent));
        View floor = new View(ctx);
        floor.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x0005070C, 0xB305070C, 0xF205070C}));
        root.addView(floor, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300), Gravity.BOTTOM));

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(dp(56), dp(26), dp(56), dp(22));
        root.addView(col, match());

        // Top bar: wordmark ...... weather, clock.
        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setClipChildren(false);
        col.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView mark = Buzz.text(ctx, 18, Buzz.TEXT, R.string.font_medium);
        mark.setText(Buzz.wordmark("buzz", accent));
        top.addView(mark);
        top.addView(Buzz.spacer(ctx), new LinearLayout.LayoutParams(0, 1, 1f));
        chip = new WeatherChip(ctx, 15, Buzz.TEXT, Buzz.glassPill(ctx, 20, Buzz.HONEY), false);
        chip.setId(View.generateViewId());
        chip.setOnClickListener(v -> host.openForecast(accent));
        LinearLayout.LayoutParams cp = wrap();
        cp.setMarginEnd(dp(18));
        top.addView(chip, cp);
        TextView clock = Buzz.clock(ctx, 22, Buzz.TEXT, R.string.font_light);
        top.addView(clock);

        // The stage.
        kicker = Buzz.text(ctx, 13, accent, R.string.font_medium);
        kicker.setAllCaps(true);
        kicker.setLetterSpacing(0.24f);
        LinearLayout.LayoutParams kp = wrap();
        kp.topMargin = dp(36 * style.scale());
        col.addView(kicker, kp);
        title = Buzz.text(ctx, 58 * style.scale(), Buzz.TEXT, R.string.font_medium);
        Buzz.shadow(title);
        LinearLayout.LayoutParams tp = wrap();
        tp.topMargin = dp(6);
        col.addView(title, tp);
        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setClipChildren(false);
        LinearLayout.LayoutParams ap = wrap();
        ap.topMargin = dp(16 * style.scale());
        col.addView(actions, ap);
        open = Buzz.pill(ctx, R.string.marquee_open, true);
        open.setId(View.generateViewId());
        actions.addView(open, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        info = Buzz.pill(ctx, R.string.marquee_info, false);
        info.setId(View.generateViewId());
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        ip.setMarginStart(dp(12));
        actions.addView(info, ip);
        open.setOnClickListener(v -> {
            if (stageApp != null) host.launchApp(stageApp);
            else host.openAllApps();
        });
        info.setOnClickListener(v -> {
            if (stageApp != null) host.openAppInfo(stageApp);
            else host.openLauncherSettings();
        });

        col.addView(Buzz.spacer(ctx), new LinearLayout.LayoutParams(1, 0, 1f));

        TextView favLabel = Buzz.text(ctx, 15, Buzz.TEXT_2, R.string.font_medium);
        favLabel.setText(R.string.marquee_favorites);
        col.addView(favLabel, wrap());
        scroll = new HorizontalScrollView(ctx);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(dp(14), dp(12), dp(60), dp(12));
        scroll.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMarginStart(-dp(14));
        col.addView(scroll, sp);

        inputs = new LinearLayout(ctx);
        inputs.setOrientation(LinearLayout.HORIZONTAL);
        inputs.setGravity(Gravity.CENTER_VERTICAL);
        inputs.setClipChildren(false);
        LinearLayout.LayoutParams inp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inp.topMargin = dp(4);
        col.addView(inputs, inp);
        if (!style.inputsRow) inputs.setVisibility(View.GONE);
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
        String focusedKey = lastCard != null ? (String) lastCard.getTag() : null;
        row.removeAllViews();
        cards.clear();
        lastCard = null;
        View restore = null;
        View first = null;
        for (AppAdapter.Entry e : HomeApps.pickFilled(host, MAX_APPS)) {
            View c = card(e);
            if (first == null) first = c;
            if (e.component.flattenToString().equals(focusedKey)) restore = c;
        }
        card(null);
        buildInputs();
        View target = restore != null ? restore : first != null ? first : cards.get(0);
        lastCard = target;
        stage((AppAdapter.Entry) target.getTag(R.id.homeEntry), target, false);
        if (root.findFocus() == null || restore != null) root.post(target::requestFocus);
    }

    private View card(AppAdapter.Entry e) {
        int a = e == null ? style.baseAccent() : style.accentFor(e);
        View card = Buzz.appCard(ctx, e, a, 14, false);
        card.setTag(R.id.homeEntry, e);
        card.setOnClickListener(v -> {
            if (e == null) host.openAllApps();
            else host.launchApp(e);
        });
        Buzz.lift(card, 1.16f, (v, focused) -> {
            if (!focused) return;
            lastCard = v;
            stage(e, v, true);
        });
        float s = style.scale();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(172 * s), dp(97 * s));
        lp.setMarginEnd(dp(16 * s));
        row.addView(card, lp);
        cards.add(card);
        card.setNextFocusUpId(open.getId());
        return card;
    }

    /** Puts an app on the stage: its name, its colour, and where Up from the actions returns. */
    private void stage(AppAdapter.Entry e, View card, boolean fade) {
        stageApp = e;
        int target = e == null ? style.baseAccent() : style.accentFor(e);
        String name = e == null ? ctx.getString(R.string.all_apps_short) : e.label;
        kicker.setText(e == null ? R.string.marquee_library : R.string.marquee_favorite);
        open.setNextFocusDownId(card.getId());
        info.setNextFocusDownId(card.getId());
        chip.setNextFocusDownId(open.getId());
        if (fade && !name.contentEquals(title.getText())) {
            title.animate().cancel();
            title.setAlpha(0.25f);
            title.setTranslationX(dp(10));
            title.setText(name);
            title.animate().alpha(1f).translationX(0f).setDuration(220).start();
        } else {
            title.setText(name);
        }
        fadeTo(target);
    }

    private void fadeTo(int target) {
        if (anim != null) anim.cancel();
        int from = accent;
        accent = target;
        kicker.setTextColor(target);
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(320);
        anim.addUpdateListener(a -> paintGlow(Buzz.blend(from, target, (float) a.getAnimatedValue())));
        anim.start();
    }

    private void paintGlow(int color) {
        int w = root.getWidth();
        if (w <= 0) return;
        glow.setGradientCenter(0.22f, 0.42f);
        glow.setGradientRadius(w * 0.42f);
        glow.setColors(new int[]{Buzz.withAlpha(color, 0x59), Buzz.withAlpha(color, 0x1A), Buzz.withAlpha(color, 0)});
    }

    /** The inputs row: up to three HDMI inputs with a dot for "something is connected", then the menus. */
    private void buildInputs() {
        inputs.removeAllViews();
        if (!style.inputsRow) return;
        TextView label = Buzz.text(ctx, 15, Buzz.TEXT_2, R.string.font_medium);
        label.setText(R.string.inputs);
        LinearLayout.LayoutParams lp = wrap();
        lp.setMarginEnd(dp(14));
        inputs.addView(label, lp);
        for (HomeLayoutHost.HomeInput in : host.inputs(3)) {
            View c = inputChip(in.label, in.connected ? 0xFF3DD68C : 0x59FFFFFF);
            c.setOnClickListener(v -> host.openInput(in));
            inputs.addView(c);
        }
        View all = inputChip(ctx.getString(R.string.marquee_all_inputs), Buzz.withAlpha(style.baseAccent(), 0xFF));
        all.setOnClickListener(v -> host.openInputs(v));
        inputs.addView(all);
        for (int i = 0; i < inputs.getChildCount(); i++) {
            View v = inputs.getChildAt(i);
            if (v.isFocusable() && !cards.isEmpty()) v.setNextFocusUpId(lastCard != null ? lastCard.getId() : cards.get(0).getId());
        }
    }

    private View inputChip(String text, int dot) {
        LinearLayout c = new LinearLayout(ctx);
        c.setId(View.generateViewId());
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setFocusable(true);
        c.setClickable(true);
        c.setPadding(dp(14), 0, dp(16), 0);
        float r = dp(12);
        c.setBackground(Buzz.states(Buzz.round(0xB30C1018, r, Math.max(1, dp(1)), 0x24FFFFFF),
                Buzz.round(0x40FFFFFF, r, dp(2), Buzz.TEXT)));
        View d = new View(ctx);
        d.setBackground(Buzz.round(dot, dp(4), 0, 0));
        c.addView(d, new LinearLayout.LayoutParams(dp(8), dp(8)));
        TextView t = Buzz.text(ctx, 15, Buzz.TEXT, R.string.font_regular);
        t.setText(text);
        LinearLayout.LayoutParams tl = wrap();
        tl.setMarginStart(dp(10));
        c.addView(t, tl);
        c.setContentDescription(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        lp.setMarginEnd(dp(12));
        c.setLayoutParams(lp);
        Buzz.lift(c, 1.05f, null);
        return c;
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
        if (focused != null && !cards.contains(focused) && lastCard != null) {
            lastCard.requestFocus();
            return true;
        }
        if (!cards.isEmpty() && focused != cards.get(0)) {
            scroll.smoothScrollTo(0, 0);
            cards.get(0).requestFocus();
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
