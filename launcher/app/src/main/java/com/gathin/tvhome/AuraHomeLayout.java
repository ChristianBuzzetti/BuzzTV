package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
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
import java.util.Calendar;
import java.util.List;

/**
 * Aura, BuzzTV's own Home: a large, quiet clock over the wallpaper and one row of app cards. The screen takes
 * its colour from the app in focus: a soft aura rises behind the row in that app's colour and follows the focus
 * along it, so moving through the apps tints the room. One GradientDrawable, recoloured over 350 ms on a focus
 * change and still otherwise; nothing runs while Home sits idle.
 */
final class AuraHomeLayout implements CustomHomeLayout {
    private static final int MAX_APPS = 14;
    private static final float CARD_SCALE = 1.14f;

    private final Context ctx;
    private final HomeStyle style;
    private final FrameLayout root;
    private final GradientDrawable aura;
    private final TextView greeting;
    private final WeatherChip chip;
    private final View[] topButtons;
    private final ImageView btnWifi;
    private final TextView focusName;
    private final TextView focusHint;
    private final HorizontalScrollView scroll;
    private final LinearLayout row;
    private final List<View> cards = new ArrayList<>();

    private HomeLayoutHost host;
    private int accent = Buzz.HONEY;
    private float auraX = 0.2f;
    private ValueAnimator auraAnim;
    private View lastCard;

    AuraHomeLayout(Context ctx) {
        this(ctx, HomeStyle.load(ctx));
    }

