package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Minimal crisp vector weather icon drawn directly on Canvas for the clock header.
 * Zero extra drawable resources, sharp at any TV DPI, no per-frame allocations.
 */
public final class WeatherIconView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bolt = new Path();
    private String kind = "sun";

    public WeatherIconView(Context context) {
        super(context);
        init();
    }

    public WeatherIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setKind(String kind) {
        String next = kind == null ? "sun" : kind;
        if (next.equals(this.kind)) return;
        this.kind = next;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float s = Math.min(w, h);
        if (s <= 0f) return;
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        switch (kind) {
            case "sun":
                drawSun(canvas, cx, cy, s * 0.24f, s * 0.42f, s * 0.08f);
                break;
            case "moon":
                drawMoon(canvas, cx, cy, s * 0.34f);
                break;
            case "partly_day":
                drawSun(canvas, cx + s * 0.14f, cy - s * 0.14f, s * 0.16f, s * 0.28f, s * 0.06f);
                drawCloud(canvas, cx - s * 0.05f, cy + s * 0.10f, s * 0.62f, 0xF2FFFFFF);
                break;
            case "partly_night":
                drawMoon(canvas, cx + s * 0.16f, cy - s * 0.15f, s * 0.20f);
                drawCloud(canvas, cx - s * 0.05f, cy + s * 0.10f, s * 0.62f, 0xF2FFFFFF);
                break;
            case "cloud":
                drawCloud(canvas, cx, cy + s * 0.04f, s * 0.78f, 0xE6FFFFFF);
                break;
            case "fog":
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.10f);
                paint.setColor(0xD9FFFFFF);
                canvas.drawLine(cx - s * 0.34f, cy - s * 0.20f, cx + s * 0.34f, cy - s * 0.20f, paint);
                canvas.drawLine(cx - s * 0.28f, cy, cx + s * 0.38f, cy, paint);
                canvas.drawLine(cx - s * 0.36f, cy + s * 0.20f, cx + s * 0.26f, cy + s * 0.20f, paint);
                break;
            case "rain":
                drawCloud(canvas, cx, cy - s * 0.10f, s * 0.72f, 0xE6FFFFFF);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.08f);
                paint.setColor(0xFF9EC5FF);
                for (int i = -1; i <= 1; i++) {
                    float rx = cx + i * s * 0.20f;
                    canvas.drawLine(rx, cy + s * 0.20f, rx - s * 0.06f, cy + s * 0.38f, paint);
                }
                break;
            case "snow":
                drawCloud(canvas, cx, cy - s * 0.10f, s * 0.72f, 0xE6FFFFFF);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFFFFFFF);
                for (int i = -1; i <= 1; i++) {
                    canvas.drawCircle(cx + i * s * 0.20f, cy + s * 0.28f, s * 0.065f, paint);
                }
                break;
            case "storm":
                drawCloud(canvas, cx, cy - s * 0.12f, s * 0.72f, 0xE6FFFFFF);
                bolt.reset();
                bolt.moveTo(cx + s * 0.04f, cy + s * 0.12f);
                bolt.lineTo(cx - s * 0.08f, cy + s * 0.28f);
                bolt.lineTo(cx + s * 0.02f, cy + s * 0.28f);
                bolt.lineTo(cx - s * 0.06f, cy + s * 0.44f);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.08f);
                paint.setColor(0xFFFFD54F);
                canvas.drawPath(bolt, paint);
                break;
            default:
                drawSun(canvas, cx, cy, s * 0.24f, s * 0.42f, s * 0.08f);
                break;
        }
    }

    private void drawSun(Canvas canvas, float cx, float cy, float r, float rayOut, float stroke) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFE082);
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        float rayIn = r * 1.38f;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            canvas.drawLine(cx + cos * rayIn, cy + sin * rayIn, cx + cos * rayOut, cy + sin * rayOut, paint);
        }
    }

    private void drawMoon(Canvas canvas, float cx, float cy, float r) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(r * 0.46f);
        paint.setColor(0xFFD8E8FF);
        canvas.drawArc(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.72f, cy + r * 0.72f, 45f, 240f, false, paint);
    }

    private void drawCloud(Canvas canvas, float cx, float cy, float width, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        float r1 = width * 0.24f;
        float r2 = width * 0.30f;
        float r3 = width * 0.21f;
        float baseY = cy + width * 0.14f;
        canvas.drawCircle(cx - width * 0.24f, baseY - r1 * 0.45f, r1, paint);
        canvas.drawCircle(cx + width * 0.02f, baseY - r2 * 0.75f, r2, paint);
        canvas.drawCircle(cx + width * 0.27f, baseY - r3 * 0.45f, r3, paint);
        canvas.drawRoundRect(cx - width * 0.44f, baseY - r1 * 0.5f, cx + width * 0.45f, baseY + r1 * 0.45f,
                r1 * 0.45f, r1 * 0.45f, paint);
    }
}
