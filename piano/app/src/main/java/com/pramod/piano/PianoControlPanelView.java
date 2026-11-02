package com.pramod.piano;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public final class PianoControlPanelView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    public PianoControlPanelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        RectF panel = new RectF(0, 0, width, height);

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 0, height,
                new int[]{0xFF292D33, 0xFF101216, 0xFF050608},
                null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(panel, 7f * density, 7f * density, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density);
        paint.setColor(0xFF717780);
        canvas.drawRoundRect(panel, 7f * density, 7f * density, paint);
        paint.setStyle(Paint.Style.FILL);

        drawText(canvas, "TONE SELECT", width * 0.025f, height * 0.22f,
                0xFFC9CED6, 6f, true);
        RectF display = new RectF(width * 0.025f, height * 0.29f,
                width * 0.235f, height * 0.73f);
        paint.setColor(0xFF190F0D);
        canvas.drawRoundRect(display, 2f * density, 2f * density, paint);
        paint.setColor(0xFF57342A);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density);
        canvas.drawRoundRect(display, 2f * density, 2f * density, paint);
        paint.setStyle(Paint.Style.FILL);
        drawText(canvas, "PRESET 001", display.left + 5f * density,
                display.top + 10f * density, 0xFFFF9A72, 7f, true);
        drawText(canvas, "STAGE PIANO", display.left + 5f * density,
                display.top + 21f * density, 0xFFFFD1B7, 7f, false);

        drawKnob(canvas, width * 0.285f, height * 0.49f);
        drawText(canvas, "VOLUME", width * 0.285f, height * 0.19f,
                0xFFE2E5E9, 6f, true);
        drawText(canvas, "STAGE SYNTH", width * 0.345f, height * 0.48f,
                0xFFFFFFFF, 11f, true);
        drawText(canvas, "PERFORMANCE KEYBOARD", width * 0.345f, height * 0.72f,
                0xFFADB4BE, 6f, false);

        String[] sliderNames = {"CUTOFF", "RES", "ATTACK", "RELEASE"};
        float sliderStart = width * 0.56f;
        float sliderStep = width * 0.045f;
        for (int i = 0; i < sliderNames.length; i++) {
            float x = sliderStart + i * sliderStep;
            drawSlider(canvas, x, height * 0.22f, height * 0.72f);
            drawText(canvas, sliderNames[i], x, height * 0.91f,
                    0xFFD8DDE4, 5f, false);
        }
        drawText(canvas, "SOUND MODIFY", sliderStart + sliderStep * 1.5f,
                height * 0.14f, 0xFFE4E7EB, 6f, true);

        String[] padNames = {"A", "B", "C", "D"};
        float padWidth = width * 0.043f;
        float padHeight = height * 0.28f;
        float padStartX = width * 0.80f;
        float padStartY = height * 0.25f;
        float padGap = width * 0.008f;
        for (int i = 0; i < padNames.length; i++) {
            float x = padStartX + (i % 2) * (padWidth + padGap);
            float y = padStartY + (i / 2) * (padHeight + height * 0.06f);
            RectF pad = new RectF(x, y, x + padWidth, y + padHeight);
            paint.setShader(new LinearGradient(x, y, x, y + padHeight,
                    new int[]{0xFFFFD9D0, 0xFFE7A79C, 0xFFB36B64},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(pad, 2f * density, 2f * density, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0xFFFFE7E1);
            paint.setStrokeWidth(density);
            canvas.drawRoundRect(pad, 2f * density, 2f * density, paint);
            paint.setStyle(Paint.Style.FILL);
            drawCenteredText(canvas, padNames[i], pad.centerX(), pad.centerY(),
                    0xFF281B1A, 7f, true);
        }
        drawText(canvas, "AUDIO PAD", padStartX + padWidth + padGap,
                height * 0.14f, 0xFFE4E7EB, 6f, true);

        String[] buttons = {"PIANO", "KEYBOARD", "STRINGS", "BRASS", "SYNTH", "ORGAN"};
        float buttonStartX = width * 0.025f;
        float buttonY = height * 0.82f;
        float buttonWidth = width * 0.049f;
        float buttonHeight = height * 0.13f;
        float buttonGap = width * 0.008f;
        for (int i = 0; i < buttons.length; i++) {
            float x = buttonStartX + i * (buttonWidth + buttonGap);
            RectF button = new RectF(x, buttonY, x + buttonWidth, buttonY + buttonHeight);
            paint.setColor(i == 0 ? 0xFFFFD4C8 : 0xFFE4E6E9);
            canvas.drawRoundRect(button, 2f * density, 2f * density, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0xFF8F969F);
            paint.setStrokeWidth(density);
            canvas.drawRoundRect(button, 2f * density, 2f * density, paint);
            paint.setStyle(Paint.Style.FILL);
            drawCenteredText(canvas, buttons[i], button.centerX(), button.centerY(),
                    0xFF202329, 4.5f, true);
        }
        drawRightText(canvas, "88-KEY • MULTI-TOUCH",
                width * 0.975f, height * 0.94f, 0xFFBFC5CD, 5.5f, false);
    }

    private void drawKnob(Canvas canvas, float centerX, float centerY) {
        float radius = 11f * density;
        paint.setShader(new LinearGradient(centerX - radius, centerY - radius,
                centerX + radius, centerY + radius,
                new int[]{0xFF636A73, 0xFF111419, 0xFF030405},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(centerX, centerY, radius, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * density);
        paint.setColor(0xFFB2B7BE);
        canvas.drawCircle(centerX, centerY, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(centerX - 2f * density, centerY - 5f * density,
                1.5f * density, paint);
        paint.setColor(0xFFFF8B68);
        canvas.drawCircle(centerX + 6f * density, centerY + 6f * density,
                1.2f * density, paint);
    }

    private void drawSlider(Canvas canvas, float centerX, float top, float bottom) {
        float trackWidth = 2f * density;
        paint.setColor(0xFF080A0D);
        canvas.drawRoundRect(centerX - trackWidth, top, centerX + trackWidth, bottom,
                trackWidth, trackWidth, paint);
        paint.setColor(0xFF59616C);
        canvas.drawRect(centerX - density, top + (bottom - top) * 0.35f,
                centerX + density, bottom - density, paint);
        RectF cap = new RectF(centerX - 5f * density, top + (bottom - top) * 0.34f,
                centerX + 5f * density, top + (bottom - top) * 0.48f);
        paint.setShader(new LinearGradient(cap.left, cap.top, cap.left, cap.bottom,
                new int[]{0xFFFFFFFF, 0xFFD5D9DE, 0xFF747B84},
                null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(cap, 1.5f * density, 1.5f * density, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density);
        paint.setColor(0xFF111419);
        canvas.drawRoundRect(cap, 1.5f * density, 1.5f * density, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawText(Canvas canvas, String text, float x, float y,
                          int color, float sizeSp, boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(sizeSp * density);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setFakeBoldText(bold);
        canvas.drawText(text, x, y, paint);
        paint.setFakeBoldText(false);
    }

    private void drawCenteredText(Canvas canvas, String text, float x, float y,
                                  int color, float sizeSp, boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(sizeSp * density);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(bold);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(text, x, y - (metrics.ascent + metrics.descent) / 2f, paint);
        paint.setFakeBoldText(false);
    }

    private void drawRightText(Canvas canvas, String text, float x, float y,
                               int color, float sizeSp, boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(sizeSp * density);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setFakeBoldText(bold);
        canvas.drawText(text, x, y, paint);
        paint.setFakeBoldText(false);
    }
}