    AuraHomeLayout(Context ctx, HomeStyle style) {
        this.ctx = ctx;
        this.style = style;
        this.accent = style.baseAccent();
        root = new FrameLayout(ctx);
        root.setClipChildren(false);

        // Legibility: darker at the top (clock) and much darker at the bottom (cards and names).
        View scrim = new View(ctx);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99060810, 0x14060810, 0x33060810, 0xE6060810}));
        root.addView(scrim, match());

        aura = new GradientDrawable();
        aura.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        View auraView = new View(ctx);
        auraView.setBackground(aura);
        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(auraView, ap);
        auraView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> paintAura(accent));

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(dp(56), dp(34), dp(56), dp(26));
        root.addView(col, match());

        // Top: greeting, clock, date and weather on the left; round buttons on the right.
        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setClipChildren(false);
        col.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout left = new LinearLayout(ctx);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setClipChildren(false);
        top.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        greeting = Buzz.text(ctx, 15, Buzz.TEXT_2, R.string.font_medium);
        greeting.setAllCaps(true);
        greeting.setLetterSpacing(0.2f);
        Buzz.shadow(greeting);
        left.addView(greeting);
        TextView clock = Buzz.clock(ctx, 92, Buzz.TEXT, R.string.font_extralight);
        Buzz.shadow(clock);
        LinearLayout.LayoutParams cp = wrap();
        cp.topMargin = dp(4);
        left.addView(clock, cp);
        TextView date = Buzz.date(ctx, 19, Buzz.TEXT, R.string.font_light);
        Buzz.shadow(date);
        LinearLayout.LayoutParams dateLp = wrap();
        dateLp.topMargin = dp(2);
        left.addView(date, dateLp);
        chip = new WeatherChip(ctx, 16, Buzz.TEXT, Buzz.glassPill(ctx, 22, Buzz.HONEY), true);
        chip.setId(View.generateViewId());
        LinearLayout.LayoutParams chp = wrap();
        chp.topMargin = dp(14);
        left.addView(chip, chp);

        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        top.addView(buttons, wrap());
        ImageView btnInputs = Buzz.iconButton(ctx, R.drawable.ic_input, R.string.inputs, Buzz.HONEY);
        btnWifi = Buzz.iconButton(ctx, R.drawable.ic_wifi, R.string.wifi, Buzz.HONEY);
        ImageView btnApps = Buzz.iconButton(ctx, R.drawable.ic_apps, R.string.app_library, Buzz.HONEY);
        ImageView btnSettings = Buzz.iconButton(ctx, R.drawable.ic_settings, R.string.settings, Buzz.HONEY);
        topButtons = new View[]{btnInputs, btnWifi, btnApps, btnSettings};
        for (View b : topButtons) {
            b.setId(View.generateViewId());
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(48), dp(48));
            bp.setMarginStart(dp(12));
            buttons.addView(b, bp);
        }
        btnInputs.setOnClickListener(v -> host.openInputs(v));
        btnWifi.setOnClickListener(v -> host.openWifi());
        btnApps.setOnClickListener(v -> host.openAllApps());
        btnSettings.setOnClickListener(v -> host.openSettings());
        btnSettings.setOnLongClickListener(v -> {
            host.openLauncherSettings();
            return true;
        });
        chip.setOnClickListener(v -> host.openForecast(accent));

        col.addView(Buzz.spacer(ctx), new LinearLayout.LayoutParams(1, 0, 1f));

        // The focused app's name, in large type right above the row.
        focusName = Buzz.text(ctx, 30, Buzz.TEXT, R.string.font_medium);
        Buzz.shadow(focusName);
        col.addView(focusName, wrap());
        focusHint = Buzz.text(ctx, 13, Buzz.TEXT_3, R.string.font_regular);
        focusHint.setAllCaps(true);
        focusHint.setLetterSpacing(0.18f);
        focusHint.setText(R.string.aura_hint);
        LinearLayout.LayoutParams hp = wrap();
        hp.topMargin = dp(4);
        col.addView(focusHint, hp);

        scroll = new HorizontalScrollView(ctx);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(false);
        row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // Room for the focused card to grow without being cut at the ends.
        row.setPadding(dp(14), dp(16), dp(40), dp(16));
        scroll.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(4);
        sp.setMarginStart(-dp(14));
        col.addView(scroll, sp);
    }

    @Override
    public View rootView() {
        return root;
    }

    @Override
    public void bind(HomeLayoutHost host) {
        this.host = host;
        onShown();
        onWifiChanged(host.wifiLabel());
        onAppsChanged();
    }

    @Override
    public void onShown() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String base = ctx.getString(Buzz.greetingRes(hour));
        String name = host == null ? "" : host.greetingName();
        greeting.setText(name.isEmpty() ? base : ctx.getString(R.string.greeting_with_name, base, name));
        greeting.setVisibility(style.greeting ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onAppsChanged() {
        if (host == null) return;
        String focusedKey = lastCard != null ? (String) lastCard.getTag() : null;
        row.removeAllViews();
        cards.clear();
        lastCard = null;
        View restore = null;
        for (AppAdapter.Entry e : HomeApps.pickFilled(host, MAX_APPS)) {
            View card = appCard(e);
            if (e.component.flattenToString().equals(focusedKey)) restore = card;
        }
        appCard(null);   // every app, at the end of the row
        View first = restore != null ? restore : cards.isEmpty() ? null : cards.get(0);
        if (first != null) {
            lastCard = first;
            pointTopAt(first);
            focusName.setText(first.getContentDescription());
            Object tint = first.getTag(R.id.homeAccent);
            if (tint instanceof Integer) fadeAuraTo((Integer) tint);
            if (root.findFocus() == null || restore != null) root.post(first::requestFocus);
        }
    }

    /** One card in the row: the app's banner, or its icon on a tinted glass; null makes the All apps card. */
    private View appCard(AppAdapter.Entry e) {
        FrameLayout card = new Buzz.RoundFrame(ctx, dp(14));
        int cardAccent = e == null ? style.baseAccent() : style.accentFor(e);
        String label = e == null ? ctx.getString(R.string.all_apps_short) : e.label;
        if (e != null && e.banner != null) {
            ImageView art = new ImageView(ctx);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setImageDrawable(Buzz.art(e));
            card.addView(art, match());
        } else {
            card.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                    Buzz.blend(cardAccent, 0xFF0A0D14, 0.55f), Buzz.blend(cardAccent, 0xFF0A0D14, 0.82f)}));
            ImageView icon = new ImageView(ctx);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Drawable d = e == null ? ctx.getDrawable(R.drawable.ic_apps) : Buzz.art(e);
            icon.setImageDrawable(d);
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(44), dp(44),
                    Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            ip.topMargin = dp(16);
            card.addView(icon, ip);
            TextView name = Buzz.text(ctx, 13, Buzz.TEXT, R.string.font_medium);
            name.setText(label);
            name.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            np.bottomMargin = dp(12);
            np.leftMargin = np.rightMargin = dp(8);
            card.addView(name, np);
        }
        float r = dp(14);
        card.setForeground(Buzz.states(Buzz.round(0, r, Math.max(1, dp(1)), 0x1FFFFFFF),
                Buzz.round(0, r, dp(3), Buzz.TEXT)));
        card.setContentDescription(label);
        card.setTag(e == null ? null : e.component.flattenToString());
        card.setTag(R.id.homeAccent, cardAccent);
        card.setOnClickListener(v -> {
            if (e == null) host.openAllApps();
            else host.launchApp(e);
        });
        Buzz.holdForInfo(card, e, host);
        Buzz.lift(card, CARD_SCALE, (v, focused) -> {
            if (!focused) return;
            lastCard = v;
            pointTopAt(v);
            focusName.setText(label);
            v.post(() -> {
                int w = root.getWidth();
                if (w > 0) {
                    int[] at = new int[2];
                    v.getLocationInWindow(at);
                    auraX = Math.max(0.1f, Math.min(0.9f, (at[0] + v.getWidth() / 2f) / w));
                }
                fadeAuraTo(cardAccent);
            });
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(176 * style.scale()), dp(99 * style.scale()));
        lp.setMarginEnd(dp(18));
        row.addView(card, lp);
        cards.add(card);
        return card;
    }

    /** Down from the top bar returns to the card last in focus, not whichever card sits under it. */
    private void pointTopAt(View card) {
        chip.setNextFocusDownId(card.getId());
        for (View b : topButtons) b.setNextFocusDownId(card.getId());
    }

    private void fadeAuraTo(int target) {
        if (auraAnim != null) auraAnim.cancel();
        int from = accent;
        accent = target;
        auraAnim = ValueAnimator.ofFloat(0f, 1f);
        auraAnim.setDuration(350);
        auraAnim.addUpdateListener(a -> paintAura(Buzz.blend(from, target, (float) a.getAnimatedValue())));
        auraAnim.start();
    }

    private void paintAura(int color) {
        int w = root.getWidth(), h = root.getHeight();
        if (w <= 0 || h <= 0) return;
        aura.setGradientCenter(auraX, 1.0f);
        aura.setGradientRadius(w * 0.62f);
        aura.setColors(new int[]{Buzz.withAlpha(color, 0xA6), Buzz.withAlpha(color, 0x40), Buzz.withAlpha(color, 0)});
    }

    @Override
    public void onWeather(LiveWeather.Reading reading) {
        chip.set(reading);
    }

    @Override
    public void onWifiChanged(String label) {
        btnWifi.setContentDescription(label == null || label.isEmpty() ? ctx.getString(R.string.wifi) : label);
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
