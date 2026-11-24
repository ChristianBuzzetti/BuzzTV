package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Console-inspired "Modo Juego" Home layout: split vertical focusable card list on the left (with
 * emerald focus rim), giant dynamic spotlight preview on the right, and a crisp bottom action bar.
 * Floats cleanly over BuzzTV's live wallpaper and particle effects.
 */
final class GamingHomeLayout implements CustomHomeLayout {
    private final Context ctx;
    private final View root;
    private final float dp;
    private final ScrollView scroll;
    private final LinearLayout list;
    private final FrameLayout spotlight;
    private final ImageView spotlightBg;
    private final ImageView spotlightIcon;
    private final TextView spotlightTitle;
    private final TextView spotlightHint;
    private final View weatherRow;
    private final WeatherIconView weatherIcon;
    private final TextView weatherText;
    private final View btnInputs;
    private final View btnWifi;
    private final TextView wifiText;
    private final View btnSettings;

    private HomeLayoutHost host;
    private final List<View> leftCards = new ArrayList<>();
    private int lastFocusedCardId = View.NO_ID;

    GamingHomeLayout(Context context) {
        this(context, null);
    }

    GamingHomeLayout(Context context, ViewGroup parent) {
        this.ctx = context;
        this.dp = context.getResources().getDisplayMetrics().density;
        this.root = LayoutInflater.from(context).inflate(R.layout.home_gaming, parent, false);
        this.scroll = root.findViewById(R.id.gamingScroll);
        this.list = root.findViewById(R.id.gamingList);
        this.spotlight = root.findViewById(R.id.gamingSpotlight);
        this.spotlightBg = root.findViewById(R.id.gamingSpotlightBg);
        this.spotlightIcon = root.findViewById(R.id.gamingSpotlightIcon);
        this.spotlightTitle = root.findViewById(R.id.gamingSpotlightTitle);
        this.spotlightHint = root.findViewById(R.id.gamingSpotlightHint);
        this.weatherRow = root.findViewById(R.id.gamingWeatherRow);
        this.weatherIcon = root.findViewById(R.id.gamingWeatherIcon);
        this.weatherText = root.findViewById(R.id.gamingWeatherText);
        this.btnInputs = root.findViewById(R.id.gamingBtnInputs);
        this.btnWifi = root.findViewById(R.id.gamingBtnWifi);
        this.wifiText = root.findViewById(R.id.gamingWifiText);
        this.btnSettings = root.findViewById(R.id.gamingBtnSettings);

        clipRounded(spotlight, 14f * dp);
    }

    @Override
    public View rootView() {
        return root;
    }

    @Override
    public void bind(HomeLayoutHost host) {
        this.host = host;
        btnInputs.setOnClickListener(v -> host.openInputs(v));
        btnWifi.setOnClickListener(v -> host.openWifi());
        btnSettings.setOnClickListener(v -> host.openSettings());
        btnSettings.setOnLongClickListener(v -> {
            host.openLauncherSettings();
            return true;
        });
        onWifiChanged(host.wifiLabel());
        onAppsChanged();
    }

    @Override
    public void onAppsChanged() {
        if (host == null) return;
        list.removeAllViews();
        leftCards.clear();
        lastFocusedCardId = View.NO_ID;

        String openHint = ctx.getString(R.string.library_open);
        List<AppAdapter.Entry> apps = host.dockApps();
        if (apps == null || apps.isEmpty()) apps = host.allApps();

        boolean spotlightInitialized = false;
        if (apps != null) {
            for (AppAdapter.Entry entry : apps) {
                if (entry == null || entry.kind == AppAdapter.Entry.EMPTY) continue;
                final AppAdapter.Entry app = entry;
                View card = buildCard(
                        app.banner,
                        app.icon,
                        app.label,
                        openHint,
                        v -> host.launchApp(app),
                        () -> showSpotlight(app.banner, app.icon, app.label, openHint));
                leftCards.add(card);
                list.addView(card);
                if (!spotlightInitialized) {
                    showSpotlight(app.banner, app.icon, app.label, openHint);
                    spotlightInitialized = true;
                }
            }
        }

        // Dedicated APPS card
        Drawable appsIcon = ctx.getDrawable(R.drawable.ic_apps);
        String appsLabel = ctx.getString(R.string.quick_apps);
        String appsSub = ctx.getString(R.string.app_library);
        View appsCard = buildCard(
                null,
                appsIcon,
                appsLabel,
                appsSub,
                v -> host.openAllApps(),
                () -> showSpotlight(null, appsIcon, appsLabel, appsSub));
        leftCards.add(appsCard);
        list.addView(appsCard);
        if (!spotlightInitialized) {
            showSpotlight(null, appsIcon, appsLabel, appsSub);
        }

        // Dedicated ENTRADAS card
        Drawable inputsIcon = ctx.getDrawable(R.drawable.ic_input);
        String inputsLabel = ctx.getString(R.string.inputs);
        String inputsSub = ctx.getString(R.string.choose_input);
        View inputsCard = buildCard(
                null,
                inputsIcon,
                inputsLabel,
                inputsSub,
                v -> host.openInputs(v),
                () -> showSpotlight(null, inputsIcon, inputsLabel, inputsSub));
        leftCards.add(inputsCard);
        list.addView(inputsCard);

        wireFocusChain();

        if (!leftCards.isEmpty()) {
            View first = leftCards.get(0);
            lastFocusedCardId = first.getId();
            updateBottomUpTarget(lastFocusedCardId);
            if (root.findFocus() == null) {
                root.post(first::requestFocus);
            }
        }
    }

