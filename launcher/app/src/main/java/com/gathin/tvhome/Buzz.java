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
import android.graphics.Outline;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
            if (focused) unclip(view);
            float s = focused ? scale : 1f;
            view.animate().scaleX(s).scaleY(s).setDuration(FOCUS_MS).start();
            view.setTranslationZ(focused ? dp(view.getContext(), 8) : 0f);
            if (also != null) also.onFocusChange(view, focused);
        });
    }

    /**
     * Lets a view that grows on focus draw past its parents' edges, so its ring is never cut. Every container up
     * to the nearest scrolling one stops clipping; the scrolling one keeps its content inside its own bounds
     * (its parent clips it), so scrolled-away rows never paint over the rest of the screen. A scroll's content
     * carries padding for the growth.
     */
    static void unclip(View v) {
        android.view.ViewParent p = v.getParent();
        while (p instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) p;
            g.setClipChildren(false);
            if (g instanceof android.widget.ScrollView || g instanceof android.widget.HorizontalScrollView) {
                g.setClipToPadding(false);
                if (g.getParent() instanceof ViewGroup) ((ViewGroup) g.getParent()).setClipChildren(true);
                return;
            }
            p = g.getParent();
        }
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


    /** Clips a view to a rounded rectangle (hardware-accelerated, so the clip is free while it animates). */
    static void roundClip(View v, float radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        v.setClipToOutline(true);
    }

    /**
     * An app card: the banner edge to edge, or the icon on a glass tinted with the app's colour and its name
     * under it; {@code e == null} makes the All apps card. White ring when focused. Not yet listening to focus:
     * the layout adds its own lift.
     */
    static FrameLayout appCard(Context c, AppAdapter.Entry e, int accent, float radiusDp, boolean preferIcon) {
        FrameLayout card = new FrameLayout(c);
        card.setId(View.generateViewId());
        card.setFocusable(true);
        card.setClickable(true);
        float r = dp(c, radiusDp);
        roundClip(card, r);
        String label = e == null ? c.getString(R.string.all_apps_short) : e.label;
        if (e != null && e.banner != null && !preferIcon) {
            ImageView art = new ImageView(c);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setImageDrawable(art(e));
            card.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            card.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{
                    blend(accent, 0xFF0A0D14, 0.55f), blend(accent, 0xFF0A0D14, 0.84f)}));
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            ImageView icon = new ImageView(c);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImageDrawable(e == null ? c.getDrawable(R.drawable.ic_apps) : iconArt(e));
            col.addView(icon, new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));
            TextView name = text(c, 13, TEXT, R.string.font_medium);
            name.setText(label);
            name.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            np.topMargin = dp(c, 8);
            np.leftMargin = np.rightMargin = dp(c, 8);
            col.addView(name, np);
            card.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        card.setForeground(states(round(0, r, Math.max(1, dp(c, 1)), 0x1FFFFFFF), round(0, r, dp(c, 3), TEXT)));
        card.setContentDescription(label);
        card.setTag(e == null ? null : e.component.flattenToString());
        card.setTag(R.id.homeLabel, label);
        card.setTag(R.id.homeAccent, accent);
        return card;
    }

    /** A pill button: white and dark text when {@code primary}, glass otherwise; white ring on focus. */
    static TextView pill(Context c, int label, boolean primary) {
        TextView b = text(c, 17, primary ? 0xFF070A11 : TEXT, R.string.font_medium);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        int padH = dp(c, 24);
        b.setPadding(padH, 0, padH, 0);
        float r = dp(c, 24);
        b.setBackground(primary
                ? states(round(0xE6F7F8FA, r, 0, 0), round(0xFFFFFFFF, r, dp(c, 3), HONEY))
                : states(round(0x8C101420, r, Math.max(1, dp(c, 1)), 0x47FFFFFF), round(0x40FFFFFF, r, dp(c, 2), TEXT)));
        lift(b, 1.06f, null);
        return b;
    }

    /** A remote-key hint: a keycap and what it does. */
    static View keyHint(Context c, String key, int label) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView cap = text(c, 12, TEXT, R.string.font_medium);
        cap.setText(key);
        cap.setGravity(Gravity.CENTER);
        cap.setMinWidth(dp(c, 28));
        cap.setPadding(dp(c, 7), dp(c, 4), dp(c, 7), dp(c, 5));
        android.graphics.drawable.GradientDrawable g = round(0x1AFFFFFF, dp(c, 7), Math.max(1, dp(c, 1)), 0x33FFFFFF);
        cap.setBackground(g);
        row.addView(cap);
        TextView t = text(c, 15, TEXT_2, R.string.font_regular);
        t.setText(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(c, 9));
        lp.setMarginEnd(dp(c, 24));
        row.addView(t, lp);
        return row;
    }

    /** A small caps section label ("PERSONALIZAR"). */
    static TextView kicker(Context c, int label, int color) {
        TextView t = text(c, 14, color, R.string.font_medium);
        t.setText(label);
        t.setAllCaps(true);
        t.setLetterSpacing(0.2f);
        return t;
    }

    /** "buzz" + "tv" in the accent: the BuzzTV wordmark. */
    static CharSequence wordmark(String first, int accent) {
        android.text.SpannableString s = new android.text.SpannableString(first + "tv");
        s.setSpan(new android.text.style.ForegroundColorSpan(accent | 0xFF000000), first.length(), first.length() + 2,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    static void center(TextView t) {
        t.setGravity(Gravity.CENTER);
    }
}
