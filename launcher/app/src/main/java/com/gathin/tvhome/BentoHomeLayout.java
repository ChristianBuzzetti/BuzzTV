package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sala (bento) Home layout: a 2-column Bento dashboard floating over the live wallpaper.
 * Left column holds a large translucent clock, date, and live weather hero card; right column holds a
 * 2x2 Bento grid of the user's top 4 favorite apps; bottom bar holds 3 equal-width Bento action pills
 * (Apps, Inputs, Settings / hold for Launcher settings).
 */
final class BentoHomeLayout implements CustomHomeLayout {

    private static final int TILE_COUNT = 4;
    private static final float TILE_FOCUS_SCALE = 1.04f;
    private static final float ACTION_FOCUS_SCALE = 1.03f;
    private static final long FOCUS_ANIM_MS = 160L;

    private final Context ctx;
    private final View root;
    private final TextView wifiStatus;
    private final View weatherRow;
    private final WeatherIconView weatherIcon;
    private final TextView weatherText;
    private final View[] tiles = new View[TILE_COUNT];
    private final ImageView[] tileIcons = new ImageView[TILE_COUNT];
    private final TextView[] tileLabels = new TextView[TILE_COUNT];
    private final AppAdapter.Entry[] tileEntries = new AppAdapter.Entry[TILE_COUNT];
    private final View btnApps;
    private final View btnInputs;
    private final View btnSettings;

    private HomeLayoutHost host;

    BentoHomeLayout(Context ctx) {
        this(ctx, null);
    }

