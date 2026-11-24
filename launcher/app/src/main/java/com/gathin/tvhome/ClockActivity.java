package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

/**
 * The clock screensaver shown by Home itself after home_idle_clock_minutes without input. Some TVs' own
 * screens (CVTE's live TV) do not keep the screen on, so the system screensaver would cut into programmes;
 * this one only ever starts from Home. Any key returns to Home.
 */
public class ClockActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }


    private ClockScene scene;
    private LiveWeather liveWeather;
    private View weatherRow;
    private WeatherIconView weatherIcon;
    private TextView weatherText;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dream_clock);
        Config cfg = Config.load(this);
        ClockFormat.apply(getWindow().getDecorView(), cfg.clockFormat);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE);
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
    protected void onResume() {
        super.onResume();
        scene.start();
        if (liveWeather != null) liveWeather.start();
    }

    @Override
    protected void onPause() {
        if (liveWeather != null) liveWeather.stop();
        scene.stop();
        super.onPause();
        finish();   // never left behind in the back stack
    }

    @Override
    protected void onDestroy() {
        if (liveWeather != null) liveWeather.shutdown();
        super.onDestroy();
    }

    private void onLiveWeather(LiveWeather.Reading reading) {
        if (reading == null || weatherRow == null || weatherIcon == null || weatherText == null) return;
        weatherIcon.setKind(LiveWeather.iconKind(reading.weatherCode, reading.isDay));
        String condition = getString(LiveWeather.conditionRes(reading.weatherCode));
        weatherText.setText(LiveWeather.formatHeader(reading.tempC, condition, reading.city));
        weatherRow.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // The key that wakes the clock should not also act on Home, so it is consumed here.
        if (event.getAction() == KeyEvent.ACTION_UP) finish();
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        finish();
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
