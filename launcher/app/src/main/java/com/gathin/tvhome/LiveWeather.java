package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
    /** Weather Sync 1.1 and later add these after the first six; an older add-on simply leaves them out. */
    private static final String[] EXTRA_COLUMNS = {"feels_c", "humidity", "wind_kmh"};
    private static final String[] FORECAST_COLUMNS = {"date", "weather_code", "max_c", "min_c", "precip_pct"};
    static final int UNKNOWN = Integer.MIN_VALUE;
    private static final long REFRESH_MS = 30 * 60_000L;

    interface Listener {
        void onWeather(Reading reading);
    }

    interface ForecastListener {
        /** Empty when the add-on is missing, too old to have a forecast, or offline with nothing cached. */
        void onForecast(List<Day> days);
    }

    /** One day of the extended forecast. */
    static final class Day {
        final LocalDate date;
        final int weatherCode;
        final int maxC;
        final int minC;
        /** 0-100, or -1 when unknown. */
        final int precipPct;

        Day(LocalDate date, int weatherCode, int maxC, int minC, int precipPct) {
            this.date = date;
            this.weatherCode = weatherCode;
            this.maxC = maxC;
            this.minC = minC;
            this.precipPct = precipPct;
        }
    }

    static final class Reading {
        final int tempC;
        final int weatherCode;
        final boolean isDay;
        final String city;
        final String effect;
        final long updatedAt;
        final int feelsC;
        final int humidity;
        final int windKmh;

        Reading(int tempC, int weatherCode, boolean isDay, String city, String effect, long updatedAt) {
            this(tempC, weatherCode, isDay, city, effect, updatedAt, UNKNOWN, UNKNOWN, UNKNOWN);
        }

        Reading(int tempC, int weatherCode, boolean isDay, String city, String effect, long updatedAt,
                int feelsC, int humidity, int windKmh) {
            this.tempC = tempC;
            this.weatherCode = weatherCode;
            this.isDay = isDay;
            this.city = city == null ? "" : city.trim();
            this.effect = WallpaperMotion.effect(effect);
            this.updatedAt = updatedAt;
            this.feelsC = feelsC;
            this.humidity = humidity;
            this.windKmh = windKmh;
        }
    }

    private final Context ctx;
    private final Uri uri;
    private final Uri forecastUri;
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
        this.forecastUri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath("forecast")
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
                try (Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        int[] at = new int[COLUMNS.length];
                        for (int i = 0; i < COLUMNS.length; i++) {
                            at[i] = c.getColumnIndex(COLUMNS[i]);
                            if (at[i] < 0) at[i] = i;   // positional, as the first add-on served them
                        }
                        parsed = new Reading(
                                c.getInt(at[0]),
                                c.getInt(at[1]),
                                c.getInt(at[2]) != 0,
                                c.getString(at[3]),
                                c.getString(at[4]),
                                c.getLong(at[5]),
                                optInt(c, EXTRA_COLUMNS[0]),
                                optInt(c, EXTRA_COLUMNS[1]),
                                optInt(c, EXTRA_COLUMNS[2]));
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

    private static int optInt(Cursor c, String column) {
        int i = c.getColumnIndex(column);
        return i < 0 || c.isNull(i) ? UNKNOWN : c.getInt(i);
    }

    /** Reads the 7-day forecast off the main thread and hands it back on it (empty when unavailable). */
    void readForecast(ForecastListener out) {
        try {
            io.execute(() -> {
                List<Day> days = new ArrayList<>();
                try (Cursor c = ctx.getContentResolver().query(forecastUri, FORECAST_COLUMNS, null, null, null)) {
                    while (c != null && c.moveToNext()) {
                        LocalDate date = parseDate(c.getString(0));
                        if (date == null) continue;
                        days.add(new Day(date, c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4)));
                    }
                } catch (RuntimeException ignored) {
                    days = Collections.emptyList();
                }
                final List<Day> result = days;
                main.post(() -> out.onForecast(result));
            });
        } catch (RejectedExecutionException ignored) {
            // Home is being destroyed.
        }
    }

    static LocalDate parseDate(String iso) {
        if (iso == null) return null;
        try {
            return LocalDate.parse(iso.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** "Hoy", "Mañana", then the short weekday name ("jue"), capitalised. */
    static String dayLabel(LocalDate day, LocalDate today, String todayWord, String tomorrowWord, Locale locale) {
        if (day.equals(today)) return todayWord;
        if (day.equals(today.plusDays(1))) return tomorrowWord;
        DayOfWeek dow = day.getDayOfWeek();
        String name = dow.getDisplayName(TextStyle.SHORT, locale).replace(".", "");
        return name.isEmpty() ? name : name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    }

    /**
     * Where a day's min-max range sits on the week's scale, as {start, end} fractions 0-1, for the range bar.
     * A week with one temperature throughout shows a full bar.
     */
    static float[] rangeFractions(int min, int max, int weekMin, int weekMax) {
        if (weekMax <= weekMin) return new float[]{0f, 1f};
        float span = weekMax - weekMin;
        float a = Math.max(0f, Math.min(1f, (min - weekMin) / span));
        float b = Math.max(a, Math.min(1f, (max - weekMin) / span));
        return new float[]{a, b};
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
