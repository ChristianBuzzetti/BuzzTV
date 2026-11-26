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

    @Test
    public void labelsForecastDays() {
        java.time.LocalDate today = java.time.LocalDate.of(2026, 11, 26);   // a Thursday
        java.util.Locale es = new java.util.Locale("es");
        assertEquals("Hoy", LiveWeather.dayLabel(today, today, "Hoy", "Mañana", es));
        assertEquals("Mañana", LiveWeather.dayLabel(today.plusDays(1), today, "Hoy", "Mañana", es));
        assertEquals("Sáb", LiveWeather.dayLabel(today.plusDays(2), today, "Hoy", "Mañana", es));
        assertEquals("Mon", LiveWeather.dayLabel(today.plusDays(4), today, "Today", "Tomorrow",
                java.util.Locale.ENGLISH));
    }

    @Test
    public void placesEachDayOnTheWeeksScale() {
        float[] r = LiveWeather.rangeFractions(15, 25, 10, 30);
        assertEquals(0.25f, r[0], 1e-4f);
        assertEquals(0.75f, r[1], 1e-4f);
        float[] flat = LiveWeather.rangeFractions(20, 20, 20, 20);
        assertEquals(0f, flat[0], 1e-4f);
        assertEquals(1f, flat[1], 1e-4f);
        assertEquals(null, LiveWeather.parseDate("not a date"));
        assertEquals(java.time.LocalDate.of(2026, 11, 27), LiveWeather.parseDate("2026-11-27"));
    }

    @Test
    public void temperatureColoursRunCoolToWarm() {
        assertEquals(0xFF5AA9FF, ForecastPanel.tempColor(-5));
        assertEquals(0xFFFFC53D, ForecastPanel.tempColor(24));
        assertEquals(0xFFFF5A4E, ForecastPanel.tempColor(40));
    }
}
