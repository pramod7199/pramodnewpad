package com.pramod.piano;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public final class PianoPitchBendView extends View {
    interface BendListener {
        void onPitchBend(int value);
    }

    private static final int CENTER = 8192;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private int value = CENTER;
    private BendListener listener;

    public PianoPitchBendView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
    }

    void setBendListener(BendListener listener) {
        this.listener = listener;
    }

    private void updateFromY(float y) {
        float top = 48f * density;
        float bottom = getHeight() - 48f * density;
        float fraction = Math.max(0f, Math.min(1f, (y - top) / Math.max(1f, bottom - top)));
        value = Math.round((1f - fraction) * 16383f);
        if (listener != null) listener.onPitchBend(value);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(8f * density);
        paint.setColor(0xFFE7EAF0);
        canvas.drawText("PITCH", width * 0.5f, 20f * density, paint);
        canvas.drawText("BEND", width * 0.5f, 31f * density, paint);

        float centerX = width * 0.5f;
        float trackTop = 48f * density;
        float trackBottom = height - 48f * density;
        float trackWidth = 6f * density;
        RectF track = new RectF(centerX - trackWidth / 2f, trackTop,
                centerX + trackWidth / 2f, trackBottom);
        paint.setShader(new LinearGradient(track.left, track.top, track.right, track.bottom,
                new int[]{0xFFFF8B68, 0xFF202833, 0xFF78A9FF},
                null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(track, trackWidth, trackWidth, paint);
        paint.setShader(null);

        float centerY = (trackTop + trackBottom) * 0.5f;
        paint.setColor(0xFF8995A6);
        paint.setStrokeWidth(density);
        canvas.drawLine(centerX - 10f * density, centerY,
                centerX + 10f * density, centerY, paint);

        float fraction = 1f - value / 16383f;
        float knobY = trackTop + fraction * (trackBottom - trackTop);
        float knobRadius = 11f * density;
        paint.setShader(new LinearGradient(centerX - knobRadius, knobY - knobRadius,
                centerX + knobRadius, knobY + knobRadius,
                new int[]{0xFFFFFFFF, 0xFFD8DDE5, 0xFF616B78},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(centerX, knobY, knobRadius, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFF050608);
        paint.setStrokeWidth(density);
        canvas.drawCircle(centerX, knobY, knobRadius, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setTextSize(7f * density);
        paint.setColor(value == CENTER ? 0xFF9BAAC0
                : value > CENTER ? 0xFFFFA18A : 0xFF9CC0FF);
        canvas.drawText(value == CENTER ? "CENTER" : value > CENTER ? "UP" : "DOWN",
                centerX, height - 20f * density, paint);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                updateFromY(event.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                value = CENTER;
                if (listener != null) listener.onPitchBend(CENTER);
                invalidate();
                return true;
            default:
                return true;
        }
    }
}
