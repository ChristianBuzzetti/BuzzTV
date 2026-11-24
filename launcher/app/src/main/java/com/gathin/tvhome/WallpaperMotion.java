package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import java.util.Locale;

/**
 * Optional movement on the wallpaper, off unless a theme asks for it: wallpaper_drift zooms and pans the picture
 * very slowly (a minute per sway), wallpaper_effect draws a light particle layer over it (WallpaperEffectView).
 *
 * Built for 1 GB TVs: one throttled tick (20 frames a second for the drift alone, 30 with an effect) runs only
 * while Home is in front; it stops when an app, the idle clock or a dialog covers Home, never runs with
 * animations off, reduced_motion or on a device Android reports as low on RAM, and allocates nothing per frame.
 * The drift moves the picture through view properties, so the bitmap is never redrawn, only composited.
 */
final class WallpaperMotion {
    static final String NONE = "none";
    static final String RAIN = "rain";
    static final String STORM = "storm";
    static final String SNOW = "snow";
    static final String STARS = "stars";
    static final String SUNBEAMS = "sunbeams";
    static final String CLOUDS = "clouds";
    static final String BUBBLES = "bubbles";
    static final String FIREFLIES = "fireflies";
    static final String MOTES = "motes";
    static final String[] ALL_EFFECTS = {
            RAIN, STORM, SNOW, STARS, SUNBEAMS, CLOUDS, BUBBLES, FIREFLIES, MOTES
    };
    /** One full sway of the drift, there and back. */
    static final long DRIFT_PERIOD_MS = 60_000L;
    static final long DRIFT_FRAME_MS = 50L;
    static final long EFFECT_FRAME_MS = 33L;

    private final View image;
    private final WallpaperEffectView effect;
    private final float drift;
    private final long frameMs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long origin = SystemClock.uptimeMillis();
    private boolean running;
    private final Runnable tick = this::tick;

    /** @param effect null for the drift alone */
    WallpaperMotion(View image, WallpaperEffectView effect, int driftPercent) {
        this.image = image;
        this.effect = effect;
        this.drift = Math.max(0, Math.min(10, driftPercent)) / 100f;
        this.frameMs = effect != null ? EFFECT_FRAME_MS : DRIFT_FRAME_MS;
    }

    /** A theme's wallpaper_effect, lower-cased; anything this launcher does not draw reads as none. */
    static String effect(String raw) {
        String v = raw == null ? NONE : raw.trim().toLowerCase(Locale.ROOT);
        switch (v) {
            case RAIN:
            case STORM:
            case SNOW:
            case STARS:
            case SUNBEAMS:
            case CLOUDS:
            case BUBBLES:
            case FIREFLIES:
            case MOTES:
                return v;
            default:
                return NONE;
        }
    }

    /** Whether any movement may run on this TV with these settings. */
    static boolean allowed(boolean animations, boolean reducedMotion, boolean lowRamDevice) {
        return animations && !reducedMotion && !lowRamDevice;
    }

    /** True when the theme asks for something to move at all. */
    static boolean wanted(int driftPercent, String effect) {
        return driftPercent > 0 || !NONE.equals(effect(effect));
    }

    /** The picture's scale at {@code elapsed} ms: 1, out to 1 + drift and back, eased at both ends. */
    static float driftScale(float drift, long elapsed) {
        return 1f + drift * driftAmount(elapsed);
    }

    /** 0 to 1 and back over DRIFT_PERIOD_MS, as a cosine so the turn has no jolt. */
    static float driftAmount(long elapsed) {
        double phase = (elapsed % DRIFT_PERIOD_MS) / (double) DRIFT_PERIOD_MS * 2 * Math.PI;
        return (float) (0.5 - 0.5 * Math.cos(phase));
    }

    /** Horizontal pan in px, inside the margin the zoom opens, so no edge of the picture ever shows. */
    static float driftShift(float drift, long elapsed, int width) {
        float scale = driftScale(drift, elapsed);
        double phase = (elapsed % (DRIFT_PERIOD_MS * 2)) / (double) (DRIFT_PERIOD_MS * 2) * 2 * Math.PI;
        return (float) ((scale - 1f) * width * 0.4 * Math.sin(phase));
    }

    void start() {
        if (running) return;
        running = true;
        if (effect != null) effect.setRunning(true);
        handler.post(tick);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(tick);
        if (effect != null) effect.setRunning(false);
    }

    private void tick() {
        if (!running) return;
        long elapsed = SystemClock.uptimeMillis() - origin;
        if (drift > 0f) {
            float scale = driftScale(drift, elapsed);
            image.setScaleX(scale);
            image.setScaleY(scale);
            image.setTranslationX(driftShift(drift, elapsed, image.getWidth()));
        }
        if (effect != null) effect.invalidate();
        handler.postDelayed(tick, frameMs);
    }
}
