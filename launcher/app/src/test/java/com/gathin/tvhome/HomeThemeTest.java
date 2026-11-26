package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class HomeThemeTest {
    @Test public void auraIsTheDefaultAndUnknownIdsFallBackToIt() {
        assertEquals(HomeTheme.AURA, HomeTheme.DEFAULT);
        assertEquals(HomeTheme.AURA, HomeTheme.normalize(null));
        assertEquals(HomeTheme.AURA, HomeTheme.normalize(""));
        assertEquals(HomeTheme.AURA, HomeTheme.normalize("unknown"));
        assertEquals(HomeTheme.CINEMA, HomeTheme.normalize(" Cinema "));
        assertEquals(HomeTheme.BENTO, HomeTheme.normalize("BENTO"));
        assertEquals(HomeTheme.GAMING, HomeTheme.normalize("gaming"));
        assertEquals(HomeTheme.MINIMAL, HomeTheme.normalize("minimal"));
        assertEquals(HomeTheme.STANDARD, HomeTheme.normalize("standard"));
    }

    @Test public void onlyStandardKeepsTheDockAndGrid() {
        assertTrue(HomeTheme.isStandard(HomeTheme.STANDARD));
        assertFalse(HomeTheme.isStandard(null));
        assertFalse(HomeTheme.usesCustomLayout(HomeTheme.STANDARD));
        assertEquals(0, HomeTheme.focusAccent(HomeTheme.STANDARD));
        for (String id : HomeTheme.ALL) {
            if (!HomeTheme.STANDARD.equals(id)) {
                assertTrue(id, HomeTheme.usesCustomLayout(id));
                assertNotEquals(id, 0, HomeTheme.focusAccent(id));
            }
            assertNotEquals(0, HomeTheme.labelRes(id));
        }
    }

    @Test public void settingsListAuraFirstAndEveryLayoutOnce() {
        assertEquals(HomeTheme.AURA, HomeTheme.ALL[0]);
        assertEquals(6, HomeTheme.ALL.length);
        assertEquals(6, new java.util.HashSet<>(java.util.Arrays.asList(HomeTheme.ALL)).size());
    }

    @Test public void blendMixesEveryChannel() {
        assertEquals(0xFF000000, Buzz.blend(0xFF000000, 0xFFFFFFFF, 0f));
        assertEquals(0xFFFFFFFF, Buzz.blend(0xFF000000, 0xFFFFFFFF, 1f));
        assertEquals(0x80808080, Buzz.blend(0x00000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0x3300FF00, Buzz.withAlpha(0xFF00FF00, 0x33));
    }
}
