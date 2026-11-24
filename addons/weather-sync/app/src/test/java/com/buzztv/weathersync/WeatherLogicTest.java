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
}
