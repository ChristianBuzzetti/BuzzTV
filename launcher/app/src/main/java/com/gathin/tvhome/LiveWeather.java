package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Reads current weather from the companion BuzzTV Weather Sync add-on
 * ({@code content://com.buzztv.weathersync/current}) off the main thread.
 * The launcher itself stays 100% offline with no INTERNET permission.
 */
final class LiveWeather {
    static final String AUTHORITY = "com.buzztv.weathersync";
    private static final String[] COLUMNS = {"temp_c", "weather_code", "is_day", "city", "effect", "updated_at"};
    private static final long REFRESH_MS = 30 * 60_000L;

    interface Listener {
        void onWeather(Reading reading);
    }

    static final class Reading {
        final int tempC;
        final int weatherCode;
        final boolean isDay;
        final String city;
        final String effect;
        final long updatedAt;

        Reading(int tempC, int weatherCode, boolean isDay, String city, String effect, long updatedAt) {
            this.tempC = tempC;
            this.weatherCode = weatherCode;
            this.isDay = isDay;
            this.city = city == null ? "" : city.trim();
            this.effect = WallpaperMotion.effect(effect);
            this.updatedAt = updatedAt;
        }
    }

    private final Context ctx;
    private final Uri uri;
    private final Handler main;
    private final Listener listener;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean running;
    private Reading last;
    private final Runnable refreshTick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            read();
            main.postDelayed(this, REFRESH_MS);
        }
    };

    LiveWeather(Context ctx, Handler main, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.uri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath("current")
                .build();
        this.main = main;
        this.listener = listener;
    }

    /** Home is on screen: read now and refresh every 30 minutes while visible. */
    void start() {
        if (running) return;
        running = true;
        read();
        main.removeCallbacks(refreshTick);
        main.postDelayed(refreshTick, REFRESH_MS);
    }

    /** Home is off screen: stop scheduled refreshes. */
    void stop() {
        running = false;
        main.removeCallbacks(refreshTick);
    }

    void shutdown() {
        stop();
        io.shutdownNow();
    }

    Reading last() {
        return last;
    }

    void read() {
        try {
            io.execute(() -> {
                Reading parsed = null;
                try (Cursor c = ctx.getContentResolver().query(uri, COLUMNS, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        parsed = new Reading(
                                c.getInt(0),
                                c.getInt(1),
                                c.getInt(2) != 0,
                                c.getString(3),
                                c.getString(4),
                                c.getLong(5));
                    }
                } catch (RuntimeException ignored) {
                    // Add-on not installed or unavailable: keep whatever was shown.
                    return;
                }
                if (parsed == null) return;
                final Reading result = parsed;
                main.post(() -> {
                    last = result;
                    if (running) listener.onWeather(result);
                });
            });
        } catch (RejectedExecutionException ignored) {
            // Home is being destroyed.
        }
    }

    /** Maps WMO weather code + day/night to WeatherIconView's icon kind. */
    static String iconKind(int wmoCode, boolean isDay) {
        if (wmoCode >= 95 && wmoCode <= 99) return "storm";
        if ((wmoCode >= 71 && wmoCode <= 77) || wmoCode == 85 || wmoCode == 86) return "snow";
        if ((wmoCode >= 51 && wmoCode <= 67) || (wmoCode >= 80 && wmoCode <= 82)) return "rain";
        if (wmoCode == 45 || wmoCode == 48) return "fog";
        if (wmoCode == 3) return "cloud";
        if (wmoCode == 1 || wmoCode == 2) return isDay ? "partly_day" : "partly_night";
        return isDay ? "sun" : "moon";
    }

    /** Maps WMO weather code to a localized condition string resource. */
    static int conditionRes(int wmoCode) {
        if (wmoCode >= 95 && wmoCode <= 99) return R.string.weather_storm;
        if ((wmoCode >= 71 && wmoCode <= 77) || wmoCode == 85 || wmoCode == 86) return R.string.weather_snow;
        if ((wmoCode >= 61 && wmoCode <= 67) || (wmoCode >= 80 && wmoCode <= 82)) return R.string.weather_rain;
        if (wmoCode >= 51 && wmoCode <= 57) return R.string.weather_drizzle;
        if (wmoCode == 45 || wmoCode == 48) return R.string.weather_fog;
        if (wmoCode == 3) return R.string.weather_cloudy;
        if (wmoCode == 1 || wmoCode == 2) return R.string.weather_partly_cloudy;
        return R.string.weather_clear;
    }

    /** Formats the compact header line: "22° · Despejado · Buenos Aires", or without city if blank. */
    static String formatHeader(int tempC, String condition, String city) {
        StringBuilder sb = new StringBuilder();
        sb.append(tempC).append('\u00B0');
        if (condition != null && !condition.trim().isEmpty()) {
            sb.append(" \u00B7 ").append(condition.trim());
        }
        if (city != null && !city.trim().isEmpty()) {
            sb.append(" \u00B7 ").append(city.trim());
        }
        return sb.toString();
    }

    /** The wallpaper effect to apply for a live weather reading. */
    static String effectFor(Reading r) {
        if (r == null) return WallpaperMotion.NONE;
        return effectFor(r.weatherCode, r.isDay);
    }

    /** Maps WMO weather code + day/night directly to a rich wallpaper effect. */
    static String effectFor(int wmoCode, boolean isDay) {
        if (wmoCode >= 95 && wmoCode <= 99) return WallpaperMotion.STORM;
        if ((wmoCode >= 71 && wmoCode <= 77) || wmoCode == 85 || wmoCode == 86) return WallpaperMotion.SNOW;
        if ((wmoCode >= 51 && wmoCode <= 67) || (wmoCode >= 80 && wmoCode <= 82)) return WallpaperMotion.RAIN;
        if (wmoCode == 3 || wmoCode == 45 || wmoCode == 48) {
            return isDay ? WallpaperMotion.CLOUDS : WallpaperMotion.STARS;
        }
        return isDay ? WallpaperMotion.SUNBEAMS : WallpaperMotion.STARS;
    }
}
