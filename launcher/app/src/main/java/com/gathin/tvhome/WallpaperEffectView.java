package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * The particle layer wallpaper_effect asks for: rain, snow, bubbles or twinkling stars with a shooting star now
 * and then. A few dozen lines or dots in preallocated arrays, moved by the time since the last frame and drawn
 * with one Paint; WallpaperMotion invalidates it at most 30 times a second and only while Home is in front.
 * It takes no focus, clicks or accessibility.
 */
final class WallpaperEffectView extends View {
    private final String kind;
    private final float dp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(7);
    private final int count;
    private final float[] x, y, speed, size, phase, alpha;
    private boolean seeded;
    private boolean running;
    private long last;
    private float t;
    /** Shooting star (stars): seconds until the next, and the one in flight (progress below 0 when none). */
    private float nextShot = 5f, shot = -1f, shotX, shotY;

    WallpaperEffectView(Context context, String kind) {
        super(context);
        this.kind = kind;
        dp = context.getResources().getDisplayMetrics().density;
        count = count(kind);
        x = new float[count];
        y = new float[count];
        speed = new float[count];
        size = new float[count];
        phase = new float[count];
        alpha = new float[count];
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    static int count(String kind) {
        switch (kind) {
            case WallpaperMotion.RAIN: return 80;
            case WallpaperMotion.SNOW: return 70;
            case WallpaperMotion.BUBBLES: return 36;
            case WallpaperMotion.STARS: return 70;
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
            x[i] = random.nextFloat() * w;
            y[i] = random.nextFloat() * h;
            phase[i] = random.nextFloat() * 6.283f;
            switch (kind) {
                case WallpaperMotion.RAIN:
                    speed[i] = (900 + random.nextFloat() * 500) * scale;
                    size[i] = (18 + random.nextFloat() * 26) * scale;
                    alpha[i] = 0.10f + random.nextFloat() * 0.16f;
                    break;
                case WallpaperMotion.SNOW:
                    speed[i] = (30 + random.nextFloat() * 50) * scale;
                    size[i] = (1.2f + random.nextFloat() * 2.2f) * dp;
                    alpha[i] = 0.35f + random.nextFloat() * 0.45f;
                    break;
                case WallpaperMotion.BUBBLES:
                    speed[i] = (22 + random.nextFloat() * 40) * scale;
                    size[i] = (1.5f + random.nextFloat() * 4f) * dp;
                    alpha[i] = 0.15f + random.nextFloat() * 0.30f;
                    break;
                default: // stars, in the upper part of the screen, where a sky is
                    y[i] = random.nextFloat() * h * 0.7f;
                    speed[i] = 0.6f + random.nextFloat() * 1.6f;
                    size[i] = (0.6f + random.nextFloat() * 1.2f) * dp;
                    alpha[i] = 0.35f + random.nextFloat() * 0.6f;
                    break;
            }
        }
        seeded = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!seeded) return;
        int w = getWidth(), h = getHeight();
        long now = SystemClock.uptimeMillis();
        // A frame late after a pause must not jump the particles: cap the step at a tenth of a second.
        float dt = running ? Math.min(0.1f, (now - last) / 1000f) : 0f;
        last = now;
        t += dt;
        switch (kind) {
            case WallpaperMotion.RAIN: drawRain(canvas, w, h, dt); break;
            case WallpaperMotion.SNOW: drawFlakes(canvas, w, h, dt, 1, 0xFFFFFFFF, true); break;
            case WallpaperMotion.BUBBLES: drawFlakes(canvas, w, h, dt, -1, 0xFFCFFAFF, false); break;
            case WallpaperMotion.STARS: drawStars(canvas, w, h, dt); break;
            default: break;
        }
    }

    private void drawRain(Canvas canvas, int w, int h, float dt) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.4f * dp);
        paint.setColor(0xFFB8C8FF);
        for (int i = 0; i < count; i++) {
            y[i] += speed[i] * dt;
            x[i] -= speed[i] * 0.12f * dt;
            if (y[i] - size[i] > h) {
                y[i] = -size[i];
                x[i] = random.nextFloat() * (w + 60);
            }
            if (x[i] < -20) x[i] += w + 40;
            paint.setAlpha((int) (alpha[i] * 255));
            canvas.drawLine(x[i], y[i], x[i] - size[i] * 0.12f, y[i] + size[i], paint);
        }
    }

    /** Snow falls (direction 1) as filled dots; bubbles rise (-1) as rings. Both sway a little. */
    private void drawFlakes(Canvas canvas, int w, int h, float dt, int direction, int color, boolean filled) {
        paint.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * dp);
        paint.setColor(color);
        for (int i = 0; i < count; i++) {
            y[i] += direction * speed[i] * dt;
            if (direction > 0 && y[i] - size[i] > h) { y[i] = -size[i]; x[i] = random.nextFloat() * w; }
            if (direction < 0 && y[i] + size[i] < 0) { y[i] = h + size[i]; x[i] = random.nextFloat() * w; }
            float sway = (float) Math.sin(t * 0.9f + phase[i]) * 10f * dp;
            paint.setAlpha((int) (alpha[i] * 255));
            canvas.drawCircle(x[i] + sway, y[i], size[i], paint);
        }
    }

    private void drawStars(Canvas canvas, int w, int h, float dt) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        for (int i = 0; i < count; i++) {
            float twinkle = 0.55f + 0.45f * (float) Math.sin(t * speed[i] + phase[i]);
            paint.setAlpha((int) (alpha[i] * twinkle * 255));
            canvas.drawCircle(x[i], y[i], size[i], paint);
        }
        // One shooting star at a time, every 6 to 14 seconds, across the upper right.
        if (shot < 0f) {
            nextShot -= dt;
            if (nextShot <= 0f) {
                shot = 0f;
                shotX = w * (0.45f + random.nextFloat() * 0.45f);
                shotY = h * (0.05f + random.nextFloat() * 0.25f);
            }
            return;
        }
        shot += dt / 0.9f;
        if (shot >= 1f) {
            shot = -1f;
            nextShot = 6f + random.nextFloat() * 8f;
            return;
        }
        float len = 160f * dp, travel = 260f * dp;
        float hx = shotX - travel * shot, hy = shotY + travel * 0.45f * shot;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f * dp);
        paint.setAlpha((int) (200 * (1f - shot)));
        canvas.drawLine(hx, hy, hx + len, hy - len * 0.45f, paint);
    }
}
