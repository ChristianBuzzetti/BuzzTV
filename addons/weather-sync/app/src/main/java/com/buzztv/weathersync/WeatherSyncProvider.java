package com.buzztv.weathersync;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Headless ContentProvider for BuzzTV live weather.
 * Automatically detects the TV's approximate city via HTTPS IP geolocation (cached 12h),
 * fetches current weather and a 7-day forecast from Open-Meteo in one request (cached 30m), and serves
 * content://com.buzztv.weathersync/current (one row) and content://com.buzztv.weathersync/forecast (one row a day).
 * No background service, no polling.
 */
public final class WeatherSyncProvider extends ContentProvider {
    private static final String TAG = "BuzzTV.WeatherSync";
    private static final String PREFS = "weather_sync_cache";
    /** Older launchers read the first six by index, so new columns only ever go at the end. */
    private static final String[] COLUMNS = {"temp_c", "weather_code", "is_day", "city", "effect", "updated_at",
            "feels_c", "humidity", "wind_kmh"};
    private static final String[] FORECAST_COLUMNS = {"date", "weather_code", "max_c", "min_c", "precip_pct"};
    private static final long WEATHER_TTL_MS = 30 * 60_000L;
    private static final long RETRY_BACKOFF_MS = 5 * 60_000L;
    private static final long LOCATION_TTL_MS = 12 * 60 * 60_000L;
    private static final int TIMEOUT_MS = 6_000;
    private static final String[] GEO_ENDPOINTS = {
            "https://get.geojs.io/v1/ip/geo.json",
            "https://ipwho.is/"
    };

    private final Object lock = new Object();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context ctx = getContext();
        boolean forecast = "forecast".equals(uri.getLastPathSegment());
        if (ctx == null) return new MatrixCursor(forecast ? FORECAST_COLUMNS : COLUMNS);
        WeatherLogic.Reading reading = getOrRefresh(ctx);
        if (forecast) {
            List<WeatherLogic.Day> days = WeatherLogic.decodeForecast(
                    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("forecast", ""));
            MatrixCursor cursor = new MatrixCursor(FORECAST_COLUMNS, days.size());
            for (WeatherLogic.Day d : days) {
                cursor.addRow(new Object[]{d.date, d.weatherCode, d.maxC, d.minC, d.precipPct});
            }
            return cursor;
        }
        MatrixCursor cursor = new MatrixCursor(COLUMNS, 1);
        if (reading != null) {
            cursor.addRow(new Object[]{
                    reading.tempC,
                    reading.weatherCode,
                    reading.isDay ? 1 : 0,
                    reading.city,
                    reading.effect,
                    reading.updatedAt,
                    reading.feelsC,
                    reading.humidity,
                    reading.windKmh
            });
        }
        return cursor;
    }

    private WeatherLogic.Reading getOrRefresh(Context ctx) {
        synchronized (lock) {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();
            WeatherLogic.Reading cached = readCached(prefs);
            // A cache from version 1.0 has no forecast: refresh it once instead of waiting out the TTL.
            boolean hasForecast = !prefs.getString("forecast", "").isEmpty();
            if (cached != null && hasForecast && (now - cached.updatedAt) < WEATHER_TTL_MS) {
                return cached;
            }
            long lastAttempt = prefs.getLong("last_attempt_at", 0L);
            if (lastAttempt > 0 && (now - lastAttempt) < RETRY_BACKOFF_MS) {
                return cached;
            }
            prefs.edit().putLong("last_attempt_at", now).apply();
            WeatherLogic.Location loc = getOrFetchLocation(prefs, now);
            if (loc == null) {
                return cached;
            }
            String body = httpGet(WeatherLogic.openMeteoUrl(loc.latitude, loc.longitude));
            WeatherLogic.Reading fresh = WeatherLogic.parseWeather(body, loc.city, now);
            if (fresh != null) {
                saveCached(prefs, fresh);
                List<WeatherLogic.Day> days = WeatherLogic.parseForecast(body);
                if (!days.isEmpty()) {
                    prefs.edit().putString("forecast", WeatherLogic.encodeForecast(days)).apply();
                }
                return fresh;
            }
            return cached;
        }
    }

    private WeatherLogic.Location getOrFetchLocation(SharedPreferences prefs, long now) {
        long locAt = prefs.getLong("loc_updated_at", 0L);
        if (locAt > 0 && (now - locAt) < LOCATION_TTL_MS && prefs.contains("lat") && prefs.contains("lon")) {
            double lat = Double.longBitsToDouble(prefs.getLong("lat", 0L));
            double lon = Double.longBitsToDouble(prefs.getLong("lon", 0L));
            String city = prefs.getString("city", "");
            return new WeatherLogic.Location(lat, lon, city);
        }
        for (String endpoint : GEO_ENDPOINTS) {
            String body = httpGet(endpoint);
            WeatherLogic.Location loc = WeatherLogic.parseLocation(body);
            if (loc != null) {
                prefs.edit()
                        .putLong("lat", Double.doubleToRawLongBits(loc.latitude))
                        .putLong("lon", Double.doubleToRawLongBits(loc.longitude))
                        .putString("city", loc.city)
                        .putLong("loc_updated_at", now)
                        .apply();
                return loc;
            }
        }
        // Fallback to stale cached location if geolocation endpoints are unreachable
        if (prefs.contains("lat") && prefs.contains("lon")) {
            return new WeatherLogic.Location(
                    Double.longBitsToDouble(prefs.getLong("lat", 0L)),
                    Double.longBitsToDouble(prefs.getLong("lon", 0L)),
                    prefs.getString("city", ""));
        }
        return null;
    }

    private static WeatherLogic.Reading readCached(SharedPreferences prefs) {
        long updatedAt = prefs.getLong("updated_at", 0L);
        if (updatedAt <= 0L) return null;
        return new WeatherLogic.Reading(
                prefs.getInt("temp_c", 0),
                prefs.getInt("weather_code", 0),
                prefs.getInt("is_day", 1) != 0,
                prefs.getString("city", ""),
                prefs.getString("effect", "motes"),
                updatedAt,
                prefs.getInt("feels_c", WeatherLogic.UNKNOWN),
                prefs.getInt("humidity", WeatherLogic.UNKNOWN),
                prefs.getInt("wind_kmh", WeatherLogic.UNKNOWN));
    }

    private static void saveCached(SharedPreferences prefs, WeatherLogic.Reading r) {
        prefs.edit()
                .putInt("temp_c", r.tempC)
                .putInt("weather_code", r.weatherCode)
                .putInt("is_day", r.isDay ? 1 : 0)
                .putString("city", r.city)
                .putString("effect", r.effect)
                .putLong("updated_at", r.updatedAt)
                .putInt("feels_c", r.feelsC)
                .putInt("humidity", r.humidity)
                .putInt("wind_kmh", r.windKmh)
                .apply();
    }

    private static String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "BuzzTV-WeatherSync/1.1");
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                int total = 0;
                while ((n = in.read(buf)) > 0 && total < 65_536) {
                    out.write(buf, 0, n);
                    total += n;
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            Log.w(TAG, "Fetch failed for " + urlStr + ": " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    @Override
    public String getType(Uri uri) {
        return "forecast".equals(uri.getLastPathSegment())
                ? "vnd.android.cursor.dir/vnd.com.buzztv.weathersync.forecast"
                : "vnd.android.cursor.item/vnd.com.buzztv.weathersync.current";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
