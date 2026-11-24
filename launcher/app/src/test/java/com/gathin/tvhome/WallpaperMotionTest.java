package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WallpaperMotionTest {
    @Test public void effectsTheLauncherDrawsAreKept() {
        assertEquals("rain", WallpaperMotion.effect("rain"));
        assertEquals("storm", WallpaperMotion.effect(" STORM "));
        assertEquals("snow", WallpaperMotion.effect("snow"));
        assertEquals("stars", WallpaperMotion.effect(" Stars "));
        assertEquals("sunbeams", WallpaperMotion.effect("SunBeams"));
        assertEquals("clouds", WallpaperMotion.effect(" clouds "));
        assertEquals("bubbles", WallpaperMotion.effect("BUBBLES"));
        assertEquals("fireflies", WallpaperMotion.effect("FireFlies"));
        assertEquals("motes", WallpaperMotion.effect("MOTES"));
        assertEquals("none", WallpaperMotion.effect("fireworks"));
        assertEquals("none", WallpaperMotion.effect(""));
        assertEquals("none", WallpaperMotion.effect(null));
    }

    @Test public void allEffectsListMatchesSupportedEffects() {
        assertEquals(9, WallpaperMotion.ALL_EFFECTS.length);
        for (String kind : WallpaperMotion.ALL_EFFECTS) {
            assertEquals(kind, WallpaperMotion.effect(kind));
            assertTrue(WallpaperMotion.wanted(0, kind));
        }
    }

    @Test public void nothingMovesUnlessAThemeAsks() {
        assertFalse(WallpaperMotion.wanted(0, "none"));
        assertFalse(WallpaperMotion.wanted(0, "lasers"));
        assertTrue(WallpaperMotion.wanted(3, "none"));
        assertTrue(WallpaperMotion.wanted(0, "rain"));
        assertTrue(WallpaperMotion.wanted(0, "storm"));
        assertTrue(WallpaperMotion.wanted(0, "sunbeams"));
        assertTrue(WallpaperMotion.wanted(0, "clouds"));
        assertTrue(WallpaperMotion.wanted(0, "fireflies"));
    }

    @Test public void motionStaysOffWhereItWouldCost() {
        assertTrue(WallpaperMotion.allowed(true, false, false));
        assertFalse(WallpaperMotion.allowed(false, false, false));
        assertFalse(WallpaperMotion.allowed(true, true, false));
        assertFalse(WallpaperMotion.allowed(true, false, true));
    }

    @Test public void theDriftGoesOutAndComesBack() {
        float drift = 0.05f;
        assertEquals(1f, WallpaperMotion.driftScale(drift, 0), 1e-6);
        assertEquals(1.05f, WallpaperMotion.driftScale(drift, WallpaperMotion.DRIFT_PERIOD_MS / 2), 1e-4);
        assertEquals(1f, WallpaperMotion.driftScale(drift, WallpaperMotion.DRIFT_PERIOD_MS), 1e-6);
        for (long t = 0; t < 3 * WallpaperMotion.DRIFT_PERIOD_MS; t += 997) {
            float s = WallpaperMotion.driftScale(drift, t);
            assertTrue(s >= 1f - 1e-6 && s <= 1.05f + 1e-6);
        }
    }

    @Test public void thePanNeverShowsAnEdge() {
        int width = 1920;
        for (float drift : new float[]{0.01f, 0.05f, 0.10f}) {
            for (long t = 0; t < 4 * WallpaperMotion.DRIFT_PERIOD_MS; t += 731) {
                float scale = WallpaperMotion.driftScale(drift, t);
                float margin = (scale - 1f) * width / 2f;
                assertTrue(Math.abs(WallpaperMotion.driftShift(drift, t, width)) <= margin + 1e-3);
            }
        }
        assertEquals(0f, WallpaperMotion.driftShift(0f, 12345, width), 0f);
    }

    @Test public void eachEffectHasAFewDozenParticlesAtMost() {
        for (String kind : WallpaperMotion.ALL_EFFECTS) {
            int n = WallpaperEffectView.count(kind);
            assertTrue(kind, n > 0 && n <= 80);
        }
        assertEquals(0, WallpaperEffectView.count("none"));
        assertEquals(0, WallpaperEffectView.count("unknown"));
    }
}
