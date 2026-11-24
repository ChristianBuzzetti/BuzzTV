package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LiveWeatherTest {

    @Test
    public void mapsWmoCodesToIcons() {
        assertEquals("sun", LiveWeather.iconKind(0, true));
        assertEquals("moon", LiveWeather.iconKind(0, false));
        assertEquals("partly_day", LiveWeather.iconKind(2, true));
        assertEquals("partly_night", LiveWeather.iconKind(1, false));
        assertEquals("cloud", LiveWeather.iconKind(3, true));
        assertEquals("fog", LiveWeather.iconKind(45, true));
        assertEquals("rain", LiveWeather.iconKind(61, true));
        assertEquals("snow", LiveWeather.iconKind(73, false));
        assertEquals("storm", LiveWeather.iconKind(95, false));
    }

    @Test
    public void formatsHeaderWithAndWithoutCity() {
        assertEquals("22° · Despejado · Buenos Aires", LiveWeather.formatHeader(22, "Despejado", "Buenos Aires"));
        assertEquals("14° · Lluvia", LiveWeather.formatHeader(14, "Lluvia", ""));
        assertEquals("14° · Lluvia", LiveWeather.formatHeader(14, "Lluvia", null));
    }

    @Test
    public void resolvesWeatherEffectFromWmoCodeAndDayNight() {
        assertEquals(WallpaperMotion.NONE, LiveWeather.effectFor(null));

        // Thunderstorms -> STORM (even if older weather-sync cached "rain")
        assertEquals(WallpaperMotion.STORM, LiveWeather.effectFor(new LiveWeather.Reading(20, 95, true, "BA", "rain", 1L)));
        assertEquals(WallpaperMotion.STORM, LiveWeather.effectFor(new LiveWeather.Reading(17, 99, false, "BA", "rain", 1L)));

        // Drizzle / rain / showers -> RAIN
        assertEquals(WallpaperMotion.RAIN, LiveWeather.effectFor(new LiveWeather.Reading(16, 51, true, "BA", "", 1L)));
        assertEquals(WallpaperMotion.RAIN, LiveWeather.effectFor(new LiveWeather.Reading(18, 63, true, "BA", "rain", 1L)));
        assertEquals(WallpaperMotion.RAIN, LiveWeather.effectFor(new LiveWeather.Reading(14, 82, false, "BA", "", 1L)));

        // Snow / sleet / snow showers -> SNOW
        assertEquals(WallpaperMotion.SNOW, LiveWeather.effectFor(new LiveWeather.Reading(-2, 71, true, "BA", "", 1L)));
        assertEquals(WallpaperMotion.SNOW, LiveWeather.effectFor(new LiveWeather.Reading(-4, 77, false, "BA", "", 1L)));
        assertEquals(WallpaperMotion.SNOW, LiveWeather.effectFor(new LiveWeather.Reading(-1, 86, true, "BA", "", 1L)));

        // Fog / overcast -> CLOUDS by day, STARS at night (even if older cache had "motes")
        assertEquals(WallpaperMotion.CLOUDS, LiveWeather.effectFor(new LiveWeather.Reading(15, 3, true, "BA", "motes", 1L)));
        assertEquals(WallpaperMotion.STARS, LiveWeather.effectFor(new LiveWeather.Reading(12, 3, false, "BA", "motes", 1L)));
        assertEquals(WallpaperMotion.CLOUDS, LiveWeather.effectFor(new LiveWeather.Reading(11, 45, true, "BA", "", 1L)));
        assertEquals(WallpaperMotion.STARS, LiveWeather.effectFor(new LiveWeather.Reading(9, 48, false, "BA", "", 1L)));

        // Clear / partly cloudy -> SUNBEAMS by day, STARS at night (even if older cache had "motes")
        assertEquals(WallpaperMotion.SUNBEAMS, LiveWeather.effectFor(new LiveWeather.Reading(25, 0, true, "BA", "motes", 1L)));
        assertEquals(WallpaperMotion.SUNBEAMS, LiveWeather.effectFor(new LiveWeather.Reading(23, 2, true, "BA", "", 1L)));
        assertEquals(WallpaperMotion.STARS, LiveWeather.effectFor(new LiveWeather.Reading(15, 0, false, "BA", "", 1L)));
        assertEquals(WallpaperMotion.STARS, LiveWeather.effectFor(new LiveWeather.Reading(14, 1, false, "BA", "", 1L)));
    }
}
