package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * BuzzTV's settings, built in code on the Buzz kit: a hub with the current Home as a live miniature and the
 * categories as cards, then one screen per category. Design and Background are full screens (a live preview
 * of the real layout, a gallery of the TV's own pictures); the rest are lists of large rows. Every change is
 * saved at once and bumps the settings revision, so Home rebuilds when it comes back to the front.
 */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};
    /** Background animation choices, in the order the Animation screen lists them. */
    static final String[] ANIMATION_OPTIONS = {
            Config.WALLPAPER_ANIMATION_NONE, Config.WALLPAPER_ANIMATION_WEATHER,
            WallpaperMotion.MOTES, WallpaperMotion.STARS, WallpaperMotion.SNOW, WallpaperMotion.RAIN,
            WallpaperMotion.STORM, WallpaperMotion.SUNBEAMS, WallpaperMotion.CLOUDS, WallpaperMotion.BUBBLES,
            WallpaperMotion.FIREFLIES
    };

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Map<String, Bitmap> thumbs = new HashMap<>();
    private SharedPreferences choices;
    private Config config;
    private FrameLayout content;
    private LinearLayout footer;
    private LinearLayout crumbs;
    private Bitmap wallpaper;
    private HomeLayoutHost previewHost;
    private LiveWeather weather;
    private LiveWeather.Reading reading;
    /** Where Back goes from the screen on display; null on the hub (Back leaves settings). */
    private Runnable back;

    // Design screen state, saved only by "Use this layout".
    private String pendingTheme;
    private HomeStyle pendingStyle;
    private FrameLayout designPreviewHolder;
    /** The small preview on the Ajustes hub, swapped for a fresh one when the weather arrives. */
    private FrameLayout hubPreview;
    private TextView designKicker, designTitle, designDesc;
    /** The carousel's thumbnails, each tagged with its layout id, and the button that applies the pick. */
    private final List<View> designThumbs = new ArrayList<>();
    private TextView designUse;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        config = Config.load(this);
        wallpaper = decodeWallpaper();
        previewHost = LayoutPreview.host(this, () -> weather);
        setContentView(chrome());
        if (choices.getBoolean(Config.WEATHER_LIVE_KEY, false)) startWeather();
        showHub();
    }

    /**
     * Reads the Weather Sync add-on for the header and the previews. start(), not read(): LiveWeather hands a
     * reading to its listener only while started, so a bare read() left Ajustes without weather and its preview
     * asking to turn on a Clima that was already on. onDestroy's shutdown() stops it.
     */
    private void startWeather() {
        weather = new LiveWeather(this, main, r -> {
            reading = r;
            updateHeaderWeather();
            // Posted: the first reading can land before the hub is attached, and View.post waits for that.
            content.post(this::refreshPreviewsForWeather);
        });
        weather.start();
    }

    /** Redraws whichever live preview is on screen, so it shows the reading that just arrived. */
    private void refreshPreviewsForWeather() {
        if (designPreviewHolder != null && designPreviewHolder.isAttachedToWindow()) {
            refreshDesignPreview();
        }
        if (hubPreview != null && hubPreview.isAttachedToWindow() && hubPreview.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) hubPreview.getParent();
            int at = parent.indexOfChild(hubPreview);
            FrameLayout fresh = LayoutPreview.create(this, config.homeTheme, HomeStyle.load(this), wallpaper,
                    previewHost, reading, dp(270));
            fresh.setElevation(hubPreview.getElevation());
            fresh.setLayoutParams(hubPreview.getLayoutParams());
            parent.removeViewAt(at);
            parent.addView(fresh, at);
            hubPreview = fresh;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
        if (weather != null) weather.shutdown();
    }

    @Override
    public void onBackPressed() {
        if (back != null) back.run();
        else super.onBackPressed();
    }

    // ---- chrome: backdrop, header, content, footer -------------------------------------------------

    private TextView headerWeather;

    private View chrome() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF06080E);
        root.setClipChildren(false);   // side panels reach past the content margins to the screen's edges
        if (wallpaper != null) {
            // The wallpaper shrunk to 32 px and stretched back: a soft wash of its colours, made once.
            Bitmap tiny = Bitmap.createScaledBitmap(wallpaper, 32, Math.max(1, 32 * wallpaper.getHeight() / wallpaper.getWidth()), true);
            ImageView wash = new ImageView(this);
            wash.setScaleType(ImageView.ScaleType.CENTER_CROP);
            wash.setImageBitmap(tiny);
            root.addView(wash, match());
        }
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xB306080E, 0xD906080E, 0xF006080E}));
        root.addView(scrim, match());
        View glow = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientCenter(0.1f, 0.0f);
        g.setGradientRadius(dp(420));
        g.setColors(new int[]{0x29FFC53D, 0x00FFC53D});
        glow.setBackground(g);
        root.addView(glow, match());

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36), Gravity.TOP);
        hl.setMargins(dp(48), dp(18), dp(48), 0);
        root.addView(header, hl);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.buzz_logo);
        logo.setContentDescription(getString(R.string.app_name));
        header.addView(logo, new LinearLayout.LayoutParams(dp(28), dp(28)));
        crumbs = new LinearLayout(this);
        crumbs.setOrientation(LinearLayout.HORIZONTAL);
        crumbs.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cl.setMarginStart(dp(12));
        header.addView(crumbs, cl);
        headerWeather = Buzz.text(this, 14, Buzz.TEXT_2, R.string.font_regular);
        LinearLayout.LayoutParams wl = wrap();
        wl.setMarginEnd(dp(16));
        header.addView(headerWeather, wl);
        header.addView(Buzz.clock(this, 17, Buzz.TEXT, R.string.font_light));

        content = new FrameLayout(this);
        content.setClipChildren(false);
        FrameLayout.LayoutParams ctl = match();
        ctl.setMargins(dp(48), dp(68), dp(48), dp(48));
        root.addView(content, ctl);

        footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28), Gravity.BOTTOM);
        fl.setMargins(dp(48), 0, dp(48), dp(14));
        root.addView(footer, fl);
        return root;
    }

    private void setCrumbs(int... labels) {
        crumbs.removeAllViews();
        String[] all = new String[labels.length + 1];
        all[0] = "BuzzTV";
        for (int i = 0; i < labels.length; i++) all[i + 1] = getString(labels[i]);
        for (int i = 0; i < all.length; i++) {
            boolean last = i == all.length - 1;
            TextView t = Buzz.text(this, 14, last ? Buzz.TEXT : Buzz.TEXT_3, last ? R.string.font_medium : R.string.font_regular);
            t.setText(all[i]);
            crumbs.addView(t);
            if (!last) {
                TextView sep = Buzz.text(this, 14, Buzz.TEXT_3, R.string.font_regular);
                sep.setText("  ›  ");
                crumbs.addView(sep);
            }
        }
    }

    private void setKeys(String[] keys, int[] labels) {
        footer.removeAllViews();
        for (int i = 0; i < keys.length; i++) footer.addView(Buzz.keyHint(this, keys[i], labels[i]));
    }

    private void updateHeaderWeather() {
        if (reading == null) {
            headerWeather.setText("");
            return;
        }
        headerWeather.setText(getString(R.string.degrees, reading.tempC) + " · "
                + getString(LiveWeather.conditionRes(reading.weatherCode)));
    }

    // ---- hub ------------------------------------------------------------------------------------------

    private void showHub() {
        back = null;
        setCrumbs(R.string.settings_title);
        setKeys(new String[]{"◀▶▲▼", "OK", "↩"},
                new int[]{R.string.key_move, R.string.key_open, R.string.key_back_home});
        content.removeAllViews();
        LinearLayout across = new LinearLayout(this);
        across.setOrientation(LinearLayout.HORIZONTAL);
        across.setClipChildren(false);
        content.addView(across, match());

        LinearLayout aside = new LinearLayout(this);
        aside.setOrientation(LinearLayout.VERTICAL);
        across.addView(aside, new LinearLayout.LayoutParams(dp(270), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView title = Buzz.text(this, 40, Buzz.TEXT, R.string.font_medium);
        title.setText(R.string.settings_title);
        aside.addView(title);
        TextView sub = Buzz.text(this, 14, Buzz.TEXT_2, R.string.font_light);
        sub.setSingleLine(false);
        sub.setLineSpacing(dp(3), 1f);
        sub.setText(R.string.settings_subtitle);
        LinearLayout.LayoutParams sl = wrap();
        sl.topMargin = dp(8);
        aside.addView(sub, sl);
        String theme = config.homeTheme;
        FrameLayout preview = LayoutPreview.create(this, theme, HomeStyle.load(this), wallpaper, previewHost, reading, dp(270));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(preview.getLayoutParams().width, preview.getLayoutParams().height);
        pl.topMargin = dp(16);
        preview.setElevation(dp(12));
        aside.addView(preview, pl);
        hubPreview = preview;
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams chl = wrap();
        chl.topMargin = dp(12);
        aside.addView(chips, chl);
        chips.addView(chip(getString(HomeTheme.labelRes(theme)), Buzz.HONEY));
        chips.addView(chip(animationLabel(config.wallpaperAnimation), 0xFFA78BFA));
        aside.addView(Buzz.spacer(this), new LinearLayout.LayoutParams(1, 0, 1f));
        TextView version = Buzz.text(this, 12, Buzz.TEXT_3, R.string.font_regular);
        version.setText(getString(R.string.settings_version, versionName()));
        aside.addView(version);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setClipChildren(false);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        ml.setMarginStart(dp(34));
        across.addView(main, ml);
        main.addView(Buzz.kicker(this, R.string.settings_personalize, Buzz.TEXT_3));
        View design = bigCard(R.drawable.ic_layout, 0xFFFFD36B, 0xFFF59E0B, R.string.settings_design,
                getString(HomeTheme.labelRes(theme)), getString(R.string.settings_design_hint, HomeTheme.ALL.length), this::showDesign);
        View background = bigCard(R.drawable.ic_image, 0xFF34D3C2, 0xFF0E8F86, R.string.settings_background,
                BackgroundPicker.state(this, choices), null, this::openBackground);
        View animation = bigCard(R.drawable.ic_sparkle, 0xFFA78BFA, 0xFF6D28D9, R.string.wallpaper_animation_label,
                animationLabel(config.wallpaperAnimation), null, this::showAnimation);
        View weatherCard = bigCard(R.drawable.ic_cloud, 0xFF7CC4FF, 0xFF2563EB, R.string.settings_weather,
                weatherState(), null, this::showWeather);
        main.addView(pair(design, background, dp(96), dp(6)));
        main.addView(pair(animation, weatherCard, dp(96), dp(12)));
        LinearLayout.LayoutParams k2 = wrap();
        k2.topMargin = dp(16);
        TextView sys = Buzz.kicker(this, R.string.settings_system, Buzz.TEXT_3);
        main.addView(sys, k2);
        View apps = smallRow(R.drawable.ic_apps, R.string.settings_apps, getString(R.string.settings_apps_value), this::showApps);
        View clock = smallRow(R.drawable.ic_clock, R.string.settings_clock, clockLabel(config.clockFormat), this::showClock);
        View home = smallRow(R.drawable.ic_home, R.string.settings_default_home, getString(R.string.settings_default_home_value), this::showHome);
        View tv = smallRow(R.drawable.ic_tv, R.string.settings_tv, getString(R.string.settings_tv_value),
                () -> open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        main.addView(pair(apps, clock, dp(48), dp(6)));
        main.addView(pair(home, tv, dp(48), dp(10)));
        design.requestFocus();
    }

    private View chip(String label, int dot) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(10), 0, dp(12), 0);
        c.setBackground(Buzz.round(0x14FFFFFF, dp(13), Math.max(1, dp(1)), 0x1AFFFFFF));
        View d = new View(this);
        d.setBackground(Buzz.round(dot, dp(3), 0, 0));
        c.addView(d, new LinearLayout.LayoutParams(dp(6), dp(6)));
        TextView t = Buzz.text(this, 12, Buzz.TEXT, R.string.font_regular);
        t.setText(label);
        LinearLayout.LayoutParams tl = wrap();
        tl.setMarginStart(dp(6));
        c.addView(t, tl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26));
        lp.setMarginEnd(dp(8));
        c.setLayoutParams(lp);
        return c;
    }

    private LinearLayout pair(View a, View b, int height, int top) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(0, height, 1f);
        al.setMarginEnd(dp(12));
        row.addView(a, al);
        row.addView(b, new LinearLayout.LayoutParams(0, height, 1f));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        rl.topMargin = top;
        row.setLayoutParams(rl);
        return row;
    }

    /** A large category card: a coloured icon tile, the name, its current value, and a hint while focused. */
    private View bigCard(int icon, int from, int to, int name, String value, String hint, Runnable go) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setFocusable(true);
        c.setClickable(true);
        c.setPadding(dp(16), 0, dp(14), 0);
        float r = dp(18);
        c.setBackground(Buzz.states(Buzz.round(0x9E141824, r, Math.max(1, dp(1)), 0x17FFFFFF),
                Buzz.round(0x38FFC53D, r, dp(2), Buzz.HONEY)));
        FrameLayout tile = new FrameLayout(this);
        GradientDrawable tg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{from, to});
        tg.setCornerRadius(dp(13));
        tile.setBackground(tg);
        ImageView i = new ImageView(this);
        i.setImageResource(icon);
        tile.addView(i, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        c.addView(tile, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMarginStart(dp(14));
        c.addView(text, tl);
        TextView n = Buzz.text(this, 17, Buzz.TEXT, R.string.font_medium);
        n.setText(name);
        text.addView(n);
        TextView v = Buzz.text(this, 13, Buzz.TEXT_2, R.string.font_regular);
        v.setText(value);
        LinearLayout.LayoutParams vl = wrap();
        vl.topMargin = dp(3);
        text.addView(v, vl);
        TextView h = Buzz.text(this, 12, Buzz.HONEY, R.string.font_regular);
        h.setText(hint == null ? "" : hint);
        h.setVisibility(View.GONE);
        text.addView(h, vl);
        ImageView chev = new ImageView(this);
        chev.setImageResource(R.drawable.ic_chevron);
        chev.setAlpha(0.4f);
        c.addView(chev, new LinearLayout.LayoutParams(dp(20), dp(20)));
        c.setContentDescription(getString(name) + ", " + value);
        c.setOnClickListener(x -> go.run());
        Buzz.lift(c, 1.03f, (x, f) -> {
            h.setVisibility(f && hint != null ? View.VISIBLE : View.GONE);
            chev.setAlpha(f ? 1f : 0.4f);
            if (f) chev.setColorFilter(Buzz.HONEY);
            else chev.clearColorFilter();
        });
        return c;
    }

    private View smallRow(int icon, int name, String value, Runnable go) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setFocusable(true);
        c.setClickable(true);
        c.setPadding(dp(14), 0, dp(14), 0);
        float r = dp(14);
        c.setBackground(Buzz.states(Buzz.round(0x9E141824, r, Math.max(1, dp(1)), 0x14FFFFFF),
                Buzz.round(0x38FFC53D, r, dp(2), Buzz.HONEY)));
        ImageView i = new ImageView(this);
        i.setImageResource(icon);
        i.setAlpha(0.85f);
        c.addView(i, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView n = Buzz.text(this, 15, Buzz.TEXT, R.string.font_regular);
        n.setText(name);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nl.setMarginStart(dp(12));
        c.addView(n, nl);
        TextView v = Buzz.text(this, 13, Buzz.TEXT_3, R.string.font_regular);
        v.setText(value);
        c.addView(v);
        c.setContentDescription(getString(name) + ", " + value);
        c.setOnClickListener(x -> go.run());
        Buzz.lift(c, 1.03f, null);
        return c;
    }

    // ---- design ---------------------------------------------------------------------------------------

    private void showDesign() {
        back = this::showHub;
        setCrumbs(R.string.settings_title, R.string.settings_design);
        setKeys(new String[]{"◀▶", "OK", "↩"}, new int[]{R.string.key_change_layout, R.string.key_choose, R.string.key_back_no_change});
        content.removeAllViews();
        pendingTheme = HomeTheme.index(config.homeTheme) >= 0 ? HomeTheme.normalize(config.homeTheme) : HomeTheme.DEFAULT;
        pendingStyle = HomeStyle.load(this);

        LinearLayout across = new LinearLayout(this);
        across.setOrientation(LinearLayout.HORIZONTAL);
        across.setClipChildren(false);
        content.addView(across, match());

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setClipChildren(false);
        across.addView(left, new LinearLayout.LayoutParams(dp(560), ViewGroup.LayoutParams.MATCH_PARENT));
        designPreviewHolder = new FrameLayout(this);
        designPreviewHolder.setElevation(dp(16));
        left.addView(designPreviewHolder, new LinearLayout.LayoutParams(dp(560), dp(315)));

        LinearLayout carousel = new LinearLayout(this);
        carousel.setOrientation(LinearLayout.HORIZONTAL);
        carousel.setClipChildren(false);
        carousel.setClipToPadding(false);
        carousel.setPadding(0, dp(14), 0, 0);
        left.addView(carousel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        designThumbs.clear();
        View selectedThumb = null;
        for (int i = 0; i < HomeTheme.ALL.length; i++) {
            String id = HomeTheme.ALL[i];
            View t = layoutThumb(id);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginEnd(i == HomeTheme.ALL.length - 1 ? 0 : dp(8));
            carousel.addView(t, lp);
            if (id.equals(pendingTheme)) selectedThumb = t;
        }

        LinearLayout aside = new LinearLayout(this);
        aside.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        al.setMarginStart(dp(30));
        across.addView(aside, al);
        designKicker = Buzz.kicker(this, R.string.settings_design, Buzz.HONEY);
        aside.addView(designKicker);
        designTitle = Buzz.text(this, 30, Buzz.TEXT, R.string.font_medium);
        LinearLayout.LayoutParams dtl = wrap();
        dtl.topMargin = dp(4);
        aside.addView(designTitle, dtl);
        designDesc = Buzz.text(this, 13, Buzz.TEXT_2, R.string.font_light);
        designDesc.setSingleLine(false);
        designDesc.setMaxLines(3);
        designDesc.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams ddl = wrap();
        ddl.topMargin = dp(6);
        aside.addView(designDesc, ddl);
        TextView live = Buzz.text(this, 11, 0xFF3DD68C, R.string.font_medium);
        live.setText("\u25CF  " + getString(R.string.design_live));
        live.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams lvl = wrap();
        lvl.topMargin = dp(8);
        aside.addView(live, lvl);

        aside.addView(label(R.string.design_accent, 14));
        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        swatches.setGravity(Gravity.CENTER_VERTICAL);
        swatches.setClipChildren(false);
        aside.addView(swatches);
        List<View> sw = new ArrayList<>();
        for (int accent : HomeStyle.ACCENTS) {
            View s = accent == 0 ? textChoice(getString(R.string.design_accent_auto)) : swatch(accent);
            s.setOnClickListener(v -> {
                pendingStyle = pendingStyle.with(accent, pendingStyle.compact, pendingStyle.greeting, pendingStyle.inputsRow);
                for (View o : sw) o.setSelected(o == v);
                refreshDesignPreview();
            });
            s.setSelected(accent == pendingStyle.accent);
            sw.add(s);
            swatches.addView(s);
        }

        aside.addView(label(R.string.design_density, 12));
        aside.addView(segmented(new String[]{getString(R.string.design_comfortable), getString(R.string.design_compact)},
                pendingStyle.compact ? 1 : 0, i -> {
                    pendingStyle = pendingStyle.with(pendingStyle.accent, i == 1, pendingStyle.greeting, pendingStyle.inputsRow);
                    refreshDesignPreview();
                }));
        aside.addView(toggle(R.string.design_greeting, pendingStyle.greeting, on -> {
            pendingStyle = pendingStyle.with(pendingStyle.accent, pendingStyle.compact, on, pendingStyle.inputsRow);
            refreshDesignPreview();
        }));
        aside.addView(toggle(R.string.design_inputs_row, pendingStyle.inputsRow, on -> {
            pendingStyle = pendingStyle.with(pendingStyle.accent, pendingStyle.compact, pendingStyle.greeting, on);
            refreshDesignPreview();
        }));
        aside.addView(Buzz.spacer(this), new LinearLayout.LayoutParams(1, 0, 1f));
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        aside.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        TextView use = Buzz.pill(this, R.string.design_use, true);
        designUse = use;
        use.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        use.setPadding(dp(10), 0, dp(10), 0);
        use.setBackground(Buzz.states(gradientPill(), Buzz.round(0xFFFFD36B, dp(23), dp(3), Buzz.TEXT)));
        use.setTextColor(0xFF1A1204);
        buttons.addView(use, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        TextView cancel = Buzz.pill(this, R.string.design_cancel, false);
        cancel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        cancel.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams cnl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        cnl.setMarginStart(dp(10));
        buttons.addView(cancel, cnl);
        use.setOnClickListener(v -> {
            choices.edit().putString(Config.HOME_THEME_KEY, pendingTheme).apply();
            pendingStyle.save(this);
            changed();
            showHub();
        });
        cancel.setOnClickListener(v -> showHub());

        refreshDesignText();
        refreshDesignPreview();
        if (selectedThumb != null) selectedThumb.requestFocus();
    }

    private GradientDrawable gradientPill() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFFFD36B, 0xFFF5A70B});
        g.setCornerRadius(dp(23));
        return g;
    }

    private void refreshDesignText() {
        int idx = HomeTheme.index(pendingTheme);
        designKicker.setText(getString(R.string.design_counter, idx + 1, HomeTheme.ALL.length));
        designTitle.setText(HomeTheme.labelRes(pendingTheme));
        designDesc.setText(HomeTheme.descriptionRes(pendingTheme));
        if (designUse != null) {
            designUse.setText(getString(R.string.design_use_named, getString(HomeTheme.labelRes(pendingTheme))));
        }
        markDesignThumbs();
    }

    /**
     * The picked layout stays marked when the focus moves on: a honey ring, a check badge and its name in honey.
     * The layout Home uses now carries a small "En uso" line, so the two are never confused.
     */
    private void markDesignThumbs() {
        String inUse = HomeTheme.normalize(config.homeTheme);
        for (View t : designThumbs) {
            String id = (String) t.getTag();
            boolean picked = id.equals(pendingTheme);
            View badge = t.findViewWithTag("badge");
            TextView name = t.findViewWithTag("name");
            TextView use = t.findViewWithTag("inuse");
            t.setSelected(picked);
            if (badge != null) badge.setVisibility(picked ? View.VISIBLE : View.INVISIBLE);
            if (name != null) {
                name.setTextColor(picked ? Buzz.HONEY : t.hasFocus() || t.isFocused() ? Buzz.TEXT : Buzz.TEXT_2);
                name.setTypeface(Buzz.font(this, picked ? R.string.font_medium : R.string.font_regular));
            }
            if (use != null) use.setVisibility(id.equals(inUse) ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void refreshDesignPreview() {
        designPreviewHolder.removeAllViews();
        FrameLayout p = LayoutPreview.create(this, pendingTheme, pendingStyle, wallpaper, previewHost, reading, dp(560));
        designPreviewHolder.addView(p);
    }

    /** A sketch of a layout for the carousel: where its clock and apps sit, and Aura's glow. */
    private View layoutThumb(String id) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setFocusable(true);
        col.setClickable(true);
        col.setClipChildren(false);
        FrameLayout box = new FrameLayout(this);
        float r = dp(9);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF23324D, 0xFF10151F});
        bg.setCornerRadius(r);
        box.setBackground(bg);
        Buzz.roundClip(box, r);
        int bw = dp(70), bh = dp(42);
        float[][] parts = thumbParts(id);
        for (float[] p : parts) {
            View v = new View(this);
            v.setBackground(Buzz.round(p[4] == 1 ? 0xCCFFFFFF : 0x66FFFFFF, dp(2), 0, 0));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(bw * p[2]), Math.round(bh * p[3]));
            lp.leftMargin = Math.round(bw * p[0]);
            lp.topMargin = Math.round(bh * p[1]);
            box.addView(v, lp);
        }
        if (HomeTheme.AURA.equals(id)) {
            View glow = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            g.setGradientCenter(0.35f, 1.1f);
            g.setGradientRadius(dp(40));
            g.setColors(new int[]{0xCC5AB0FF, 0x005AB0FF});
            glow.setBackground(g);
            box.addView(glow, 0, match());
        }
        // The ring follows the thumbnail's own state: honey and thick for the picked layout, white for focus only.
        View ring = new View(this);
        android.graphics.drawable.StateListDrawable ringStates = new android.graphics.drawable.StateListDrawable();
        ringStates.addState(new int[]{android.R.attr.state_selected}, Buzz.round(0x26FFC53D, r, dp(3), Buzz.HONEY));
        ringStates.addState(new int[]{android.R.attr.state_focused}, Buzz.round(0, r, dp(2), Buzz.TEXT));
        ringStates.addState(new int[]{}, Buzz.round(0, r, Math.max(1, dp(1)), 0x1FFFFFFF));
        ring.setBackground(ringStates);
        ring.setDuplicateParentStateEnabled(true);
        ring.setTag("ring");
        box.addView(ring, match());
        TextView badge = Buzz.text(this, 9, 0xFF1A1204, R.string.font_medium);
        badge.setText("✓");
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Buzz.round(Buzz.HONEY, dp(8), 0, 0));
        badge.setVisibility(View.INVISIBLE);
        badge.setTag("badge");
        FrameLayout.LayoutParams bdl = new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.TOP | Gravity.END);
        bdl.topMargin = dp(4);
        bdl.rightMargin = dp(4);
        box.addView(badge, bdl);
        col.addView(box, new LinearLayout.LayoutParams(bw, bh));
        TextView name = Buzz.text(this, 12, Buzz.TEXT_2, R.string.font_regular);
        name.setText(HomeTheme.labelRes(id));
        name.setTag("name");
        LinearLayout.LayoutParams nl = wrap();
        nl.topMargin = dp(6);
        col.addView(name, nl);
        TextView inUse = Buzz.text(this, 9, 0xFF3DD68C, R.string.font_medium);
        inUse.setText(R.string.design_in_use);
        inUse.setAllCaps(true);
        inUse.setLetterSpacing(0.08f);
        inUse.setVisibility(View.INVISIBLE);
        inUse.setTag("inuse");
        LinearLayout.LayoutParams ul = wrap();
        ul.topMargin = dp(2);
        col.addView(inUse, ul);
        col.setTag(id);
        col.setContentDescription(getString(HomeTheme.labelRes(id)));
        // OK picks the layout and goes straight to the button that applies it.
        col.setOnClickListener(v -> {
            if (!id.equals(pendingTheme)) {
                pendingTheme = id;
                refreshDesignText();
                refreshDesignPreview();
            }
            if (designUse != null) designUse.requestFocus();
        });
        Buzz.lift(col, 1.08f, (v, f) -> {
            if (f && !id.equals(pendingTheme)) {
                pendingTheme = id;
                refreshDesignText();
                refreshDesignPreview();
            } else {
                markDesignThumbs();
            }
        });
        designThumbs.add(col);
        return col;
    }

    /** {left, top, width, height, bright} fractions of the thumbnail for each block of a layout's sketch. */
    static float[][] thumbParts(String id) {
        switch (HomeTheme.normalize(id)) {
            case HomeTheme.MARQUEE: return new float[][]{{0.08f, 0.16f, 0.4f, 0.14f, 1}, {0.08f, 0.7f, 0.84f, 0.18f, 1}};
            case HomeTheme.AURA: return new float[][]{{0.08f, 0.14f, 0.3f, 0.2f, 0}, {0.08f, 0.7f, 0.84f, 0.16f, 1}};
            case HomeTheme.PANEL: return new float[][]{{0f, 0f, 0.12f, 1f, 0}, {0.2f, 0.12f, 0.24f, 0.32f, 0}, {0.48f, 0.12f, 0.44f, 0.32f, 0}, {0.2f, 0.56f, 0.72f, 0.32f, 1}};
            case HomeTheme.ORBIT: return new float[][]{{0.32f, 0.14f, 0.36f, 0.2f, 0}, {0.18f, 0.66f, 0.18f, 0.16f, 0}, {0.4f, 0.6f, 0.2f, 0.2f, 1}, {0.64f, 0.66f, 0.18f, 0.16f, 0}};
            case HomeTheme.MOSAIC: return new float[][]{{0.06f, 0.12f, 0.24f, 0.76f, 0}, {0.36f, 0.12f, 0.3f, 0.5f, 1}, {0.7f, 0.12f, 0.24f, 0.22f, 0}, {0.7f, 0.4f, 0.24f, 0.22f, 0}, {0.36f, 0.68f, 0.58f, 0.2f, 0}};
            case HomeTheme.BENTO: return new float[][]{{0.06f, 0.12f, 0.42f, 0.5f, 0}, {0.54f, 0.12f, 0.19f, 0.24f, 1}, {0.75f, 0.12f, 0.19f, 0.24f, 1}, {0.54f, 0.4f, 0.4f, 0.22f, 0}, {0.06f, 0.7f, 0.88f, 0.16f, 0}};
            default: return new float[][]{{0.08f, 0.12f, 0.24f, 0.14f, 0}, {0.06f, 0.66f, 0.88f, 0.2f, 1}};
        }
    }

    private View swatch(int color) {
        View s = new View(this);
        s.setFocusable(true);
        s.setClickable(true);
        float r = dp(15);
        GradientDrawable normal = Buzz.round(color, r, Math.max(1, dp(1)), 0x40FFFFFF);
        GradientDrawable picked = Buzz.round(color, r, dp(3), Buzz.TEXT);
        android.graphics.drawable.StateListDrawable st = new android.graphics.drawable.StateListDrawable();
        st.addState(new int[]{android.R.attr.state_focused}, picked);
        st.addState(new int[]{android.R.attr.state_selected}, picked);
        st.addState(new int[]{}, normal);
        s.setBackground(st);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
        lp.setMarginStart(dp(8));
        s.setLayoutParams(lp);
        Buzz.lift(s, 1.15f, null);
        return s;
    }

    private TextView textChoice(String text) {
        TextView t = Buzz.text(this, 13, Buzz.TEXT, R.string.font_medium);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        t.setPadding(dp(12), 0, dp(12), 0);
        float r = dp(16);
        android.graphics.drawable.StateListDrawable st = new android.graphics.drawable.StateListDrawable();
        st.addState(new int[]{android.R.attr.state_focused}, Buzz.round(0x4DFFC53D, r, dp(2), Buzz.TEXT));
        st.addState(new int[]{android.R.attr.state_selected}, Buzz.round(0x33FFC53D, r, dp(2), Buzz.HONEY));
        st.addState(new int[]{}, Buzz.round(0x14FFFFFF, r, Math.max(1, dp(1)), 0x24FFFFFF));
        t.setBackground(st);
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)));
        Buzz.lift(t, 1.05f, null);
        return t;
    }

    // ---- background -----------------------------------------------------------------------------------

    private String gallerySection;   // null = every section
    private static final int GALLERY_COLUMNS = 4;
    private static final int GALLERY_MAX = 120;

    /** From the hub: open on the section in use (or every picture when everything rotates). */
    private void openBackground() {
        String mode = choices.getString(BackgroundLibrary.MODE_KEY, null);
        gallerySection = BackgroundLibrary.MODE_ALL.equals(mode) ? null : choices.getString(BackgroundLibrary.SECTION_KEY, null);
        showBackground(-1);
    }

    private void showBackground() {
        showBackground(-1);
    }

    /** @param focusChip the section chip to keep the focus on after a redraw, or -1 for the chosen picture */
    private void showBackground(int focusChip) {
        back = this::showHub;
        setCrumbs(R.string.settings_title, R.string.settings_background);
        setKeys(new String[]{"OK", "\u21A9"}, new int[]{R.string.key_apply, R.string.key_back});
        content.removeAllViews();
        File root = Config.dir(this);
        List<String> sections = BackgroundLibrary.sections(root);
        String mode = choices.getString(BackgroundLibrary.MODE_KEY, null);

        LinearLayout across = new LinearLayout(this);
        across.setOrientation(LinearLayout.HORIZONTAL);
        across.setClipChildren(false);   // the side panel reaches past the content margins to the screen's edges
        content.addView(across, match());

        // The left column clips: the chip row and the gallery scroll inside it and never paint over the panel.
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setClipChildren(true);
        across.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        head.setPadding(dp(14), 0, dp(14), 0);
        left.addView(head);
        TextView title = Buzz.text(this, 30, Buzz.TEXT, R.string.font_medium);
        title.setText(R.string.settings_background);
        head.addView(title);
        TextView sub = Buzz.text(this, 13, Buzz.TEXT_3, R.string.font_regular);
        sub.setText(R.string.background_whole);
        LinearLayout.LayoutParams subl = wrap();
        subl.setMarginStart(dp(12));
        subl.bottomMargin = dp(5);
        head.addView(sub, subl);

        if (sections.isEmpty()) {
            TextView empty = Buzz.text(this, 15, Buzz.TEXT_2, R.string.font_regular);
            empty.setSingleLine(false);
            empty.setText(R.string.background_empty);
            empty.setPadding(dp(14), 0, dp(14), 0);
            LinearLayout.LayoutParams el = wrap();
            el.topMargin = dp(24);
            left.addView(empty, el);
        } else {
            HorizontalScrollView chipScroll = new HorizontalScrollView(this);
            chipScroll.setHorizontalScrollBarEnabled(false);
            chipScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            // A carousel: the chips slide under a soft fade at either end, never past the panel's line.
            chipScroll.setHorizontalFadingEdgeEnabled(true);
            chipScroll.setFadingEdgeLength(dp(56));
            chipScroll.setSmoothScrollingEnabled(true);
            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setClipChildren(false);
            // Room on every side for a chip that grows on focus.
            chips.setPadding(dp(14), dp(10), dp(14), dp(10));
            chipScroll.addView(chips);
            LinearLayout.LayoutParams csl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            csl.topMargin = dp(4);
            left.addView(chipScroll, csl);
            List<String> chipIds = new ArrayList<>();
            chipIds.add(null);
            chipIds.addAll(sections);
            View keep = null;
            for (int i = 0; i < chipIds.size(); i++) {
                String sec = chipIds.get(i);
                final int index = i;
                TextView c = textChoice(sec == null ? getString(R.string.background_all) : ThemeLibrary.label(sec));
                ((LinearLayout.LayoutParams) c.getLayoutParams()).setMarginEnd(dp(10));
                c.setSelected(sec == null ? gallerySection == null : sec.equals(gallerySection));
                c.setOnClickListener(v -> {
                    gallerySection = sec;
                    showBackground(index);
                });
                chips.addView(c);
                if (i == focusChip) keep = c;
                if (c.isSelected()) chipScroll.post(() -> chipScroll.smoothScrollTo(
                        Math.max(0, c.getLeft() - chipScroll.getWidth() / 3), 0));
            }

            ScrollView scroll = new ScrollView(this);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            LinearLayout rows = new LinearLayout(this);
            rows.setOrientation(LinearLayout.VERTICAL);
            rows.setClipChildren(false);
            rows.setPadding(dp(14), dp(10), dp(14), dp(14));
            scroll.addView(rows);
            left.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            List<File> pics = new ArrayList<>();
            if (gallerySection == null) for (String sec : sections) pics.addAll(BackgroundLibrary.images(root, sec));
            else pics.addAll(BackgroundLibrary.images(root, gallerySection));
            if (pics.size() > GALLERY_MAX) pics = pics.subList(0, GALLERY_MAX);
            String picked = BackgroundLibrary.MODE_IMAGE.equals(mode) ? choices.getString(BackgroundLibrary.IMAGE_KEY, null) : null;
            String pickedSection = choices.getString(BackgroundLibrary.SECTION_KEY, null);
            LinearLayout line = null;
            List<View> cells = new ArrayList<>();
            View first = null;
            for (int i = 0; i < pics.size(); i++) {
                if (i % GALLERY_COLUMNS == 0) {
                    line = new LinearLayout(this);
                    line.setOrientation(LinearLayout.HORIZONTAL);
                    line.setClipChildren(false);
                    LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(84));
                    ll.bottomMargin = dp(14);
                    rows.addView(line, ll);
                }
                File f = pics.get(i);
                String section = sectionOf(f);
                boolean selected = f.getName().equals(picked) && section.equals(pickedSection);
                View cell = picture(f, section, selected, cells);
                cells.add(cell);
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                cl.setMarginEnd(i % GALLERY_COLUMNS == GALLERY_COLUMNS - 1 ? 0 : dp(14));
                line.addView(cell, cl);
                if (first == null || selected) first = cell;
            }
            if (line != null) {
                for (int i = pics.size(); i % GALLERY_COLUMNS != 0; i++) {
                    LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                    cl.setMarginEnd(i % GALLERY_COLUMNS == GALLERY_COLUMNS - 1 ? 0 : dp(14));
                    line.addView(Buzz.spacer(this), cl);
                }
            }
            View target = keep != null ? keep : first;
            if (target != null) target.requestFocus();
        }

        // Right: how the picture is chosen, how often it changes (only when it does), the animation.
        // A fixed side panel: flush with the right and bottom edges of the screen, behind a hairline the gallery
        // and the section carousel never cross.
        View rule = new View(this);
        rule.setBackgroundColor(0x24FFFFFF);
        LinearLayout.LayoutParams rul = new LinearLayout.LayoutParams(Math.max(1, dp(1)), ViewGroup.LayoutParams.MATCH_PARENT);
        rul.setMarginStart(dp(10));
        rul.bottomMargin = -dp(48);
        across.addView(rule, rul);
        LinearLayout aside = new LinearLayout(this);
        aside.setOrientation(LinearLayout.VERTICAL);
        aside.setPadding(dp(24), dp(6), dp(48), dp(56));
        aside.setBackgroundColor(0xE60B0F18);
        LinearLayout.LayoutParams asl = new LinearLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.MATCH_PARENT);
        asl.setMarginEnd(-dp(48));
        asl.bottomMargin = -dp(48);
        across.addView(aside, asl);
        aside.addView(Buzz.kicker(this, R.string.background_how, Buzz.TEXT_3));
        String current = gallerySection != null ? gallerySection : choices.getString(BackgroundLibrary.SECTION_KEY, null);
        String[][] modes = {
                {BackgroundLibrary.MODE_IMAGE, getString(R.string.background_mode_image), getString(R.string.background_mode_image_sub)},
                {BackgroundLibrary.MODE_SECTION, getString(R.string.background_mode_section),
                        current == null ? getString(R.string.background_mode_section_sub) : ThemeLibrary.label(current)},
                {BackgroundLibrary.MODE_ALL, getString(R.string.background_mode_all), getString(R.string.background_mode_all_sub)}};
        View firstMode = null;
        for (String[] m : modes) {
            View r = radio(m[1], m[2], m[0].equals(mode), () -> {
                if (BackgroundLibrary.MODE_ALL.equals(m[0])) saveBackground(BackgroundLibrary.MODE_ALL, null, null);
                else if (BackgroundLibrary.MODE_SECTION.equals(m[0]) && current != null) saveBackground(BackgroundLibrary.MODE_SECTION, current, null);
                else if (BackgroundLibrary.MODE_IMAGE.equals(m[0])) {
                    Toast.makeText(this, R.string.background_pick_one, Toast.LENGTH_SHORT).show();
                    return;
                } else return;
                showBackground();
            });
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
            rl.topMargin = dp(8);
            aside.addView(r, rl);
            if (firstMode == null) firstMode = r;
        }
        // A fixed picture never changes: no interval to choose.
        boolean rotates = BackgroundLibrary.MODE_SECTION.equals(mode) || BackgroundLibrary.MODE_ALL.equals(mode);
        if (rotates) {
            LinearLayout.LayoutParams k2 = wrap();
            k2.topMargin = dp(16);
            aside.addView(Buzz.kicker(this, R.string.background_every, Buzz.TEXT_3), k2);
            int rot = choices.getInt(Config.WALLPAPER_ROTATION_KEY, BackgroundLibrary.DEFAULT_ROTATION_MINUTES);
            int rotIdx = 3;
            for (int i = 0; i < Config.WALLPAPER_ROTATION_CHOICES.length; i++) if (Config.WALLPAPER_ROTATION_CHOICES[i] == rot) rotIdx = i;
            String[] rotLabels = {getString(R.string.every_1m), getString(R.string.every_5m), getString(R.string.every_10m),
                    getString(R.string.every_20m), getString(R.string.every_60m)};
            View stepper = stepper(rotLabels, rotIdx, i -> {
                choices.edit().putInt(Config.WALLPAPER_ROTATION_KEY, Config.WALLPAPER_ROTATION_CHOICES[i]).apply();
                changed();
            });
            LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
            stl.topMargin = dp(8);
            aside.addView(stepper, stl);
        }
        LinearLayout.LayoutParams k3 = wrap();
        k3.topMargin = dp(16);
        aside.addView(Buzz.kicker(this, R.string.wallpaper_animation_label, Buzz.TEXT_3), k3);
        View anim = actionRow(animationLabel(config.wallpaperAnimation), "", this::showAnimation);
        LinearLayout.LayoutParams anl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        anl.topMargin = dp(8);
        aside.addView(anim, anl);
        if (sections.isEmpty() && firstMode != null) firstMode.requestFocus();
    }

    /**
     * One value at a time, changed with Left and Right: "◀  20 minutes  ▶" with a dot per choice under it. Takes
     * a narrow column far better than five chips side by side.
     */
    private View stepper(String[] values, int selected, java.util.function.IntConsumer pick) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setFocusable(true);
        box.setClickable(true);
        float r = dp(16);
        box.setBackground(Buzz.states(Buzz.round(0x8C141824, r, Math.max(1, dp(1)), 0x1AFFFFFF),
                Buzz.round(0x33FFC53D, r, dp(2), Buzz.HONEY)));
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ImageView prev = new ImageView(this);
        prev.setImageResource(R.drawable.ic_chevron);
        prev.setRotation(180f);
        line.addView(prev, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView value = Buzz.text(this, 17, Buzz.TEXT, R.string.font_medium);
        value.setGravity(Gravity.CENTER);
        line.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView nextIcon = new ImageView(this);
        nextIcon.setImageResource(R.drawable.ic_chevron);
        line.addView(nextIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        LinearLayout dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dl = wrap();
        dl.topMargin = dp(8);
        box.addView(dots, dl);
        List<View> dotViews = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            View d = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(6), dp(6));
            lp.setMargins(dp(3), 0, dp(3), 0);
            dots.addView(d, lp);
            dotViews.add(d);
        }
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        int[] at = {selected};
        Runnable paint = () -> {
            value.setText(values[at[0]]);
            prev.setAlpha(at[0] == 0 ? 0.25f : 1f);
            nextIcon.setAlpha(at[0] == values.length - 1 ? 0.25f : 1f);
            for (int i = 0; i < dotViews.size(); i++) {
                boolean on = i == at[0];
                dotViews.get(i).setBackground(Buzz.round(on ? Buzz.HONEY : 0x40FFFFFF, dp(3), 0, 0));
            }
            box.setContentDescription(getString(R.string.background_every) + ": " + values[at[0]]);
        };
        paint.run();
        box.setOnKeyListener((v, code, e) -> {
            if (code != android.view.KeyEvent.KEYCODE_DPAD_LEFT && code != android.view.KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            int next = at[0] + (code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
            // Past either end the key moves the focus out as usual.
            if (next < 0 || next >= values.length) return false;
            if (e.getAction() != android.view.KeyEvent.ACTION_DOWN) return true;
            at[0] = next;
            paint.run();
            pick.accept(next);
            return true;
        });
        box.setOnClickListener(v -> {
            at[0] = (at[0] + 1) % values.length;
            paint.run();
            pick.accept(at[0]);
        });
        Buzz.lift(box, 1.03f, (v, f) -> {
            int tint = f ? Buzz.HONEY : Buzz.TEXT;
            prev.setColorFilter(tint);
            nextIcon.setColorFilter(tint);
        });
        return box;
    }

    private static String sectionOf(File f) {
        File parent = f.getParentFile();
        if (parent == null) return "";
        if (ThemeFiles.WALLPAPERS.equals(parent.getName()) && parent.getParentFile() != null) return parent.getParentFile().getName();
        return parent.getName();
    }

    private View picture(File f, String section, boolean selected, List<View> cells) {
        float r = dp(14);
        FrameLayout cell = new Buzz.RoundFrame(this, r);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setBackground(Buzz.round(0xFF141A26, r, 0, 0));
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cell.addView(img, match());
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xB3000000}));
        cell.addView(shade, match());
        TextView name = Buzz.text(this, 12, Buzz.TEXT, R.string.font_medium);
        name.setText(pictureName(f));
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        nl.setMargins(dp(10), 0, dp(10), dp(8));
        cell.addView(name, nl);
        TextView check = Buzz.text(this, 13, 0xFF1A1204, R.string.font_medium);
        check.setText("✓");
        check.setGravity(Gravity.CENTER);
        check.setBackground(Buzz.round(Buzz.HONEY, dp(11), 0, 0));
        check.setVisibility(selected ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams ckl = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.END);
        ckl.setMargins(0, dp(8), dp(8), 0);
        cell.addView(check, ckl);
        cell.setForeground(Buzz.states(Buzz.round(0, r, selected ? dp(2) : Math.max(1, dp(1)), selected ? Buzz.HONEY : 0x1FFFFFFF),
                Buzz.round(0, r, dp(3), Buzz.TEXT)));
        cell.setTag(check);
        cell.setContentDescription(name.getText());
        cell.setOnClickListener(v -> {
            saveBackground(BackgroundLibrary.MODE_IMAGE, section, f.getName());
            for (View o : cells) {
                ((View) o.getTag()).setVisibility(o == v ? View.VISIBLE : View.GONE);
            }
            Toast.makeText(this, getString(R.string.background_applied, name.getText()), Toast.LENGTH_SHORT).show();
        });
        Buzz.lift(cell, 1.06f, null);
        loadThumb(f, img);
        return cell;
    }

    private final Map<String, Map<String, String>> captions = new HashMap<>();

    /** The picture's caption when its folder has captions.txt, else its file name without the leading number. */
    private String pictureName(File f) {
        // A section's single wallpaper.jpg is named after its section ("Synthwave"), not "Wallpaper".
        if (Config.WALLPAPER_NAME.equals(f.getName())) return ThemeLibrary.label(sectionOf(f));
        File dir = f.getParentFile();
        Map<String, String> names = captions.get(String.valueOf(dir));
        if (names == null) {
            names = dir == null ? Collections.emptyMap() : WallpaperCaptions.read(dir);
            captions.put(String.valueOf(dir), names);
        }
        String caption = names.get(f.getName());
        String label = BackgroundLibrary.imageLabel(f, caption);
        if (caption == null) label = label.replaceFirst("^\\d+[\\s._-]*", "");
        if (label.isEmpty()) return BackgroundLibrary.imageLabel(f, null);
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }

    private void loadThumb(File f, ImageView into) {
        Bitmap cached = thumbs.get(f.getPath());
        if (cached != null) {
            into.setImageBitmap(cached);
            return;
        }
        io.execute(() -> {
            Bitmap b = decode(f, 280);
            if (b == null) return;
            main.post(() -> {
                thumbs.put(f.getPath(), b);
                into.setImageBitmap(b);
            });
        });
    }

    private void saveBackground(String mode, String section, String image) {
        SharedPreferences.Editor e = choices.edit().putString(BackgroundLibrary.MODE_KEY, mode).remove(Config.WALLPAPER_CHOICE_KEY);
        if (section == null) e.remove(BackgroundLibrary.SECTION_KEY);
        else e.putString(BackgroundLibrary.SECTION_KEY, section);
        if (image == null) e.remove(BackgroundLibrary.IMAGE_KEY);
        else e.putString(BackgroundLibrary.IMAGE_KEY, image);
        e.apply();
        changed();
        config = Config.load(this);
    }

    // ---- list screens ---------------------------------------------------------------------------------

    /** A list screen: title, description, then the rows; Back returns to the hub. */
    private LinearLayout listScreen(int title, int description, int crumb) {
        back = this::showHub;
        setCrumbs(R.string.settings_title, crumb);
        setKeys(new String[]{"▲▼", "OK", "↩"}, new int[]{R.string.key_move, R.string.key_choose, R.string.key_back});
        content.removeAllViews();
        LinearLayout across = new LinearLayout(this);
        across.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(across, match());
        LinearLayout intro = new LinearLayout(this);
        intro.setOrientation(LinearLayout.VERTICAL);
        across.addView(intro, new LinearLayout.LayoutParams(dp(270), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView t = Buzz.text(this, 34, Buzz.TEXT, R.string.font_medium);
        t.setSingleLine(false);
        t.setText(title);
        intro.addView(t);
        TextView d = Buzz.text(this, 14, Buzz.TEXT_2, R.string.font_light);
        d.setSingleLine(false);
        d.setLineSpacing(dp(3), 1f);
        d.setText(description);
        LinearLayout.LayoutParams dl = wrap();
        dl.topMargin = dp(10);
        intro.addView(d, dl);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        rows.setPadding(dp(12), dp(6), dp(12), dp(6));
        scroll.addView(rows);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        sl.setMarginStart(dp(34));
        across.addView(scroll, sl);
        return rows;
    }

    private void addRow(LinearLayout rows, View row) {
        LinearLayout.LayoutParams lp = row.getLayoutParams() instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) row.getLayoutParams()
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        lp.bottomMargin = dp(8);
        rows.addView(row, lp);
    }

    private void addSection(LinearLayout rows, int label) {
        TextView k = Buzz.kicker(this, label, Buzz.TEXT_3);
        LinearLayout.LayoutParams lp = wrap();
        lp.topMargin = rows.getChildCount() == 0 ? 0 : dp(14);
        lp.bottomMargin = dp(8);
        rows.addView(k, lp);
    }

    private void showAnimation() {
        LinearLayout rows = listScreen(R.string.wallpaper_animation_label, R.string.animation_desc, R.string.wallpaper_animation_label);
        Runnable parentBack = back;
        View focus = null;
        for (String option : ANIMATION_OPTIONS) {
            boolean on = option.equals(config.wallpaperAnimation);
            View r = radio(animationLabel(option), option.equals(Config.WALLPAPER_ANIMATION_WEATHER)
                    ? getString(R.string.animation_weather_sub) : "", on, () -> {
                choices.edit().putString(Config.WALLPAPER_ANIMATION_KEY, option)
                        .putBoolean(Config.WALLPAPER_MOTION_KEY, Config.wallpaperAnimationEnabled(option)).apply();
                changed();
                config = Config.load(this);
                showAnimation();
            });
            addRow(rows, r);
            if (focus == null || on) focus = r;
        }
        back = parentBack;
        if (focus != null) focus.requestFocus();
    }

    private void showWeather() {
        LinearLayout rows = listScreen(R.string.settings_weather, R.string.weather_desc, R.string.settings_weather);
        boolean on = choices.getBoolean(Config.WEATHER_LIVE_KEY, false);
        View t = toggle(R.string.weather_live_label, on, v -> {
            choices.edit().putBoolean(Config.WEATHER_LIVE_KEY, v).apply();
            changed();
            if (v && weather == null) startWeather();
        });
        addRow(rows, t);
        addRow(rows, actionRow(getString(R.string.weather_open_forecast), reading == null ? "" : getString(R.string.degrees, reading.tempC), () -> {
            if (weather == null) {
                Toast.makeText(this, R.string.weather_turn_on, Toast.LENGTH_SHORT).show();
                return;
            }
            ForecastPanel.show(this, weather, reading, Buzz.HONEY);
        }));
        TextView note = Buzz.text(this, 13, Buzz.TEXT_3, R.string.font_regular);
        note.setSingleLine(false);
        note.setText(R.string.weather_addon_note);
        LinearLayout.LayoutParams nl = wrap();
        nl.topMargin = dp(8);
        rows.addView(note, nl);
        t.requestFocus();
    }

    private void showApps() {
        LinearLayout rows = listScreen(R.string.settings_apps, R.string.apps_desc, R.string.settings_apps);
        View reorder = actionRow(getString(R.string.reorder), "", () -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        addRow(rows, reorder);
        addSection(rows, R.string.apps_classic_only);
        addRow(rows, labeledSegment(R.string.columns_label, new String[]{"4", "5", "6", "7"}, Math.max(0, Math.min(3, config.columns - 4)), i -> {
            choices.edit().putInt("columns", i + 4).apply();
            changed();
        }));
        addRow(rows, labeledSegment(R.string.rows_below_dock_label, new String[]{"0", "1", "2", "3"}, Math.max(0, Math.min(3, config.rowsBelowDock)), i -> {
            choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, i).apply();
            changed();
        }));
        int dockIdx = 0;
        for (int i = 0; i < DOCK_OPACITIES.length; i++) if (DOCK_OPACITIES[i] == config.dockOpacity) dockIdx = i;
        addRow(rows, labeledSegment(R.string.dock_opacity_label, new String[]{"100%", "70%", "50%", "30%"}, dockIdx, i -> {
            choices.edit().putInt(Config.DOCK_OPACITY_KEY, DOCK_OPACITIES[i]).apply();
            changed();
        }));
        boolean advanced = config.animations && config.advancedEffects;
        addRow(rows, toggle(R.string.advanced_effects, advanced, on -> {
            if (!on) {
                choices.edit().putBoolean("advanced_effects", false).apply();
                changed();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.advanced_effects)
                    .setMessage(R.string.advanced_effects_warning)
                    .setPositiveButton(R.string.enable, (d, w) -> {
                        choices.edit().putBoolean("advanced_effects", true).apply();
                        changed();
                    })
                    .setNegativeButton(android.R.string.cancel, (d, w) -> showApps())
                    .show();
        }));
        reorder.requestFocus();
    }

    private void showClock() {
        LinearLayout rows = listScreen(R.string.settings_clock, R.string.clock_desc, R.string.settings_clock);
        int idx = 0;
        for (int i = 0; i < CLOCK_FORMATS.length; i++) if (CLOCK_FORMATS[i].equals(config.clockFormat)) idx = i;
        View seg = labeledSegment(R.string.clock_format_label, new String[]{getString(R.string.clock_format_system),
                getString(R.string.clock_format_12), getString(R.string.clock_format_24)}, idx, i -> {
            choices.edit().putString(Config.CLOCK_FORMAT_KEY, CLOCK_FORMATS[i]).apply();
            changed();
            config = Config.load(this);
        });
        addRow(rows, seg);
        HomeStyle st = HomeStyle.load(this);
        addRow(rows, toggle(R.string.design_greeting, st.greeting, on -> {
            HomeStyle cur = HomeStyle.load(this);
            cur.with(cur.accent, cur.compact, on, cur.inputsRow).save(this);
            changed();
        }));
        seg.requestFocus();
    }

    private void showHome() {
        LinearLayout rows = listScreen(R.string.settings_default_home, R.string.home_desc, R.string.settings_default_home);
        View pick = actionRow(getString(R.string.home_settings), "", () -> open(new Intent(this, HomeSettingsActivity.class)));
        addRow(rows, pick);
        if (config.openOnStartChoice) {
            addRow(rows, actionRow(getString(R.string.open_on_start_label), openOnStartLabel(), this::showOpenOnStartPicker));
        }
        addRow(rows, actionRow(getString(R.string.follow_theme), "", () -> {
            if (!hasOnTvChoices()) {
                Toast.makeText(this, R.string.follow_theme_none, Toast.LENGTH_SHORT).show();
                return;
            }
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(Config.CLOCK_FORMAT_KEY)
                    .remove(Config.HOME_THEME_KEY).remove(Config.WALLPAPER_ROTATION_KEY)
                    .remove(OpenOnStart.KEY).commit();
            changed();
            config = Config.load(this);
            Toast.makeText(this, R.string.follow_theme_done, Toast.LENGTH_SHORT).show();
        }));
        pick.requestFocus();
    }

    // ---- row widgets ----------------------------------------------------------------------------------

    private TextView label(int text, int top) {
        TextView t = Buzz.text(this, 13, Buzz.TEXT_2, R.string.font_regular);
        t.setText(text);
        LinearLayout.LayoutParams lp = wrap();
        lp.topMargin = dp(top);
        lp.bottomMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private StatefulRow row() {
        StatefulRow r = new StatefulRow(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPadding(dp(16), 0, dp(16), 0);
        float rad = dp(14);
        r.setBackground(Buzz.states(Buzz.round(0x8C141824, rad, Math.max(1, dp(1)), 0x14FFFFFF),
                Buzz.round(0x33FFC53D, rad, dp(2), Buzz.HONEY)));
        r.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        Buzz.lift(r, 1.02f, null);
        return r;
    }

    /** A plain LinearLayout; named so rows read clearly in the code above. */
    static final class StatefulRow extends LinearLayout {
        StatefulRow(android.content.Context c) {
            super(c);
        }
    }

    private View actionRow(String name, String value, Runnable go) {
        StatefulRow r = row();
        TextView n = Buzz.text(this, 16, Buzz.TEXT, R.string.font_regular);
        n.setText(name);
        r.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = Buzz.text(this, 14, Buzz.TEXT_3, R.string.font_regular);
        v.setText(value);
        r.addView(v);
        ImageView chev = new ImageView(this);
        chev.setImageResource(R.drawable.ic_chevron);
        chev.setAlpha(0.5f);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(18), dp(18));
        cl.setMarginStart(dp(8));
        r.addView(chev, cl);
        r.setContentDescription(name + (value.isEmpty() ? "" : ", " + value));
        r.setOnClickListener(x -> go.run());
        return r;
    }

    private View radio(String name, String sub, boolean on, Runnable go) {
        StatefulRow r = row();
        View dot = new View(this);
        dot.setBackground(Buzz.round(0, dp(10), on ? dp(6) : dp(2), on ? Buzz.HONEY : 0x66FFFFFF));
        r.addView(dot, new LinearLayout.LayoutParams(dp(20), dp(20)));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMarginStart(dp(12));
        r.addView(text, tl);
        TextView n = Buzz.text(this, 15, Buzz.TEXT, on ? R.string.font_medium : R.string.font_regular);
        n.setText(name);
        text.addView(n);
        if (!sub.isEmpty()) {
            TextView s = Buzz.text(this, 12, Buzz.TEXT_3, R.string.font_regular);
            s.setText(sub);
            text.addView(s);
        }
        r.setContentDescription(name);
        r.setSelected(on);
        r.setOnClickListener(x -> go.run());
        return r;
    }

    private View toggle(int name, boolean initial, Consumer<Boolean> changedTo) {
        StatefulRow r = row();
        TextView n = Buzz.text(this, 15, Buzz.TEXT, R.string.font_regular);
        n.setText(name);
        r.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        FrameLayout track = new FrameLayout(this);
        View knob = new View(this);
        knob.setBackground(Buzz.round(Buzz.TEXT, dp(10), 0, 0));
        track.addView(knob, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER_VERTICAL));
        r.addView(track, new LinearLayout.LayoutParams(dp(46), dp(26)));
        boolean[] state = {initial};
        Runnable paint = () -> {
            track.setBackground(Buzz.round(state[0] ? 0xFF3DD68C : 0x33FFFFFF, dp(13), 0, 0));
            knob.setTranslationX(state[0] ? dp(23) : dp(3));
            r.setContentDescription(getString(name) + ": " + getString(state[0] ? R.string.on : R.string.off));
        };
        paint.run();
        r.setOnClickListener(x -> {
            state[0] = !state[0];
            paint.run();
            changedTo.accept(state[0]);
        });
        r.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        return r;
    }

    /** A segmented control: one focusable chip per option; the chosen one is filled white. */
    private LinearLayout segmented(String[] options, int selected, java.util.function.IntConsumer pick) {
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        seg.setBackground(Buzz.round(0x14FFFFFF, dp(12), 0, 0));
        List<TextView> items = new ArrayList<>();
        int[] chosen = {selected};
        Runnable paint = () -> {
            for (int i = 0; i < items.size(); i++) {
                boolean on = i == chosen[0];
                TextView t = items.get(i);
                t.setTextColor(on ? 0xFF070A11 : Buzz.TEXT_2);
                t.setTypeface(Buzz.font(this, on ? R.string.font_medium : R.string.font_regular));
                float r = dp(9);
                t.setBackground(Buzz.states(Buzz.round(on ? Buzz.TEXT : 0, r, 0, 0),
                        Buzz.round(on ? Buzz.TEXT : 0x33FFFFFF, r, dp(2), Buzz.HONEY)));
            }
        };
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextView t = Buzz.text(this, 13, Buzz.TEXT_2, R.string.font_regular);
            t.setText(options[i]);
            t.setGravity(Gravity.CENTER);
            t.setFocusable(true);
            t.setClickable(true);
            t.setOnClickListener(v -> {
                chosen[0] = index;
                paint.run();
                pick.accept(index);
            });
            seg.addView(t, new LinearLayout.LayoutParams(0, dp(32), 1f));
            items.add(t);
        }
        paint.run();
        seg.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return seg;
    }

    private View labeledSegment(int name, String[] options, int selected, java.util.function.IntConsumer pick) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), 0, dp(8), 0);
        r.setBackground(Buzz.round(0x8C141824, dp(14), Math.max(1, dp(1)), 0x14FFFFFF));
        TextView n = Buzz.text(this, 15, Buzz.TEXT, R.string.font_regular);
        n.setText(name);
        r.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout seg = segmented(options, selected, pick);
        r.addView(seg, new LinearLayout.LayoutParams(dp(54 * options.length), ViewGroup.LayoutParams.WRAP_CONTENT));
        r.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        return r;
    }

    // ---- helpers kept from the earlier screen -------------------------------------------------------

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    showHome();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    String animationLabel(String key) {
        switch (key == null ? Config.WALLPAPER_ANIMATION_NONE : key) {
            case Config.WALLPAPER_ANIMATION_WEATHER: return getString(R.string.animation_weather);
            case WallpaperMotion.RAIN: return getString(R.string.animation_rain);
            case WallpaperMotion.STORM: return getString(R.string.animation_storm);
            case WallpaperMotion.SNOW: return getString(R.string.animation_snow);
            case WallpaperMotion.STARS: return getString(R.string.animation_stars);
            case WallpaperMotion.SUNBEAMS: return getString(R.string.animation_sunbeams);
            case WallpaperMotion.CLOUDS: return getString(R.string.animation_clouds);
            case WallpaperMotion.BUBBLES: return getString(R.string.animation_bubbles);
            case WallpaperMotion.FIREFLIES: return getString(R.string.animation_fireflies);
            case WallpaperMotion.MOTES: return getString(R.string.animation_motes);
            default: return getString(R.string.animation_none);
        }
    }

    private String clockLabel(String format) {
        if ("12".equals(format)) return getString(R.string.clock_format_12);
        if ("24".equals(format)) return getString(R.string.clock_format_24);
        return getString(R.string.clock_format_system);
    }

    private String weatherState() {
        if (!choices.getBoolean(Config.WEATHER_LIVE_KEY, false)) return getString(R.string.off);
        if (reading == null) return getString(R.string.on);
        return reading.city.isEmpty() ? getString(R.string.degrees, reading.tempC)
                : reading.city + " · " + getString(R.string.degrees, reading.tempC);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(Config.CLOCK_FORMAT_KEY) || choices.contains(Config.HOME_THEME_KEY)
                || choices.contains(Config.WALLPAPER_ROTATION_KEY) || choices.contains(OpenOnStart.KEY);
    }

    /** The background on screen now, small: for the previews and the settings backdrop. */
    private Bitmap decodeWallpaper() {
        File f = config.wallpapers.isEmpty() ? config.wallpaper : config.wallpapers.get(0);
        return f == null ? null : decode(f, 960);
    }

    private static Bitmap decode(File f, int width) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= width) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException | NullPointerException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }

    private int dp(float v) {
        return Buzz.dp(this, v);
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
