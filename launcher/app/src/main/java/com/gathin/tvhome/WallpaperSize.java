package com.gathin.tvhome;

/**
 * How big a theme's wallpaper may be and what size it is decoded at (#166). A 1 GB TV cannot afford a
 * picture decoded at full size: a 65535x2159 image is about 283 MB even in RGB_565.
 */
final class WallpaperSize {
    private WallpaperSize() { }

    /** 4K UHD (3840x2160, 8.3 M pixels) and a little more; anything bigger is refused. */
    static final long MAX_PIXELS = 8_400_000L;

    static boolean accepted(int width, int height) {
        return width > 0 && height > 0 && (long) width * height <= MAX_PIXELS;
    }

    /** The largest power-of-two sample size that still covers the screen in both directions. */
    static int sampleSize(int width, int height, int screenW, int screenH) {
        int sample = 1;
        while (width / (sample * 2) >= screenW && height / (sample * 2) >= screenH) sample *= 2;
        return sample;
    }

    /**
     * {inDensity, inTargetDensity} that scale a (sampled) picture down to what CENTER_CROP shows: the
     * side that limits the cover lands on the screen size. Null when it is already that small.
     */
    static int[] coverScale(int width, int height, int screenW, int screenH) {
        if ((long) screenW * height >= (long) screenH * width) {   // width limits the cover
            return width > screenW ? new int[]{width, screenW} : null;
        }
        return height > screenH ? new int[]{height, screenH} : null;
    }

    /**
     * True when a picture's shape is more than 3 % away from the screen's. Cropping such a picture to fill the
     * screen cuts off a part people notice (a 4:3 photo loses a quarter of its height), so it is shown whole
     * instead, framed by a soft wash of its own colours (WallpaperPlayer).
     */
    static boolean needsFit(int width, int height, int screenW, int screenH) {
        if (width <= 0 || height <= 0 || screenW <= 0 || screenH <= 0) return false;
        double picture = (double) width / height, screen = (double) screenW / screenH;
        return Math.abs(picture - screen) / screen > 0.03;
    }

    /** {left, top, right, bottom} of the whole picture scaled to fit inside the screen, centred. */
    static int[] fitRect(int width, int height, int screenW, int screenH) {
        double k = Math.min((double) screenW / width, (double) screenH / height);
        int w = (int) Math.round(width * k), h = (int) Math.round(height * k);
        int left = (screenW - w) / 2, top = (screenH - h) / 2;
        return new int[]{left, top, left + w, top + h};
    }

    /** {left, top, right, bottom} of the centre part of a picture that has the screen's shape (a cover crop). */
    static int[] coverRect(int width, int height, int screenW, int screenH) {
        if ((long) width * screenH > (long) height * screenW) {   // wider than the screen: trim the sides
            int w = (int) Math.round((double) height * screenW / screenH);
            int left = (width - w) / 2;
            return new int[]{left, 0, left + w, height};
        }
        int h = (int) Math.round((double) width * screenH / screenW);
        int top = (height - h) / 2;
        return new int[]{0, top, width, top + h};
    }
}