    @SuppressLint("InflateParams")
    BentoHomeLayout(Context ctx, ViewGroup parent) {
        this.ctx = ctx;
        this.root = parent != null
                ? LayoutInflater.from(ctx).inflate(R.layout.home_bento, parent, false)
                : LayoutInflater.from(ctx).inflate(R.layout.home_bento, null);
        ClockFormat.apply(root, Config.load(ctx).clockFormat);

        wifiStatus = root.findViewById(R.id.bentoWifiStatus);
        weatherRow = root.findViewById(R.id.bentoWeatherRow);
        // The weather opens the 7-day forecast.
        weatherRow.setFocusable(true);
        weatherRow.setClickable(true);
        int wpad = Buzz.dp(ctx, 10);
        weatherRow.setPadding(wpad, wpad / 2, wpad, wpad / 2);
        weatherRow.setBackground(Buzz.glassPill(ctx, 18, HomeTheme.ACCENT_BENTO));
        weatherRow.setOnClickListener(v -> {
            if (host != null) host.openForecast(HomeTheme.ACCENT_BENTO);
        });
        Buzz.lift(weatherRow, 1.05f, null);
        weatherIcon = root.findViewById(R.id.bentoWeatherIcon);
        weatherText = root.findViewById(R.id.bentoWeatherText);

        int[] tileIds = {R.id.bentoTile0, R.id.bentoTile1, R.id.bentoTile2, R.id.bentoTile3};
        int[] iconIds = {R.id.bentoTileIcon0, R.id.bentoTileIcon1, R.id.bentoTileIcon2, R.id.bentoTileIcon3};
        int[] labelIds = {R.id.bentoTileLabel0, R.id.bentoTileLabel1, R.id.bentoTileLabel2, R.id.bentoTileLabel3};
        float iconRadius = 10f * ctx.getResources().getDisplayMetrics().density;

        for (int i = 0; i < TILE_COUNT; i++) {
            final int index = i;
            tiles[i] = root.findViewById(tileIds[i]);
            tileIcons[i] = root.findViewById(iconIds[i]);
            tileLabels[i] = root.findViewById(labelIds[i]);
            tileIcons[i].setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), iconRadius);
                }
            });
            tileIcons[i].setClipToOutline(true);
            wireFocusScale(tiles[i], TILE_FOCUS_SCALE);
            tiles[i].setOnClickListener(v -> {
                if (host == null) return;
                AppAdapter.Entry entry = tileEntries[index];
                if (entry != null) {
                    host.launchApp(entry);
                } else {
                    host.openAllApps();
                }
            });
            tiles[i].setOnLongClickListener(v -> {
                AppAdapter.Entry entry = tileEntries[index];
                if (host == null || entry == null) return false;
                host.openAppInfo(entry);
                return true;
            });
        }

        btnApps = root.findViewById(R.id.bentoBtnApps);
        btnInputs = root.findViewById(R.id.bentoBtnInputs);
        btnSettings = root.findViewById(R.id.bentoBtnSettings);

        wireFocusScale(btnApps, ACTION_FOCUS_SCALE);
        wireFocusScale(btnInputs, ACTION_FOCUS_SCALE);
        wireFocusScale(btnSettings, ACTION_FOCUS_SCALE);

        btnApps.setOnClickListener(v -> {
            if (host != null) host.openAllApps();
        });
        btnInputs.setOnClickListener(v -> {
            if (host != null) host.openInputs(v);
        });
        btnSettings.setOnClickListener(v -> {
            if (host != null) host.openSettings();
        });
        btnSettings.setOnLongClickListener(v -> {
            if (host != null) host.openLauncherSettings();
            return true;
        });
    }

    @Override
    public View rootView() {
        return root;
    }

    @Override
    public void bind(HomeLayoutHost host) {
        this.host = host;
        onAppsChanged();
        onWifiChanged(host.wifiLabel());
        root.post(() -> {
            if (tiles[0] != null) tiles[0].requestFocus();
        });
    }

    @Override
    public void onAppsChanged() {
        if (host == null) return;
        List<AppAdapter.Entry> picked = pickTopApps(host.dockApps(), host.allApps());
        for (int i = 0; i < TILE_COUNT; i++) {
            AppAdapter.Entry entry = i < picked.size() ? picked.get(i) : null;
            tileEntries[i] = entry;
            if (entry != null) {
                boolean hasBanner = entry.banner != null;
                Drawable art = hasBanner ? entry.banner : entry.icon;
                tileIcons[i].setScaleType(hasBanner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
                tileIcons[i].setImageDrawable(art);
                tileIcons[i].setColorFilter(null);
                tileLabels[i].setText(entry.label);
            } else {
                tileIcons[i].setScaleType(ImageView.ScaleType.FIT_CENTER);
                tileIcons[i].setImageResource(R.drawable.ic_apps);
                tileIcons[i].setColorFilter(0xFFF5B963);
                tileLabels[i].setText(R.string.quick_apps);
            }
        }
    }

    @Override
    public void onWeather(LiveWeather.Reading reading) {
        if (reading == null || weatherRow == null || weatherIcon == null || weatherText == null) return;
        weatherIcon.setKind(LiveWeather.iconKind(reading.weatherCode, reading.isDay));
        String condition = ctx.getString(LiveWeather.conditionRes(reading.weatherCode));
        weatherText.setText(LiveWeather.formatHeader(reading.tempC, condition, reading.city));
        weatherRow.setVisibility(View.VISIBLE);
    }

    @Override
    public void onWifiChanged(String label) {
        if (wifiStatus == null) return;
        wifiStatus.setText(label == null ? "" : label);
    }

    @Override
    public boolean handleBack() {
        return false;
    }

    private static List<AppAdapter.Entry> pickTopApps(List<AppAdapter.Entry> dock, List<AppAdapter.Entry> all) {
        List<AppAdapter.Entry> out = new ArrayList<>(TILE_COUNT);
        Set<String> seen = new HashSet<>();
        addEntries(out, seen, dock);
        addEntries(out, seen, all);
        return out;
    }

    private static void addEntries(List<AppAdapter.Entry> out, Set<String> seen, List<AppAdapter.Entry> source) {
        if (source == null) return;
        for (AppAdapter.Entry e : source) {
            if (out.size() >= TILE_COUNT) return;
            if (e == null || e.kind == AppAdapter.Entry.EMPTY) continue;
            String key = e.pkg != null ? e.pkg : e.label;
            if (key != null && !seen.add(key)) continue;
            out.add(e);
        }
    }

    private static void wireFocusScale(View v, float focusedScale) {
        v.setOnFocusChangeListener((view, hasFocus) -> {
            float target = hasFocus ? focusedScale : 1.0f;
            view.animate()
                    .scaleX(target)
                    .scaleY(target)
                    .setDuration(FOCUS_ANIM_MS)
                    .start();
            view.setTranslationZ(hasFocus ? 8f : 0f);
        });
    }
}
