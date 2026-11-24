package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * The ambient particle/atmosphere layer wallpaper_effect asks for: rain, storm, snow, stars, sunbeams,
 * clouds, bubbles, fireflies, or luminous motes. Preallocated arrays only, zero per-frame allocations,
 * driven by elapsed time and invalidated at most 30 times a second while Home is in front.
 * It takes no focus, clicks or accessibility.
 */
final class WallpaperEffectView extends View {
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final int SUNBEAM_SHAFTS = 5;

    private final String kind;
    private final float dp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Path beamPath = new Path();
    private final Random random = new Random(7);
    private final int count;
    private final float[] x, y, speed, size, phase, alpha, depth;
    private boolean seeded;
    private boolean running;
    private long last;
    private float t;

    /** Shooting star (stars): seconds until the next, and progress [0..1] while in flight (<0 when idle). */
    private float nextShot = 4.5f, shot = -1f, shotX, shotY;
    /** Atmospheric lightning flash (storm): seconds until next, progress [0..1] while active (<0 when idle). */
    private float nextFlash = 4.0f, flash = -1f, flashX;

    WallpaperEffectView(Context context, String kind) {
        super(context);
        this.kind = WallpaperMotion.effect(kind);
        dp = context.getResources().getDisplayMetrics().density;
        count = count(this.kind);
        x = new float[count];
        y = new float[count];
        speed = new float[count];
        size = new float[count];
        phase = new float[count];
        alpha = new float[count];
        depth = new float[count];
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    static int count(String kind) {
        switch (WallpaperMotion.effect(kind)) {
            case WallpaperMotion.RAIN: return 80;
            case WallpaperMotion.STORM: return 80;
            case WallpaperMotion.SNOW: return 72;
            case WallpaperMotion.STARS: return 75;
            case WallpaperMotion.SUNBEAMS: return 40;
            case WallpaperMotion.CLOUDS: return 22;
            case WallpaperMotion.BUBBLES: return 36;
            case WallpaperMotion.FIREFLIES: return 34;
            case WallpaperMotion.MOTES: return 48;
            default: return 0;
        }
    }

    void setRunning(boolean running) {
        this.running = running;
        last = SystemClock.uptimeMillis();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) seed(w, h);
    }

