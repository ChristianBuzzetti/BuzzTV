package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Home Cinema ("Cine en casa") architectural layout:
 * top-left title + top-right clock & weather, dramatic warm-gold hero stage with primary/secondary
 * action pills, and 4 large spacious frosted bottom cards (plus an All Apps card).
 */
final class CinemaHomeLayout implements CustomHomeLayout {
    private final Context ctx;
    private final View root;
    private final TextView heroTitle;
    private final TextView heroSubtitle;
    private final View btnPlay;
    private final View btnInputs;
    private final View btnSettings;
    private final View weatherRow;
    private final WeatherIconView weatherIcon;
    private final TextView weatherText;
    private final LinearLayout cardsRow;
    private final float density;

    private HomeLayoutHost host;
    private AppAdapter.Entry selectedApp;
    private View lastFocusedCard;

    CinemaHomeLayout(Context ctx) {
        this.ctx = ctx;
        this.density = ctx.getResources().getDisplayMetrics().density;
        this.root = LayoutInflater.from(ctx).inflate(R.layout.home_cinema, null, false);
        this.heroTitle = root.findViewById(R.id.cinemaHeroTitle);
        this.heroSubtitle = root.findViewById(R.id.cinemaHeroSubtitle);
        this.btnPlay = root.findViewById(R.id.cinemaBtnPlay);
        this.btnInputs = root.findViewById(R.id.cinemaBtnInputs);
        this.btnSettings = root.findViewById(R.id.cinemaBtnSettings);
        this.weatherRow = root.findViewById(R.id.cinemaWeatherRow);
        this.weatherIcon = root.findViewById(R.id.cinemaWeatherIcon);
        this.weatherText = root.findViewById(R.id.cinemaWeatherText);
        this.cardsRow = root.findViewById(R.id.cinemaCardsRow);

        btnPlay.setOnClickListener(v -> {
            if (host == null) return;
            if (selectedApp != null) host.launchApp(selectedApp);
            else host.openAllApps();
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

        View.OnKeyListener actionKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && lastFocusedCard != null) {
                lastFocusedCard.requestFocus();
                return true;
            }
            return keyCode == KeyEvent.KEYCODE_DPAD_UP;
        };
        btnPlay.setOnKeyListener(actionKeys);
        btnInputs.setOnKeyListener(actionKeys);
        btnSettings.setOnKeyListener(actionKeys);
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
        cardsRow.removeAllViews();
        List<AppAdapter.Entry> apps = new ArrayList<>(host.dockApps());
        if (apps.isEmpty()) apps.addAll(host.allApps());

        int cardW = Math.round(198 * density);
        int cardH = Math.round(118 * density);
        int gap = Math.round(16 * density);

        int count = Math.min(apps.size(), 6);
        for (int i = 0; i < count; i++) {
            final AppAdapter.Entry entry = apps.get(i);
            View card = buildCard(entry.label, entry.banner != null ? entry.banner : entry.icon,
                    entry.banner != null, cardW, cardH, i > 0 ? gap : 0);
            card.setOnClickListener(v -> host.launchApp(entry));
            card.setOnFocusChangeListener((v, focused) -> {
                animateCardFocus(v, focused);
                if (focused) {
                    selectedApp = entry;
                    lastFocusedCard = v;
                    heroTitle.setText(entry.label);
                    heroSubtitle.setText(R.string.cinema_subtitle);
                }
            });
            card.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    btnPlay.requestFocus();
                    return true;
                }
                return keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
            });
            cardsRow.addView(card);
        }

        // Final "Apps" card so all installed apps are always 1 click away.
        Drawable appsIcon = ctx.getDrawable(R.drawable.ic_apps_pill);
        View allAppsCard = buildCard(ctx.getString(R.string.quick_apps), appsIcon, false,
                cardW, cardH, cardsRow.getChildCount() > 0 ? gap : 0);
        allAppsCard.setOnClickListener(v -> host.openAllApps());
        allAppsCard.setOnFocusChangeListener((v, focused) -> {
            animateCardFocus(v, focused);
            if (focused) {
                selectedApp = null;
                lastFocusedCard = v;
                heroTitle.setText(R.string.quick_apps);
                heroSubtitle.setText(R.string.cinema_all_apps_subtitle);
            }
        });
        allAppsCard.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                btnPlay.requestFocus();
                return true;
            }
            return keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
        });
        cardsRow.addView(allAppsCard);

        if (!apps.isEmpty()) {
            selectedApp = apps.get(0);
            heroTitle.setText(selectedApp.label);
            heroSubtitle.setText(R.string.cinema_subtitle);
        }
        if (cardsRow.getChildCount() > 0) {
            lastFocusedCard = cardsRow.getChildAt(0);
            cardsRow.post(() -> {
                if (cardsRow.getChildCount() > 0 && !root.hasFocus()) {
                    cardsRow.getChildAt(0).requestFocus();
                }
            });
        }
    }

    private View buildCard(String title, Drawable art, boolean isBanner, int w, int h, int startMargin) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackgroundResource(R.drawable.cinema_card_bg);
        card.setFocusable(true);
        card.setClickable(true);
        int pad = Math.round(12 * density);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMarginStart(startMargin);
        card.setLayoutParams(lp);

        ImageView img = new ImageView(ctx);
        if (isBanner) {
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    Math.round(112 * density), Math.round(62 * density));
            img.setLayoutParams(ilp);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            int iconSize = Math.round(44 * density);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(iconSize, iconSize);
            img.setLayoutParams(ilp);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
        img.setImageDrawable(art);
        card.addView(img);

        TextView label = new TextView(ctx);
        label.setText(title);
        label.setTextColor(Color.WHITE);
        label.setTextSize(15f);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Math.round(8 * density);
        label.setLayoutParams(tlp);
        card.addView(label);
        return card;
    }

    private void animateCardFocus(View v, boolean focused) {
        v.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f)
                .setDuration(140).start();
    }

    @Override
    public void onWeather(LiveWeather.Reading reading) {
        if (reading == null) return;
        weatherIcon.setKind(LiveWeather.iconKind(reading.weatherCode, reading.isDay));
        String condition = ctx.getString(LiveWeather.conditionRes(reading.weatherCode));
        weatherText.setText(LiveWeather.formatHeader(reading.tempC, condition, reading.city));
        weatherRow.setVisibility(View.VISIBLE);
    }

    @Override
    public void onWifiChanged(String label) {
        // Cinema layout keeps the top bar ultra-clean (brand + weather + clock).
    }

    @Override
    public boolean handleBack() {
        return false;
    }
}
