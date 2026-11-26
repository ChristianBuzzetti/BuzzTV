package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A live, scaled-down Home for the settings screens: the real layout class, built at the screen's full size
 * with the TV's own apps and wallpaper, then shrunk to fit. It takes no focus and runs no timers of its own,
 * so it costs one layout pass when it is built and nothing afterwards.
 */
final class LayoutPreview {
    private LayoutPreview() {}

    /** A box {@code widthPx} wide (16:9) showing {@code theme} with {@code style}. */
    static FrameLayout create(Context c, String theme, HomeStyle style, Bitmap wallpaper, HomeLayoutHost host,
                              LiveWeather.Reading weather, int widthPx) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        int screenW = Math.max(m.widthPixels, m.heightPixels), screenH = Math.min(m.widthPixels, m.heightPixels);
        int heightPx = Math.round(widthPx * screenH / (float) screenW);
        FrameLayout box = new FrameLayout(c);
        box.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        box.setFocusable(false);
        box.setClipChildren(true);
        box.setLayoutParams(new ViewGroup.LayoutParams(widthPx, heightPx));
        Buzz.roundClip(box, Buzz.dp(c, 14));
        box.setBackgroundColor(0xFF0A0D14);

        FrameLayout screen = new FrameLayout(c);
        screen.setLayoutParams(new FrameLayout.LayoutParams(screenW, screenH));
        float scale = widthPx / (float) screenW;
        screen.setPivotX(0);
        screen.setPivotY(0);
        screen.setScaleX(scale);
        screen.setScaleY(scale);
        if (wallpaper != null) {
            ImageView wp = new ImageView(c);
            wp.setScaleType(ImageView.ScaleType.CENTER_CROP);
            wp.setImageBitmap(wallpaper);
            screen.addView(wp, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        try {
            View content;
            if (HomeTheme.isStandard(theme)) {
                content = classic(c, host);
            } else {
                CustomHomeLayout layout = build(c, theme, style);
                content = layout.rootView();
                screen.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                layout.bind(host);
                if (weather != null) layout.onWeather(weather);
                content = null;
            }
            if (content != null) {
                screen.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        } catch (RuntimeException ignored) {
            // A preview that cannot be drawn shows the wallpaper alone; the setting itself still works.
        }
        box.addView(screen);
        return box;
    }

    static CustomHomeLayout build(Context c, String theme, HomeStyle style) {
        switch (HomeTheme.normalize(theme)) {
            case HomeTheme.AURA: return new AuraHomeLayout(c, style);
            case HomeTheme.PANEL: return new PanelHomeLayout(c, style);
            case HomeTheme.ORBIT: return new OrbitHomeLayout(c, style);
            case HomeTheme.MOSAIC: return new MosaicHomeLayout(c, style);
            case HomeTheme.BENTO: return new BentoHomeLayout(c);
            case HomeTheme.CINEMA: return new CinemaHomeLayout(c);
            case HomeTheme.GAMING: return new ArenaHomeLayout(c);
            case HomeTheme.MINIMAL: return new ZenHomeLayout(c);
            default: return new MarqueeHomeLayout(c, style);
        }
    }

    /** The classic dock and grid, sketched: a clock top left and one row of tiles along the bottom. */
    private static View classic(Context c, HomeLayoutHost host) {
        FrameLayout f = new FrameLayout(c);
        TextView clock = Buzz.clock(c, 52, Buzz.TEXT, R.string.font_light);
        Buzz.shadow(clock);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        cl.leftMargin = Buzz.dp(c, 60);
        cl.topMargin = Buzz.dp(c, 40);
        f.addView(clock, cl);
        LinearLayout dock = new LinearLayout(c);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setPadding(Buzz.dp(c, 60), Buzz.dp(c, 18), Buzz.dp(c, 60), Buzz.dp(c, 18));
        dock.setBackgroundColor(0x80000000);
        List<AppAdapter.Entry> apps = HomeApps.pickFilled(host, 6);
        for (AppAdapter.Entry e : apps) {
            View t = Buzz.appCard(c, e, Buzz.accentOf(e), 16, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Buzz.dp(c, 92), 1f);
            lp.setMarginEnd(Buzz.dp(c, 14));
            dock.addView(t, lp);
        }
        f.addView(dock, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        return f;
    }

    /** The TV's apps for previews, read once from the package manager; nothing in it does anything. */
    static HomeLayoutHost host(Context c) {
        List<AppAdapter.Entry> apps = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        try {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
                if (r.activityInfo == null || c.getPackageName().equals(r.activityInfo.packageName)) continue;
                ComponentName cn = new ComponentName(r.activityInfo.packageName, r.activityInfo.name);
                Drawable banner = r.activityInfo.loadBanner(pm);
                if (banner == null) banner = r.activityInfo.applicationInfo.loadBanner(pm);
                apps.add(new AppAdapter.Entry(String.valueOf(r.loadLabel(pm)), cn, banner, r.loadIcon(pm)));
                if (apps.size() >= 14) break;
            }
        } catch (RuntimeException ignored) {
            // no apps in the preview
        }
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        List<AppAdapter.Entry> fixed = Collections.unmodifiableList(apps);
        return new HomeLayoutHost() {
            public List<AppAdapter.Entry> dockApps() { return fixed; }
            public List<AppAdapter.Entry> allApps() { return fixed; }
            public void launchApp(AppAdapter.Entry app) { }
            public void openInputs(View anchor) { }
            public void openWifi() { }
            public void openSettings() { }
            public void openLauncherSettings() { }
            public void openAllApps() { }
            public String wifiLabel() { return ""; }
            public void openForecast(int accent) { }
            public String greetingName() { return ""; }
        };
    }
}