    private void seed(int w, int h) {
        float scale = h / 1080f;
        for (int i = 0; i < count; i++) {
            float d = random.nextFloat();
            depth[i] = d;
            x[i] = random.nextFloat() * w;
            y[i] = random.nextFloat() * h;
            phase[i] = random.nextFloat() * TWO_PI;
            switch (kind) {
                case WallpaperMotion.RAIN:
                    speed[i] = (680f + d * 880f) * scale;
                    size[i] = (14f + d * 32f) * scale;
                    alpha[i] = 0.08f + d * 0.22f;
                    break;
                case WallpaperMotion.STORM:
                    speed[i] = (1050f + d * 1050f) * scale;
                    size[i] = (22f + d * 40f) * scale;
                    alpha[i] = 0.11f + d * 0.26f;
                    break;
                case WallpaperMotion.SNOW:
                    speed[i] = (24f + d * 64f) * scale;
                    size[i] = (1.1f + d * 2.6f) * dp;
                    alpha[i] = 0.26f + d * 0.56f;
                    break;
                case WallpaperMotion.STARS:
                    y[i] = random.nextFloat() * h * 0.72f;
                    speed[i] = 0.55f + random.nextFloat() * 1.85f;
                    size[i] = (0.55f + d * 1.45f) * dp;
                    alpha[i] = 0.28f + d * 0.67f;
                    break;
                case WallpaperMotion.SUNBEAMS:
                    speed[i] = (8f + d * 18f) * scale;
                    size[i] = (1.0f + d * 2.1f) * dp;
                    alpha[i] = 0.12f + d * 0.26f;
                    break;
                case WallpaperMotion.CLOUDS:
                    y[i] = h * (0.04f + random.nextFloat() * 0.52f);
                    speed[i] = (7f + d * 19f) * scale;
                    size[i] = (120f + d * 210f) * scale;
                    alpha[i] = 0.035f + d * 0.065f;
                    break;
                case WallpaperMotion.BUBBLES:
                    speed[i] = (20f + d * 46f) * scale;
                    size[i] = (2.2f + d * 5.2f) * dp;
                    alpha[i] = 0.14f + d * 0.30f;
                    break;
                case WallpaperMotion.FIREFLIES:
                    y[i] = h * (0.14f + random.nextFloat() * 0.82f);
                    speed[i] = 0.75f + random.nextFloat() * 1.35f;
                    size[i] = (1.5f + d * 1.9f) * dp;
                    alpha[i] = 0.45f + d * 0.45f;
                    break;
                case WallpaperMotion.MOTES:
                    speed[i] = (9f + d * 24f) * scale;
                    size[i] = (1.0f + d * 2.2f) * dp;
                    alpha[i] = 0.12f + d * 0.28f;
                    break;
                default:
                    break;
            }
        }
        seeded = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!seeded || count == 0) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        long now = SystemClock.uptimeMillis();
        // A frame late after a pause must not jump the particles: cap the step at a tenth of a second.
        float dt = running ? Math.min(0.1f, Math.max(0f, (now - last) / 1000f)) : 0f;
        last = now;
        t += dt;
        switch (kind) {
            case WallpaperMotion.RAIN: drawRain(canvas, w, h, dt, 0.13f, 0xFFC0D4FF, false); break;
            case WallpaperMotion.STORM: drawRain(canvas, w, h, dt, 0.28f, 0xFFD2E2FF, true); break;
            case WallpaperMotion.SNOW: drawSnow(canvas, w, h, dt); break;
            case WallpaperMotion.STARS: drawStars(canvas, w, h, dt); break;
            case WallpaperMotion.SUNBEAMS: drawSunbeams(canvas, w, h, dt); break;
            case WallpaperMotion.CLOUDS: drawClouds(canvas, w, h, dt); break;
            case WallpaperMotion.BUBBLES: drawBubbles(canvas, w, h, dt); break;
            case WallpaperMotion.FIREFLIES: drawFireflies(canvas, w, h, dt); break;
            case WallpaperMotion.MOTES: drawMotes(canvas, w, h, dt); break;
            default: break;
        }
    }

    /** Multi-depth rain streaks; when storm is true, adds slanted wind and soft upper-sky lightning. */
    private void drawRain(Canvas canvas, int w, int h, float dt, float slant, int color, boolean storm) {
        if (storm) drawLightning(canvas, w, h, dt);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        float wrapMargin = w * slant + 80f;
        for (int i = 0; i < count; i++) {
            y[i] += speed[i] * dt;
            x[i] -= speed[i] * slant * dt;
            if (y[i] - size[i] > h) {
                y[i] = -size[i] - random.nextFloat() * 40f;
                x[i] = random.nextFloat() * (w + wrapMargin);
            }
            if (x[i] < -40f) x[i] += w + wrapMargin;
            float d = depth[i];
            paint.setStrokeWidth((0.85f + d * 1.15f) * dp);
            paint.setAlpha(clampAlpha(alpha[i] * 255f));
            canvas.drawLine(x[i], y[i], x[i] - size[i] * slant, y[i] + size[i], paint);
        }
    }

    /** Soft atmospheric double-flicker lightning glow in the upper sky for STORM. */
    private void drawLightning(Canvas canvas, int w, int h, float dt) {
        if (flash < 0f) {
            nextFlash -= dt;
            if (nextFlash <= 0f) {
                flash = 0f;
                flashX = w * (0.18f + random.nextFloat() * 0.64f);
            }
            return;
        }
        flash += dt / 0.48f;
        if (flash >= 1f) {
            flash = -1f;
            nextFlash = 5.5f + random.nextFloat() * 8.5f;
            return;
        }
        // Natural double-pulse envelope: bright initial flash, quick dip, softer after-pulse.
        float intensity;
        if (flash < 0.14f) {
            intensity = flash / 0.14f;
        } else if (flash < 0.28f) {
            intensity = 1f - 0.72f * ((flash - 0.14f) / 0.14f);
        } else if (flash < 0.42f) {
            intensity = 0.28f + 0.52f * ((flash - 0.28f) / 0.14f);
        } else {
            float tail = (flash - 0.42f) / 0.58f;
            intensity = 0.80f * (1f - tail) * (1f - tail);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFDCEBFF);
        // Subtle whole-sky atmospheric wash
        paint.setAlpha(clampAlpha(intensity * 26f));
        canvas.drawRect(0f, 0f, w, h * 0.68f, paint);
        // Layered upper-sky cloud illumination centered around flashX
        float cy = h * 0.12f;
        float rx = w * 0.46f;
        float ry = h * 0.34f;
        paint.setAlpha(clampAlpha(intensity * 36f));
        oval.set(flashX - rx, cy - ry, flashX + rx, cy + ry);
        canvas.drawOval(oval, paint);
        paint.setAlpha(clampAlpha(intensity * 54f));
        oval.set(flashX - rx * 0.52f, cy - ry * 0.55f, flashX + rx * 0.52f, cy + ry * 0.55f);
        canvas.drawOval(oval, paint);
    }

    /** Multi-depth drifting snowflakes with soft outer halo on near flakes and dual-sine sway. */
    private void drawSnow(Canvas canvas, int w, int h, float dt) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        for (int i = 0; i < count; i++) {
            float d = depth[i];
            y[i] += speed[i] * dt;
            if (y[i] - size[i] * 2f > h) {
                y[i] = -size[i] * 2f;
                x[i] = random.nextFloat() * w;
            }
            float swayAmp = (6f + d * 12f) * dp;
            float sway = (float) Math.sin(t * (0.7f + d * 0.45f) + phase[i]) * swayAmp
                    + (float) Math.sin(t * 0.32f + phase[i] * 1.9f) * (swayAmp * 0.35f);
            float cx = x[i] + sway;
            if (d > 0.55f) {
                paint.setAlpha(clampAlpha(alpha[i] * 58f));
                canvas.drawCircle(cx, y[i], size[i] * 2.0f, paint);
            }
            paint.setAlpha(clampAlpha(alpha[i] * 255f));
            canvas.drawCircle(cx, y[i], size[i], paint);
        }
    }

    /** Twinkling multi-size starfield with subtle tints and periodic shooting stars. */
    private void drawStars(Canvas canvas, int w, int h, float dt) {
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < count; i++) {
            int mod = i % 5;
            paint.setColor(mod == 0 ? 0xFFD8E9FF : (mod == 1 ? 0xFFFFF2D6 : 0xFFFFFFFF));
            float twinkle = 0.45f + 0.55f * (float) Math.sin(t * speed[i] + phase[i]);
            float a = alpha[i] * twinkle;
            if (depth[i] > 0.72f) {
                paint.setAlpha(clampAlpha(a * 64f));
                canvas.drawCircle(x[i], y[i], size[i] * 2.4f, paint);
            }
            paint.setAlpha(clampAlpha(a * 255f));
            canvas.drawCircle(x[i], y[i], size[i], paint);
        }
        // One shooting star at a time, every 5 to 13 seconds, across the upper sky.
        if (shot < 0f) {
            nextShot -= dt;
            if (nextShot <= 0f) {
                shot = 0f;
                shotX = w * (0.42f + random.nextFloat() * 0.48f);
                shotY = h * (0.04f + random.nextFloat() * 0.24f);
            }
            return;
        }
        shot += dt / 0.85f;
        if (shot >= 1f) {
            shot = -1f;
            nextShot = 5.5f + random.nextFloat() * 8.0f;
            return;
        }
        float fade = (float) Math.sin(shot * Math.PI);
        float len = 175f * dp, travel = 290f * dp;
        float hx = shotX - travel * shot;
        float hy = shotY + travel * 0.44f * shot;
        float dx = len, dy = -len * 0.44f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFFEAF4FF);
        // Outer soft tail
        paint.setStrokeWidth(2.2f * dp);
        paint.setAlpha(clampAlpha(75f * fade));
        canvas.drawLine(hx, hy, hx + dx, hy + dy, paint);
        // Brighter inner tail
        paint.setStrokeWidth(1.3f * dp);
        paint.setAlpha(clampAlpha(210f * fade));
        canvas.drawLine(hx, hy, hx + dx * 0.55f, hy + dy * 0.55f, paint);
        // Bright leading point
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        paint.setAlpha(clampAlpha(240f * fade));
        canvas.drawCircle(hx, hy, 2.0f * dp, paint);
    }

    /** Soft diagonal warm light shafts + floating golden dust motes for sunny daytime. */
    private void drawSunbeams(Canvas canvas, int w, int h, float dt) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFE7A8);
        float slantX = w * 0.36f;
        for (int s = 0; s < SUNBEAM_SHAFTS; s++) {
            float frac = s / (float) (SUNBEAM_SHAFTS - 1);
            float shimmer = 0.55f + 0.45f * (float) Math.sin(t * (0.24f + s * 0.06f) + s * 1.45f);
            float driftX = (float) Math.sin(t * 0.15f + s * 1.1f) * w * 0.025f;
            float topCenter = w * (-0.06f + frac * 0.68f) + driftX;
            float topHalf = w * (0.045f + (s % 2) * 0.02f);
            float botHalf = topHalf * 2.15f;
            float botCenter = topCenter + slantX;

            // Wide soft outer shaft
            beamPath.reset();
            beamPath.moveTo(topCenter - topHalf, 0f);
            beamPath.lineTo(topCenter + topHalf, 0f);
            beamPath.lineTo(botCenter + botHalf, h);
            beamPath.lineTo(botCenter - botHalf, h);
            beamPath.close();
            paint.setAlpha(clampAlpha((11f + 9f * shimmer)));
            canvas.drawPath(beamPath, paint);

            // Narrower luminous inner core
            beamPath.reset();
            beamPath.moveTo(topCenter - topHalf * 0.42f, 0f);
            beamPath.lineTo(topCenter + topHalf * 0.42f, 0f);
            beamPath.lineTo(botCenter + botHalf * 0.45f, h);
            beamPath.lineTo(botCenter - botHalf * 0.45f, h);
            beamPath.close();
            paint.setAlpha(clampAlpha((10f + 11f * shimmer)));
            canvas.drawPath(beamPath, paint);
        }

        // Floating golden dust motes catching the light
        paint.setColor(0xFFFFF0C2);
        for (int i = 0; i < count; i++) {
            y[i] -= speed[i] * dt;
            x[i] += (speed[i] * 0.35f + (float) Math.sin(t * 0.5f + phase[i]) * 7f * dp) * dt;
            if (y[i] + size[i] * 2f < 0f) {
                y[i] = h + size[i] * 2f;
                x[i] = random.nextFloat() * w;
            }
            if (x[i] > w + 16f) x[i] = -16f;
            else if (x[i] < -16f) x[i] = w + 16f;
            float pulse = 0.45f + 0.55f * (float) Math.sin(t * 1.15f + phase[i]);
            paint.setAlpha(clampAlpha(alpha[i] * pulse * 68f));
            canvas.drawCircle(x[i], y[i], size[i] * 2.4f, paint);
            paint.setAlpha(clampAlpha(alpha[i] * pulse * 255f));
            canvas.drawCircle(x[i], y[i], size[i], paint);
        }
    }

    /** Slow drifting soft translucent mist/cloud bands across the upper and mid sky. */
    private void drawClouds(Canvas canvas, int w, int h, float dt) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFE8F0FA);
        for (int i = 0; i < count; i++) {
            float rx = size[i] * 1.55f;
            float ry = size[i] * 0.44f;
            x[i] += speed[i] * dt;
            if (x[i] - rx > w) {
                x[i] = -rx;
                y[i] = h * (0.04f + random.nextFloat() * 0.52f);
            }
            float bob = (float) Math.sin(t * 0.18f + phase[i]) * 6f * dp;
            float breath = 0.80f + 0.20f * (float) Math.sin(t * 0.25f + phase[i]);
            float cy = y[i] + bob;
            // Outer soft mist band
            paint.setAlpha(clampAlpha(alpha[i] * breath * 140f));
            oval.set(x[i] - rx, cy - ry, x[i] + rx, cy + ry);
            canvas.drawOval(oval, paint);
            // Slightly brighter inner puff offset slightly upward
            paint.setAlpha(clampAlpha(alpha[i] * breath * 210f));
            oval.set(x[i] - rx * 0.62f, cy - ry * 0.82f, x[i] + rx * 0.54f, cy + ry * 0.45f);
            canvas.drawOval(oval, paint);
        }
    }

    /** Rising translucent rings with subtle body tint and specular highlight dot. */
    private void drawBubbles(Canvas canvas, int w, int h, float dt) {
        for (int i = 0; i < count; i++) {
            float d = depth[i];
            y[i] -= speed[i] * dt;
            if (y[i] + size[i] * 2f < 0f) {
                y[i] = h + size[i] * 2f;
                x[i] = random.nextFloat() * w;
            }
            float sway = (float) Math.sin(t * (0.75f + d * 0.4f) + phase[i]) * (8f + d * 8f) * dp;
            float cx = x[i] + sway;
            float r = size[i];

            // Very soft translucent interior
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFCFFAFF);
            paint.setAlpha(clampAlpha(alpha[i] * 48f));
            canvas.drawCircle(cx, y[i], r, paint);

            // Crisp translucent rim
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth((0.9f + d * 0.7f) * dp);
            paint.setColor(0xFFD8FBFF);
            paint.setAlpha(clampAlpha(alpha[i] * 255f));
            canvas.drawCircle(cx, y[i], r, paint);

            // Subtle upper-left specular highlight
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha(clampAlpha(alpha[i] * 310f));
            canvas.drawCircle(cx - r * 0.34f, y[i] - r * 0.34f, Math.max(0.8f * dp, r * 0.20f), paint);
        }
    }

    /** Warm golden-green glowing fireflies with layered soft halo and organic wandering paths. */
    private void drawFireflies(Canvas canvas, int w, int h, float dt) {
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < count; i++) {
            float p = phase[i];
            float vx = ((float) Math.cos(t * 0.52f + p) * 18f
                    + (float) Math.sin(t * 0.23f + p * 1.7f) * 11f) * dp;
            float vy = ((float) Math.sin(t * 0.44f + p * 1.3f) * 14f
                    + (float) Math.cos(t * 0.19f + p * 0.8f) * 7f) * dp;
            x[i] += vx * dt;
            y[i] += vy * dt;
            if (x[i] < -20f) x[i] += w + 40f;
            else if (x[i] > w + 20f) x[i] -= w + 40f;
            if (y[i] < h * 0.08f) y[i] = h * 0.94f;
            else if (y[i] > h * 0.98f) y[i] = h * 0.12f;

            float s = 0.5f + 0.5f * (float) Math.sin(t * speed[i] + p);
            float glow = 0.18f + 0.82f * (s * s);
            float a = alpha[i] * glow;
            float r = size[i];

            // Wide soft warm-green atmospheric halo
            paint.setColor(0xFFB8FF5C);
            paint.setAlpha(clampAlpha(a * 42f));
            canvas.drawCircle(x[i], y[i], r * 4.2f, paint);

            // Mid golden-green glow
            paint.setColor(0xFFE6FF75);
            paint.setAlpha(clampAlpha(a * 115f));
            canvas.drawCircle(x[i], y[i], r * 2.1f, paint);

            // Bright warm-white core
            paint.setColor(0xFFFFFDE0);
            paint.setAlpha(clampAlpha(a * 255f));
            canvas.drawCircle(x[i], y[i], r, paint);
        }
    }

    /** Soft luminous ambient dust drifting gently upward with a slow pulse, suitable for any background. */
    private void drawMotes(Canvas canvas, int w, int h, float dt) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFF6E0);
        for (int i = 0; i < count; i++) {
            y[i] -= speed[i] * dt;
            x[i] += (float) Math.sin(t * 0.45f + phase[i]) * 8f * dp * dt;
            if (y[i] + size[i] * 2f < 0f) {
                y[i] = h + size[i] * 2f;
                x[i] = random.nextFloat() * w;
            }
            if (x[i] < -12f) x[i] += w + 24f;
            else if (x[i] > w + 12f) x[i] -= w + 24f;
            float pulse = 0.45f + 0.55f * (float) Math.sin(t * 1.1f + phase[i]);
            paint.setAlpha(clampAlpha(alpha[i] * pulse * 62f));
            canvas.drawCircle(x[i], y[i], size[i] * 2.3f, paint);
            paint.setAlpha(clampAlpha(alpha[i] * pulse * 255f));
            canvas.drawCircle(x[i], y[i], size[i], paint);
        }
    }

    private static int clampAlpha(float value) {
        int a = (int) value;
        if (a < 0) return 0;
        return Math.min(255, a);
    }
}