    @Override
    public void onWeather(LiveWeather.Reading reading) {
        if (reading == null) {
            weatherRow.setVisibility(View.GONE);
            return;
        }
        weatherIcon.setKind(LiveWeather.iconKind(reading.weatherCode, reading.isDay));
        weatherText.setText(LiveWeather.formatHeader(
                reading.tempC,
                ctx.getString(LiveWeather.conditionRes(reading.weatherCode)),
                reading.city));
        weatherRow.setVisibility(View.VISIBLE);
    }

    @Override
    public void onWifiChanged(String label) {
        String text = label == null || label.trim().isEmpty()
                ? ctx.getString(R.string.wifi)
                : label.trim();
        wifiText.setText(text);
    }

    @Override
    public boolean handleBack() {
        View focused = root.findFocus();
        if (focused == btnInputs || focused == btnWifi || focused == btnSettings) {
            View target = lastFocusedCardId != View.NO_ID ? list.findViewById(lastFocusedCardId) : null;
            if (target == null && !leftCards.isEmpty()) target = leftCards.get(0);
            if (target != null) {
                target.requestFocus();
                return true;
            }
        }
        return false;
    }

    private View buildCard(Drawable banner, Drawable icon, String title, String subtitle,
                           View.OnClickListener onClick, Runnable onFocus) {
        LinearLayout card = new LinearLayout(ctx);
        card.setId(View.generateViewId());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackgroundResource(R.drawable.gaming_card_bg);
        int padH = dp(16);
        card.setPadding(padH, 0, padH, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(84));
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        // Left thumbnail slot: banner or centered icon
        FrameLayout thumbHolder = new FrameLayout(ctx);
        LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(dp(64), dp(40));
        thumbHolder.setLayoutParams(thumbLp);
        ImageView thumb = new ImageView(ctx);
        thumb.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (banner != null) {
            thumb.setImageDrawable(banner);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            clipRounded(thumb, 6f * dp);
        } else {
            thumb.setImageDrawable(icon);
            thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER);
            thumb.setLayoutParams(iconLp);
        }
        thumbHolder.addView(thumb);
        card.addView(thumbHolder);

        // Subtle vertical divider
        View divider = new View(ctx);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(dp(1), dp(40));
        divLp.setMarginStart(dp(14));
        divLp.setMarginEnd(dp(14));
        divider.setLayoutParams(divLp);
        divider.setBackgroundColor(0x33FFFFFF);
        divider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(divider);

        // Title + subtitle column
        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
        tvTitle.setAllCaps(true);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tvTitle.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        tvTitle.setIncludeFontPadding(false);
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(ctx);
        tvSub.setText(subtitle);
        tvSub.setSingleLine(true);
        tvSub.setEllipsize(TextUtils.TruncateAt.END);
        tvSub.setTextColor(0x99FFFFFF);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(3);
        tvSub.setLayoutParams(subLp);
        textCol.addView(tvSub);

        card.addView(textCol);

        card.setOnClickListener(onClick);
        card.setOnFocusChangeListener((v, focused) -> {
            if (focused) {
                lastFocusedCardId = v.getId();
                updateBottomUpTarget(lastFocusedCardId);
                onFocus.run();
                ensureVisibleInScroll(v);
            }
        });
        return card;
    }

    private void showSpotlight(Drawable banner, Drawable icon, String title, String hint) {
        if (banner != null) {
            spotlightBg.setImageDrawable(cloneDrawable(banner));
            spotlightBg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            spotlightBg.setBackground(null);
            spotlightIcon.setVisibility(View.GONE);
            spotlightIcon.setImageDrawable(null);
        } else {
            spotlightBg.setImageDrawable(null);
            spotlightBg.setBackgroundResource(R.drawable.gaming_spotlight_fallback);
            spotlightIcon.setImageDrawable(cloneDrawable(icon));
            spotlightIcon.setVisibility(icon != null ? View.VISIBLE : View.GONE);
        }
        spotlightTitle.setText(title == null ? "" : title);
        spotlightHint.setText(hint == null ? "" : hint);
    }

    private static Drawable cloneDrawable(Drawable source) {
        if (source == null) return null;
        Drawable.ConstantState state = source.getConstantState();
        return state != null ? state.newDrawable().mutate() : source;
    }

    private void wireFocusChain() {
        int n = leftCards.size();
        for (int i = 0; i < n; i++) {
            View card = leftCards.get(i);
            int id = card.getId();
            card.setNextFocusLeftId(id);
            card.setNextFocusRightId(id);
            card.setNextFocusUpId(i == 0 ? id : leftCards.get(i - 1).getId());
            card.setNextFocusDownId(i == n - 1 ? R.id.gamingBtnInputs : leftCards.get(i + 1).getId());
        }
        if (n > 0) {
            updateBottomUpTarget(leftCards.get(n - 1).getId());
        }
    }

    private void updateBottomUpTarget(int cardId) {
        btnInputs.setNextFocusUpId(cardId);
        btnWifi.setNextFocusUpId(cardId);
        btnSettings.setNextFocusUpId(cardId);
    }

    private void ensureVisibleInScroll(View card) {
        int top = card.getTop();
        int bottom = card.getBottom();
        int scrollY = scroll.getScrollY();
        int height = scroll.getHeight();
        if (height <= 0) return;
        int margin = dp(12);
        if (top - margin < scrollY) {
            scroll.smoothScrollTo(0, Math.max(0, top - margin));
        } else if (bottom + margin > scrollY + height) {
            scroll.smoothScrollTo(0, bottom + margin - height);
        }
    }

    private int dp(int value) {
        return Math.round(value * dp);
    }

    private static void clipRounded(View view, float radiusPx) {
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radiusPx);
            }
        });
        view.setClipToOutline(true);
    }
}
