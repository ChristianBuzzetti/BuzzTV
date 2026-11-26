package com.buzztv.weathersync;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pure Java parsing and mapping for automatic IP geolocation and Open-Meteo current weather.
 * Tested on the JVM without Android dependencies.
 */
final class WeatherLogic {

    static final class Location {
        final double latitude;
        final double longitude;
        final String city;

        Location(double latitude, double longitude, String city) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.city = city == null ? "" : city.trim();
        }
    }

    static final class Reading {
        final int tempC;
        final int weatherCode;
        final boolean isDay;
        final String city;
        final String effect;
        final long updatedAt;
        /** Feels-like temperature, relative humidity (%) and wind (km/h); Integer.MIN_VALUE when unknown. */
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
            this.effect = effect;
            this.updatedAt = updatedAt;
            this.feelsC = feelsC;
            this.humidity = humidity;
            this.windKmh = windKmh;
        }
    }

    /** One day of the 7-day forecast. */
    static final class Day {
        /** ISO date, yyyy-MM-dd, in the location's own time zone. */
        final String date;
        final int weatherCode;
        final int maxC;
        final int minC;
        /** Highest chance of precipitation that day, 0-100, or -1 when Open-Meteo has none. */
        final int precipPct;

        Day(String date, int weatherCode, int maxC, int minC, int precipPct) {
            this.date = date;
            this.weatherCode = weatherCode;
            this.maxC = maxC;
            this.minC = minC;
            this.precipPct = precipPct;
        }
    }

    static final int UNKNOWN = Integer.MIN_VALUE;
    static final int FORECAST_DAYS = 7;

    private WeatherLogic() {}

    /**
     * Parses JSON from either get.geojs.io/v1/ip/geo.json or ipwho.is/.
     * Returns null if coordinates are missing or invalid.
     */
    static Location parseLocation(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(json);
            if (obj.has("success") && !obj.optBoolean("success", true)) return null;
            if (!obj.has("latitude") || !obj.has("longitude")) return null;
            double lat = parseDouble(obj.opt("latitude"));
            double lon = parseDouble(obj.opt("longitude"));
            if (Double.isNaN(lat) || Double.isNaN(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                return null;
            }
            String city = obj.optString("city", "").trim();
            if (city.isEmpty()) city = obj.optString("region", "").trim();
            return new Location(lat, lon, city);
        } catch (Exception e) {
            return null;
        }
    }

    private static double parseDouble(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    /** Builds the Open-Meteo current weather URL for a location. */
    static String openMeteoUrl(double latitude, double longitude) {
        return String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,weather_code,is_day,apparent_temperature,"
                        + "relative_humidity_2m,wind_speed_10m"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
                        + "precipitation_probability_max"
                        + "&forecast_days=" + FORECAST_DAYS + "&timezone=auto",
                latitude, longitude);
    }

    /** Parses Open-Meteo's current weather JSON response into a Reading, or null on failure. */
    static Reading parseWeather(String json, String city, long nowMs) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(json);
            JSONObject current = root.optJSONObject("current");
            if (current == null || !current.has("temperature_2m") || !current.has("weather_code")) {
                return null;
            }
            int tempC = (int) Math.round(current.getDouble("temperature_2m"));
            int code = current.getInt("weather_code");
            boolean isDay = current.optInt("is_day", 1) != 0;
            String effect = effectFor(code, isDay);
            return new Reading(tempC, code, isDay, city, effect, nowMs,
                    rounded(current, "apparent_temperature"),
                    rounded(current, "relative_humidity_2m"),
                    rounded(current, "wind_speed_10m"));
        } catch (Exception e) {
            return null;
        }
    }

    private static int rounded(JSONObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return UNKNOWN;
        double v = obj.optDouble(key, Double.NaN);
        return Double.isNaN(v) ? UNKNOWN : (int) Math.round(v);
    }

    /** Parses Open-Meteo's {@code daily} block into at most {@link #FORECAST_DAYS} days; empty on failure. */
    static List<Day> parseForecast(String json) {
        if (json == null || json.trim().isEmpty()) return Collections.emptyList();
        try {
            JSONObject daily = new JSONObject(json).optJSONObject("daily");
            if (daily == null) return Collections.emptyList();
            JSONArray time = daily.optJSONArray("time");
            JSONArray code = daily.optJSONArray("weather_code");
            JSONArray max = daily.optJSONArray("temperature_2m_max");
            JSONArray min = daily.optJSONArray("temperature_2m_min");
            JSONArray pop = daily.optJSONArray("precipitation_probability_max");
            if (time == null || code == null || max == null || min == null) return Collections.emptyList();
            int n = Math.min(FORECAST_DAYS, Math.min(time.length(), Math.min(code.length(),
                    Math.min(max.length(), min.length()))));
            List<Day> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                if (code.isNull(i) || max.isNull(i) || min.isNull(i)) continue;
                int p = pop == null || i >= pop.length() || pop.isNull(i) ? -1
                        : (int) Math.round(pop.optDouble(i, -1));
                out.add(new Day(time.optString(i, ""), code.optInt(i, 0),
                        (int) Math.round(max.optDouble(i, 0)), (int) Math.round(min.optDouble(i, 0)),
                        Math.max(-1, Math.min(100, p))));
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** Compact cache form: {@code date,code,max,min,pop;...}. */
    static String encodeForecast(List<Day> days) {
        StringBuilder sb = new StringBuilder();
        for (Day d : days) {
            if (sb.length() > 0) sb.append(';');
            sb.append(d.date).append(',').append(d.weatherCode).append(',').append(d.maxC).append(',')
                    .append(d.minC).append(',').append(d.precipPct);
        }
        return sb.toString();
    }

    static List<Day> decodeForecast(String raw) {
        List<Day> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split(";")) {
            String[] f = part.split(",");
            if (f.length != 5) continue;
            try {
                out.add(new Day(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                        Integer.parseInt(f[3]), Integer.parseInt(f[4])));
            } catch (NumberFormatException ignored) {
                // skip a damaged entry
            }
        }
        return out;
    }

    /**
     * Maps a WMO weather code and day/night flag to a BuzzTV wallpaper_effect:
     * rain (drizzle/rain/showers/thunderstorm), snow (snow/sleet/snow showers),
     * stars (night without precipitation), or motes (daytime clear/cloudy/fog).
     */
    static String effectFor(int wmoCode, boolean isDay) {
        if ((wmoCode >= 51 && wmoCode <= 67)
                || (wmoCode >= 80 && wmoCode <= 82)
                || (wmoCode >= 95 && wmoCode <= 99)) {
            return "rain";
        }
        if ((wmoCode >= 71 && wmoCode <= 77) || wmoCode == 85 || wmoCode == 86) {
            return "snow";
        }
        return isDay ? "motes" : "stars";
    }
}
