package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;
import android.widget.TextView;

/**
 * Screensaver: the launcher's thin clock and date on black, over a slow ambient glow (ClockScene).
 * No network, no images: cheap enough for a 1 GB TV.
 */
public class ClockDream extends DreamService {

    private ClockScene scene;
    private LiveWeather liveWeather;
    private View weatherRow;
    private WeatherIconView weatherIcon;
    private TextView weatherText;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // the theme's locale, as ClockActivity
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);
        Config cfg = Config.load(this);
        ClockFormat.apply(getWindow().getDecorView(), cfg.clockFormat);
        scene = new ClockScene(getWindow().getDecorView());
        scene.useHomeWallpaper(this);
        weatherRow = findViewById(R.id.dreamWeatherRow);
        weatherIcon = findViewById(R.id.dreamWeatherIcon);
        weatherText = findViewById(R.id.dreamWeatherText);
        if (cfg.weatherLive) {
            liveWeather = new LiveWeather(this, new Handler(Looper.getMainLooper()), this::onLiveWeather);
        }
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        scene.start();
        if (liveWeather != null) liveWeather.start();
    }

    @Override
    public void onDreamingStopped() {
        if (liveWeather != null) liveWeather.stop();
        scene.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (liveWeather != null) liveWeather.shutdown();
        if (scene != null) scene.stop();
        super.onDetachedFromWindow();
    }

    private void onLiveWeather(LiveWeather.Reading reading) {
        if (reading == null || weatherRow == null || weatherIcon == null || weatherText == null) return;
        weatherIcon.setKind(LiveWeather.iconKind(reading.weatherCode, reading.isDay));
        String condition = getString(LiveWeather.conditionRes(reading.weatherCode));
        weatherText.setText(LiveWeather.formatHeader(reading.tempC, condition, reading.city));
        weatherRow.setVisibility(View.VISIBLE);
    }
}
