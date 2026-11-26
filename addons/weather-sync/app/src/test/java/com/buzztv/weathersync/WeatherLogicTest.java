package com.buzztv.weathersync;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WeatherLogicTest {

    @Test
    public void parsesGeoJsLocation() {
        String json = "{\"latitude\":\"-34.6037\",\"longitude\":\"-58.3816\",\"city\":\"Buenos Aires\"}";
        WeatherLogic.Location loc = WeatherLogic.parseLocation(json);
        assertNotNull(loc);
        assertEquals(-34.6037, loc.latitude, 1e-4);
        assertEquals(-58.3816, loc.longitude, 1e-4);
        assertEquals("Buenos Aires", loc.city);
    }

    @Test
    public void parsesIpWhoIsLocation() {
        String json = "{\"success\":true,\"latitude\":40.4168,\"longitude\":-3.7038,\"city\":\"Madrid\"}";
        WeatherLogic.Location loc = WeatherLogic.parseLocation(json);
        assertNotNull(loc);
        assertEquals(40.4168, loc.latitude, 1e-4);
        assertEquals(-3.7038, loc.longitude, 1e-4);
        assertEquals("Madrid", loc.city);
    }

    @Test
    public void rejectsFailedOrInvalidLocation() {
        assertNull(WeatherLogic.parseLocation("{\"success\":false,\"message\":\"rate limited\"}"));
        assertNull(WeatherLogic.parseLocation("{}"));
        assertNull(WeatherLogic.parseLocation("not json"));
    }

    @Test
    public void parsesOpenMeteoWeather() {
        String json = "{\"current\":{\"temperature_2m\":21.6,\"weather_code\":61,\"is_day\":1}}";
        WeatherLogic.Reading r = WeatherLogic.parseWeather(json, "Buenos Aires", 123456L);
        assertNotNull(r);
        assertEquals(22, r.tempC);
        assertEquals(61, r.weatherCode);
        assertTrue(r.isDay);
        assertEquals("Buenos Aires", r.city);
        assertEquals("rain", r.effect);
        assertEquals(123456L, r.updatedAt);
    }

    @Test
    public void mapsWmoCodesToEffects() {
        assertEquals("rain", WeatherLogic.effectFor(51, true));
        assertEquals("rain", WeatherLogic.effectFor(63, false));
        assertEquals("rain", WeatherLogic.effectFor(80, true));
        assertEquals("rain", WeatherLogic.effectFor(95, false));
        assertEquals("snow", WeatherLogic.effectFor(71, true));
        assertEquals("snow", WeatherLogic.effectFor(85, false));
        assertEquals("stars", WeatherLogic.effectFor(0, false));
        assertEquals("stars", WeatherLogic.effectFor(3, false));
        assertEquals("motes", WeatherLogic.effectFor(0, true));
        assertEquals("motes", WeatherLogic.effectFor(2, true));
    }

    @Test
    public void parsesCurrentExtras() {
        String json = "{\"current\":{\"temperature_2m\":21.6,\"weather_code\":1,\"is_day\":1,"
                + "\"apparent_temperature\":23.4,\"relative_humidity_2m\":64,\"wind_speed_10m\":12.6}}";
        WeatherLogic.Reading r = WeatherLogic.parseWeather(json, "", 1L);
        assertNotNull(r);
        assertEquals(23, r.feelsC);
        assertEquals(64, r.humidity);
        assertEquals(13, r.windKmh);
        WeatherLogic.Reading old = WeatherLogic.parseWeather(
                "{\"current\":{\"temperature_2m\":10,\"weather_code\":0}}", "", 1L);
        assertNotNull(old);
        assertEquals(WeatherLogic.UNKNOWN, old.humidity);
    }

    @Test
    public void parsesSevenDayForecastAndRoundTripsTheCache() {
        String json = "{\"daily\":{\"time\":[\"2026-11-26\",\"2026-11-27\",\"2026-11-28\"],"
                + "\"weather_code\":[3,61,null],\"temperature_2m_max\":[28.4,22.5,20],"
                + "\"temperature_2m_min\":[17.6,15.1,12],\"precipitation_probability_max\":[10,null,80]}}";
        java.util.List<WeatherLogic.Day> days = WeatherLogic.parseForecast(json);
        assertEquals(2, days.size());
        assertEquals("2026-11-26", days.get(0).date);
        assertEquals(28, days.get(0).maxC);
        assertEquals(18, days.get(0).minC);
        assertEquals(10, days.get(0).precipPct);
        assertEquals(61, days.get(1).weatherCode);
        assertEquals(-1, days.get(1).precipPct);

        java.util.List<WeatherLogic.Day> back = WeatherLogic.decodeForecast(WeatherLogic.encodeForecast(days));
        assertEquals(2, back.size());
        assertEquals("2026-11-27", back.get(1).date);
        assertEquals(15, back.get(1).minC);
        assertTrue(WeatherLogic.parseForecast("{}").isEmpty());
        assertTrue(WeatherLogic.decodeForecast("garbage;1,2").isEmpty());
    }

    @Test
    public void urlAsksForTheDailyForecast() {
        String url = WeatherLogic.openMeteoUrl(-34.6, -58.4);
        assertTrue(url.contains("daily=weather_code,temperature_2m_max,temperature_2m_min"));
        assertTrue(url.contains("forecast_days=7"));
        assertFalse(url.contains(","+"-58.4000,"));
    }
}
