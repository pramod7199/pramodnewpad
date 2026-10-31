package com.pramod.loopmidi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

final class WaveformTrimView extends View {
    interface OnMarkersChanged {
        void onChanged(float startFraction, float endFraction);
    }

    private float[] samples;
    private float startFraction;
    private float endFraction = 1f;
    private int dragTarget;
    private OnMarkersChanged listener;

    private final Paint backgroundPaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final Paint selectedWavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unselectedWavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint regionPaint = new Paint();
    private final Paint startPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint endPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint loadingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    WaveformTrimView(Context context) {
        super(context);
        backgroundPaint.setColor(0xFF0A1628);
        gridPaint.setColor(0x22FFFFFF);
        gridPaint.setStrokeWidth(1f);
        selectedWavePaint.setColor(0xFF38B6FF);
        selectedWavePaint.setStrokeWidth(2f);
        unselectedWavePaint.setColor(0xFF334455);
        unselectedWavePaint.setStrokeWidth(2f);
        regionPaint.setColor(0x2238B6FF);
        startPaint.setColor(0xFF00FF66);
        startPaint.setStrokeWidth(3f);
        endPaint.setColor(0xFFFF4444);
        endPaint.setStrokeWidth(3f);
        handlePaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(30f);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        loadingPaint.setColor(0xFF556677);
        loadingPaint.setTextSize(34f);
        loadingPaint.setTextAlign(Paint.Align.CENTER);
        loadingPaint.setAntiAlias(true);
    }

    void setSamples(float[] samples) {
        this.samples = samples;
        invalidate();
    }

    void setMarkers(float startFraction, float endFraction) {
        this.startFraction = Math.max(0f, Math.min(endFraction - 0.005f, startFraction));
        this.endFraction = Math.min(1f, Math.max(startFraction + 0.005f, endFraction));
        invalidate();
    }

    void setOnMarkersChanged(OnMarkersChanged listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        canvas.drawRect(0, 0, width, height, backgroundPaint);
        for (int i = 1; i < 4; i++) {
            float x = width * i / 4f;
            canvas.drawLine(x, 0, x, height, gridPaint);
        }
        canvas.drawLine(0, height * 0.5f, width, height * 0.5f, gridPaint);

        if (samples == null || samples.length == 0) {
            canvas.drawText("Waveform loading...", width * 0.5f,
                    height * 0.5f + 12, loadingPaint);
            return;
        }

        int startX = (int) (startFraction * width);
        int endX = (int) (endFraction * width);
        float centerY = height * 0.5f;
        canvas.drawRect(startX, 0, endX, height, regionPaint);
        for (int i = 0; i < samples.length; i++) {
            float x = (float) i / samples.length * width;
            float amplitude = samples[i] * height * 0.46f;
            canvas.drawLine(x, centerY - amplitude, x, centerY + amplitude,
                    x >= startX && x <= endX ? selectedWavePaint : unselectedWavePaint);
        }

        canvas.drawLine(startX, 0, startX, height, startPaint);
        canvas.drawLine(endX, 0, endX, height, endPaint);
        handlePaint.setColor(0xFF00FF66);
        canvas.drawCircle(startX, 22, 22, handlePaint);
        textPaint.setColor(0xFF000000);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("S", startX, 32, textPaint);
        handlePaint.setColor(0xFFFF4444);
        canvas.drawCircle(endX, 22, 22, handlePaint);
        textPaint.setColor(0xFFFFFFFF);
        canvas.drawText("E", endX, 32, textPaint);

        loadingPaint.setTextSize(24f);
        loadingPaint.setTextAlign(Paint.Align.LEFT);
        loadingPaint.setColor(0xFF00FF66);
        canvas.drawText(String.format(java.util.Locale.US, "%.1f%%",
                startFraction * 100), startX + 4, height - 6, loadingPaint);
        loadingPaint.setTextAlign(Paint.Align.RIGHT);
        loadingPaint.setColor(0xFFFF4444);
        canvas.drawText(String.format(java.util.Locale.US, "%.1f%%",
                endFraction * 100), endX - 4, height - 6, loadingPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int width = getWidth();
        if (width == 0) return true;
        float x = event.getX();
        float startX = startFraction * width;
        float endX = endFraction * width;
        float slop = Math.max(44f, width * 0.04f);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                float startDistance = Math.abs(x - startX);
                float endDistance = Math.abs(x - endX);
                if (startDistance < slop && endDistance < slop) {
                    dragTarget = startDistance <= endDistance ? 1 : 2;
                } else if (startDistance < slop) {
                    dragTarget = 1;
                } else if (endDistance < slop) {
                    dragTarget = 2;
                } else {
                    dragTarget = 0;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragTarget == 1) {
                    startFraction = Math.max(0f,
                            Math.min(endFraction - 0.005f, x / width));
                    notifyMarkersChanged();
                } else if (dragTarget == 2) {
                    endFraction = Math.min(1f,
                            Math.max(startFraction + 0.005f, x / width));
                    notifyMarkersChanged();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragTarget = 0;
                break;
            default:
                return false;
        }
        return true;
    }

    private void notifyMarkersChanged() {
        invalidate();
        if (listener != null) listener.onChanged(startFraction, endFraction);
    }
}
