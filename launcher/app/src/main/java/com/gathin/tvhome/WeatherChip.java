package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The live weather as one focusable chip: icon, temperature and condition. OK opens the 7-day forecast
 * (ForecastPanel). Hidden until the first reading arrives, so a TV without the Weather Sync add-on never shows
 * an empty chip or gets a focus stop that does nothing.
 */
@android.annotation.SuppressLint("ViewConstructor")
final class WeatherChip extends LinearLayout {
    private final WeatherIconView icon;
    private final TextView label;
    private final boolean withCity;

    /**
     * @param background focus states for the chip; null for a bare text style (Zen), which underlines on focus
     * @param withCity   whether the city follows the condition
     */
    WeatherChip(Context c, float textSp, int textColor, Drawable background, boolean withCity) {
        super(c);
        this.withCity = withCity;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setClickable(true);
        setVisibility(GONE);
        int padH = Buzz.dp(c, background != null ? 16 : 6);
        int padV = Buzz.dp(c, background != null ? 9 : 6);
        setPadding(padH, padV, padH, padV);
        if (background != null) {
            setBackground(background);
        } else {
            setBackground(Buzz.states(Buzz.round(0, Buzz.dp(c, 6), 0, 0),
                    Buzz.round(0x33FFFFFF, Buzz.dp(c, 6), 0, 0)));
        }
        int iconPx = Math.round(textSp * 1.35f * c.getResources().getDisplayMetrics().scaledDensity);
        icon = new WeatherIconView(c);
        LayoutParams ip = new LayoutParams(iconPx, iconPx);
        ip.setMarginEnd(Buzz.dp(c, 9));
        addView(icon, ip);
        label = Buzz.text(c, textSp, textColor, R.string.font_regular);
        addView(label, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        Buzz.lift(this, 1.06f, null);
    }

    void set(LiveWeather.Reading r) {
        if (r == null) return;
        icon.setKind(LiveWeather.iconKind(r.weatherCode, r.isDay));
        String condition = getContext().getString(LiveWeather.conditionRes(r.weatherCode));
        label.setText(LiveWeather.formatHeader(r.tempC, condition, withCity ? r.city : null));
        setContentDescription(label.getText() + ". " + getContext().getString(R.string.forecast_open));
        setVisibility(VISIBLE);
    }

    TextView label() {
        return label;
    }

    /** Text-only chips (Zen) get a shadow so they stay legible on bright wallpapers. */
    void shadow() {
        Buzz.shadow(label);
    }
}
