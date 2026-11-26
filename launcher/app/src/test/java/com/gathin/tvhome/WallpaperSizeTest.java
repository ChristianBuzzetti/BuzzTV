package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WallpaperSizeTest {
    @Test public void veryWidePictureIsRefused() {
        assertFalse(WallpaperSize.accepted(65535, 2159));
    }

    @Test public void fourKPictureIsAccepted() {
        assertTrue(WallpaperSize.accepted(3840, 2160));
        assertTrue(WallpaperSize.accepted(1920, 1080));
    }

    @Test public void unreadablePictureIsRefused() {
        assertFalse(WallpaperSize.accepted(-1, -1));
        assertFalse(WallpaperSize.accepted(0, 1080));
    }

    @Test public void fourKOn1080pSamplesByTwo() {
        assertEquals(2, WallpaperSize.sampleSize(3840, 2160, 1920, 1080));
        assertNull(WallpaperSize.coverScale(1920, 1080, 1920, 1080));
    }

    @Test public void photoBetweenSampleSizesIsScaledToTheScreen() {
        // 3000x2000 keeps sample size 1; the cover is limited by the width, so it decodes 1920 wide.
        assertEquals(1, WallpaperSize.sampleSize(3000, 2000, 1920, 1080));
        assertArrayEquals(new int[]{3000, 1920}, WallpaperSize.coverScale(3000, 2000, 1920, 1080));
    }

    @Test public void widePictureIsScaledByItsHeight() {
        assertArrayEquals(new int[]{2000, 1080}, WallpaperSize.coverScale(4000, 2000, 1920, 1080));
    }

    @Test public void smallPictureIsNotScaled() {
        assertEquals(1, WallpaperSize.sampleSize(1280, 720, 1920, 1080));
        assertNull(WallpaperSize.coverScale(1280, 720, 1920, 1080));
    }

    @Test
    public void aPhoneSizedPhotoIsAcceptedOnceSampledDown() {
        int s = WallpaperSize.sampleSize(4032, 3024, 1920, 1080);
        org.junit.Assert.assertTrue(WallpaperSize.accepted(4032 / s, 3024 / s));
        int t = WallpaperSize.sampleSize(65535, 2159, 1920, 1080);
        org.junit.Assert.assertFalse(WallpaperSize.accepted(65535 / t, 2159 / t));
    }

    @Test public void sixteenByNinePicturesFillAnySixteenByNineScreen() {
        assertFalse(WallpaperSize.needsFit(1920, 1080, 1920, 1080));
        assertFalse(WallpaperSize.needsFit(1536, 864, 3840, 2160));
        assertFalse(WallpaperSize.needsFit(1792, 1008, 1280, 720));
        assertFalse(WallpaperSize.needsFit(0, 0, 1920, 1080));
    }

    @Test public void otherShapesAreShownWholeInsteadOfCropped() {
        assertTrue(WallpaperSize.needsFit(1600, 1200, 1920, 1080));   // 4:3
        assertTrue(WallpaperSize.needsFit(3440, 1440, 1920, 1080));   // ultrawide
        assertTrue(WallpaperSize.needsFit(1080, 1920, 1920, 1080));   // a phone photo
        org.junit.Assert.assertArrayEquals(new int[]{240, 0, 1680, 1080},
                WallpaperSize.fitRect(1600, 1200, 1920, 1080));
        org.junit.Assert.assertArrayEquals(new int[]{0, 138, 1920, 942},
                WallpaperSize.fitRect(3440, 1440, 1920, 1080));
    }

    @Test public void coverRectTakesTheCentreInTheScreensShape() {
        org.junit.Assert.assertArrayEquals(new int[]{0, 3, 32, 21}, WallpaperSize.coverRect(32, 24, 1920, 1080));
        org.junit.Assert.assertArrayEquals(new int[]{2, 0, 30, 16}, WallpaperSize.coverRect(32, 16, 1920, 1080));
    }
}
