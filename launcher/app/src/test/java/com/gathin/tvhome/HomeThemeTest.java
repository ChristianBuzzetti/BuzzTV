package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class HomeThemeTest {
    @Test public void normalizeDefaultsUnknownAndBlankToStandard() {
        assertEquals(HomeTheme.STANDARD, HomeTheme.normalize(null));
        assertEquals(HomeTheme.STANDARD, HomeTheme.normalize(""));
        assertEquals(HomeTheme.STANDARD, HomeTheme.normalize("unknown"));
        assertEquals(HomeTheme.CINEMA, HomeTheme.normalize(" Cinema "));
        assertEquals(HomeTheme.BENTO, HomeTheme.normalize("BENTO"));
        assertEquals(HomeTheme.GAMING, HomeTheme.normalize("gaming"));
        assertEquals(HomeTheme.MINIMAL, HomeTheme.normalize("minimal"));
    }

    @Test public void standardLeavesDefaultConfigUntouched() {
        assertTrue(HomeTheme.isStandard(HomeTheme.STANDARD));
        assertTrue(HomeTheme.isStandard(null));
        assertEquals(0, HomeTheme.focusAccent(HomeTheme.STANDARD));
        assertEquals(Config.GLASS_DEFAULT, HomeTheme.glassStyle(HomeTheme.STANDARD, Config.GLASS_DEFAULT));
        assertEquals(1.0f, HomeTheme.focusCornerScale(HomeTheme.STANDARD), 0.001f);
        assertEquals(0, HomeTheme.pillBgColor(HomeTheme.STANDARD));
        assertTrue(HomeTheme.dockBand(HomeTheme.STANDARD, true));
        assertEquals(70, HomeTheme.dockOpacity(HomeTheme.STANDARD, 70));
        assertFalse(HomeTheme.iconOnlyTopBar(HomeTheme.STANDARD, false));
    }

    @Test public void nonStandardThemesDefineDistinctAccentsAndLayouts() {
        assertEquals(HomeTheme.ACCENT_CINEMA, HomeTheme.focusAccent(HomeTheme.CINEMA));
        assertEquals(HomeTheme.ACCENT_BENTO, HomeTheme.focusAccent(HomeTheme.BENTO));
        assertEquals(HomeTheme.ACCENT_GAMING, HomeTheme.focusAccent(HomeTheme.GAMING));
        assertEquals(0, HomeTheme.focusAccent(HomeTheme.MINIMAL));

        assertEquals(Config.GLASS_HALO, HomeTheme.glassStyle(HomeTheme.CINEMA, Config.GLASS_DEFAULT));
        assertEquals(Config.GLASS_HALO, HomeTheme.glassStyle(HomeTheme.BENTO, Config.GLASS_DEFAULT));
        assertEquals(Config.GLASS_RIM, HomeTheme.glassStyle(HomeTheme.GAMING, Config.GLASS_DEFAULT));
        assertEquals(Config.GLASS_HALO, HomeTheme.glassStyle(HomeTheme.MINIMAL, Config.GLASS_DEFAULT));

        assertTrue(HomeTheme.usesCustomLayout(HomeTheme.CINEMA));
        assertTrue(HomeTheme.usesCustomLayout(HomeTheme.BENTO));
        assertTrue(HomeTheme.usesCustomLayout(HomeTheme.GAMING));
        assertFalse(HomeTheme.usesCustomLayout(HomeTheme.STANDARD));
        assertFalse(HomeTheme.usesCustomLayout(HomeTheme.MINIMAL));

        assertTrue(HomeTheme.focusCornerScale(HomeTheme.GAMING) < HomeTheme.focusCornerScale(HomeTheme.STANDARD));
        assertEquals(68f, HomeTheme.clockSizeSp(HomeTheme.BENTO), 0.01f);
        assertFalse(HomeTheme.dockBand(HomeTheme.MINIMAL, true));
        assertTrue(HomeTheme.iconOnlyTopBar(HomeTheme.MINIMAL, false));

        for (String id : HomeTheme.ALL) {
            assertNotEquals(0, HomeTheme.labelRes(id));
        }
    }
}
