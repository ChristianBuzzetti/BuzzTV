package com.buzztv.weathersync;

import org.json.JSONObject;

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

        Reading(int tempC, int weatherCode, boolean isDay, String city, String effect, long updatedAt) {
            this.tempC = tempC;
            this.weatherCode = weatherCode;
            this.isDay = isDay;
            this.city = city == null ? "" : city.trim();
            this.effect = effect;
            this.updatedAt = updatedAt;
        }
    }

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
                        + "&current=temperature_2m,weather_code,is_day&timezone=auto",
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
            return new Reading(tempC, code, isDay, city, effect, nowMs);
        } catch (Exception e) {
            return null;
        }
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
