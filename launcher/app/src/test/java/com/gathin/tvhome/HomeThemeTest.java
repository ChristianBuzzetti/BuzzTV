package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class HomeThemeTest {
    @Test public void marqueeIsTheDefaultAndUnknownIdsFallBackToIt() {
        assertEquals(HomeTheme.MARQUEE, HomeTheme.DEFAULT);
        assertEquals(HomeTheme.MARQUEE, HomeTheme.normalize(null));
        assertEquals(HomeTheme.MARQUEE, HomeTheme.normalize(""));
        assertEquals(HomeTheme.MARQUEE, HomeTheme.normalize("unknown"));
        assertEquals(HomeTheme.AURA, HomeTheme.normalize(" Aura "));
        assertEquals(HomeTheme.ORBIT, HomeTheme.normalize("ORBIT"));
        assertEquals(HomeTheme.STANDARD, HomeTheme.normalize("standard"));
    }

    @Test public void earlierLayoutsStayValidButLeaveTheCarousel() {
        for (String id : new String[]{HomeTheme.GAMING, HomeTheme.MINIMAL, HomeTheme.CINEMA}) {
            assertEquals(id, HomeTheme.normalize(id));
            assertEquals(-1, HomeTheme.index(id));
            assertTrue(HomeTheme.usesCustomLayout(id));
        }
    }

    @Test public void theCarouselOffersSevenLayoutsMarqueeFirstAuraSecond() {
        assertEquals(7, HomeTheme.ALL.length);
        assertEquals(HomeTheme.MARQUEE, HomeTheme.ALL[0]);
        assertEquals(HomeTheme.AURA, HomeTheme.ALL[1]);
        assertEquals(7, new java.util.HashSet<>(java.util.Arrays.asList(HomeTheme.ALL)).size());
        for (int i = 0; i < HomeTheme.ALL.length; i++) {
            String id = HomeTheme.ALL[i];
            assertEquals(i, HomeTheme.index(id));
            assertNotEquals(0, HomeTheme.labelRes(id));
            assertNotEquals(0, HomeTheme.descriptionRes(id));
            assertTrue(LauncherSettingsActivity.thumbParts(id).length > 0);
            assertEquals(!HomeTheme.STANDARD.equals(id), HomeTheme.usesCustomLayout(id));
        }
        assertEquals(0, HomeTheme.focusAccent(HomeTheme.STANDARD));
    }

    @Test public void homeStyleKeepsOnlyOfferedAccents() {
        assertEquals(0, HomeStyle.validAccent(0));
        assertEquals(Buzz.HONEY, HomeStyle.validAccent(Buzz.HONEY));
        assertEquals(0, HomeStyle.validAccent(0xFF123456));
        assertEquals(0.86f, new HomeStyle(0, true, true, true).scale(), 0.001f);
        assertEquals(1f, new HomeStyle(0, false, true, true).scale(), 0.001f);
        assertEquals(Buzz.HONEY, new HomeStyle(0, false, true, true).baseAccent());
    }

    @Test public void blendMixesEveryChannel() {
        assertEquals(0xFF000000, Buzz.blend(0xFF000000, 0xFFFFFFFF, 0f));
        assertEquals(0xFFFFFFFF, Buzz.blend(0xFF000000, 0xFFFFFFFF, 1f));
        assertEquals(0x80808080, Buzz.blend(0x00000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0x3300FF00, Buzz.withAlpha(0xFF00FF00, 0x33));
    }
}
