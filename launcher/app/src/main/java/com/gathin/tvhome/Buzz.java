package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Locale;

/**
 * BuzzTV's design kit: the colours, type and focus behaviour every Home layout (Aura, Arena, Zen, Sala, Cine) and
 * the forecast panel share, so they read as one product. Views are built in code: no extra layouts or drawables,
 * nothing allocated while idle, and every focus change is a short property animation on the view that moved.
 */
final class Buzz {
    private Buzz() {}

    /** The BuzzTV signature accent: warm honey. */
    static final int HONEY = 0xFFFFC53D;
    static final int TEXT = 0xFFF7F8FA;
    static final int TEXT_2 = 0xCCF7F8FA;
    static final int TEXT_3 = 0x8CF7F8FA;
    /** Translucent card fill over any wallpaper, and its hairline edge. */
    static final int GLASS = 0x99101420;
    static final int GLASS_EDGE = 0x2EFFFFFF;
    static final long FOCUS_MS = 180L;

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static Typeface font(Context c, int fontString) {
        try {
            return UiFont.get(c, fontString);
        } catch (RuntimeException e) {
            return Typeface.DEFAULT;
        }
    }

    static TextView text(Context c, float sp, int color, int fontString) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(font(c, fontString));
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** A soft shadow under text that floats straight on a wallpaper. */
    static void shadow(TextView t) {
        float d = t.getResources().getDisplayMetrics().density;
        t.setShadowLayer(10f * d, 0f, 2f * d, 0x99000000);
    }

    static TextClock clock(Context c, float sp, int color, int fontString) {
        TextClock clock = new TextClock(c);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        clock.setTextColor(color);
        clock.setTypeface(font(c, fontString));
        clock.setIncludeFontPadding(false);
        clock.setFontFeatureSettings("tnum");
        return clock;
    }

    /** "jueves, 26 de noviembre" in the TV's language, as a TextClock so it turns over at midnight by itself. */
    static TextClock date(Context c, float sp, int color, int fontString) {
        TextClock date = new TextClock(c);
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        date.setFormat12Hour(pattern);
        date.setFormat24Hour(pattern);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        date.setTextColor(color);
        date.setTypeface(font(c, fontString));
        date.setIncludeFontPadding(false);
        return date;
    }

    static GradientDrawable round(int fill, float radiusPx, int strokePx, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, stroke);
        return g;
    }

    static StateListDrawable states(Drawable normal, Drawable focused) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, normal);
        return s;
    }

    /** Glass pill: translucent at rest, filled with the accent and ringed in white when focused. */
    static StateListDrawable glassPill(Context c, float radiusDp, int accent) {
        float r = dp(c, radiusDp);
        return states(round(GLASS, r, Math.max(1, dp(c, 1)), GLASS_EDGE),
                round(withAlpha(accent, 0x55), r, dp(c, 2), TEXT));
    }

    /** Scales a view up on focus and back down, raising it above its neighbours. */
    static void lift(View v, float scale, View.OnFocusChangeListener also) {
        v.setOnFocusChangeListener((view, focused) -> {
            float s = focused ? scale : 1f;
            view.animate().scaleX(s).scaleY(s).setDuration(FOCUS_MS).start();
            view.setTranslationZ(focused ? dp(view.getContext(), 8) : 0f);
            if (also != null) also.onFocusChange(view, focused);
        });
    }

    /** A round icon button for the top bar (Inputs, Wi-Fi, Apps, Settings). */
    static ImageView iconButton(Context c, int drawable, int description, int accent) {
        ImageView b = new ImageView(c);
        b.setImageResource(drawable);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setContentDescription(c.getString(description));
        b.setBackground(glassPill(c, 24, accent));
        lift(b, 1.1f, null);
        return b;
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    /** Mixes two ARGB colours, t = 0 gives a and 1 gives b. Plain arithmetic, so it also runs in JVM tests. */
    static int blend(int a, int b, float t) {
        float u = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
            out |= (Math.round(ca + (cb - ca) * u) & 0xFF) << shift;
        }
        return out;
    }

    /**
     * An accent that reads on a dark screen, from any colour: saturation kept between 0.35 and 0.85 and brightness
     * at least 0.8, so a black or white banner still gives a lively, legible tint. Opaque.
     */
    static int readableAccent(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color | 0xFF000000, hsv);
        if (hsv[1] < 0.12f) return HONEY;   // grey, white or black art: the BuzzTV accent instead
        hsv[1] = Math.max(0.35f, Math.min(0.85f, hsv[1]));
        hsv[2] = Math.max(0.8f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    /** The adaptive accent for an app: a vivid colour from its banner or icon, made readable. */
    static int accentOf(AppAdapter.Entry e) {
        if (e == null) return HONEY;
        if (e.ambient == 0) e.ambient = AppAdapter.vividColor(e.banner != null ? e.banner : e.icon);
        return readableAccent(e.ambient);
    }

    /** Art for a card: the app's banner, else its icon, as a private copy that can be resized freely. */
    static Drawable art(AppAdapter.Entry e) {
        Drawable d = e == null ? null : e.banner != null ? e.banner : e.icon;
        if (d == null) return null;
        Drawable.ConstantState s = d.getConstantState();
        return s != null ? s.newDrawable().mutate() : d;
    }

    /** The app's square icon for small spots; its banner, fitted whole, only when it has no icon. */
    static Drawable iconArt(AppAdapter.Entry e) {
        Drawable d = e == null ? null : e.icon != null ? e.icon : e.banner;
        if (d == null) return null;
        Drawable.ConstantState s = d.getConstantState();
        return s != null ? s.newDrawable().mutate() : d;
    }

    /** "Buenos días / tardes / noches" for the hour, like the standard Home's greeting. */
    static int greetingRes(int hour) {
        if (hour < 5) return R.string.good_night;
        if (hour < 12) return R.string.good_morning;
        if (hour < 20) return R.string.good_afternoon;
        return R.string.good_evening;
    }

    /** A view that only fills space in a LinearLayout. */
    static View spacer(Context c) {
        View v = new View(c);
        v.setFocusable(false);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    static void center(TextView t) {
        t.setGravity(Gravity.CENTER);
    }
}
