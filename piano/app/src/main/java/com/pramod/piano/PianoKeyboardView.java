package com.pramod.piano;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;

public final class PianoKeyboardView extends View {
    interface NoteListener {
        void onNoteOn(int midiNote);
        void onNoteOff(int midiNote);
    }

    private static final int LOWEST_NOTE = 21;
    private static final int HIGHEST_NOTE = 108;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SparseIntArray activePointers = new SparseIntArray();
    private final float whiteKeyWidth;
    private final float blackKeyWidth;
    private final float blackKeyHeight;
    private NoteListener listener;

    public PianoKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        whiteKeyWidth = 42f * density;
        blackKeyWidth = whiteKeyWidth * 0.62f;
        blackKeyHeight = 0.62f;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    void setNoteListener(NoteListener listener) {
        this.listener = listener;
    }

    int getKeyboardWidth() {
        int whiteCount = 0;
        for (int note = LOWEST_NOTE; note <= HIGHEST_NOTE; note++) {
            if (!isBlack(note)) whiteCount++;
        }
        return Math.round(whiteCount * whiteKeyWidth);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(getKeyboardWidth(), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int whiteIndex = 0;
        float height = getHeight();
        for (int note = LOWEST_NOTE; note <= HIGHEST_NOTE; note++) {
            if (isBlack(note)) continue;
            float left = whiteIndex * whiteKeyWidth;
            float right = left + whiteKeyWidth;
            paint.setColor(isNotePressed(note) ? 0xFFBBD7FF : 0xFFFFFFFF);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(left, 0, right, height, paint);
            paint.setColor(0xFF111827);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
            canvas.drawRect(left, 0, right, height, paint);
            drawLabel(canvas, note, (left + right) * 0.5f,
                    height - 10f * getResources().getDisplayMetrics().density,
                    0xFF111827, 9f);
            whiteIndex++;
        }

        whiteIndex = 0;
        for (int note = LOWEST_NOTE; note <= HIGHEST_NOTE; note++) {
            if (isBlack(note)) {
                float centerX = whiteIndex * whiteKeyWidth;
                float left = centerX - blackKeyWidth * 0.5f;
                float right = centerX + blackKeyWidth * 0.5f;
                float bottom = height * blackKeyHeight;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(isNotePressed(note) ? 0xFF3977C5 : 0xFF080B10);
                float radius = 3f * getResources().getDisplayMetrics().density;
                canvas.drawRoundRect(left, 0, right, bottom, radius, radius, paint);
                paint.setColor(0xFF05080D);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1f);
                canvas.drawRoundRect(left, 0, right, bottom, radius, radius, paint);
                drawLabel(canvas, note, centerX,
                        bottom - 8f * getResources().getDisplayMetrics().density,
                        0xFFFFFFFF, 7f);
            } else {
                whiteIndex++;
            }
        }
    }

    private void drawLabel(Canvas canvas, int note, float centerX, float baseline,
                           int color, float sizeSp) {
        String label = noteName(note) + octave(note);
        float density = getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(sizeSp * density);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        canvas.drawText(label, centerX, baseline, paint);
        paint.setFakeBoldText(false);
    }

    private int noteAt(float x, float y) {
        int whiteIndex = 0;
        float height = getHeight();
        if (y <= height * blackKeyHeight) {
            for (int note = LOWEST_NOTE; note <= HIGHEST_NOTE; note++) {
                if (isBlack(note)) {
                    float centerX = whiteIndex * whiteKeyWidth;
                    if (Math.abs(x - centerX) <= blackKeyWidth * 0.5f) return note;
                } else {
                    whiteIndex++;
                }
            }
        }
        int white = Math.max(0, Math.min((int) (x / whiteKeyWidth),
                getWhiteKeyCount() - 1));
        int index = 0;
        for (int note = LOWEST_NOTE; note <= HIGHEST_NOTE; note++) {
            if (!isBlack(note) && index++ == white) return note;
        }
        return HIGHEST_NOTE;
    }

    private int getWhiteKeyCount() {
        int count = 0;
        for (int note = LOWEST_NOTE; note <= HIGHEST_NOTE; note++) {
            if (!isBlack(note)) count++;
        }
        return count;
    }

    private static boolean isBlack(int note) {
        int pitchClass = note % 12;
        return pitchClass == 1 || pitchClass == 3 || pitchClass == 6
                || pitchClass == 8 || pitchClass == 10;
    }

    private static String noteName(int note) {
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G",
                "G#", "A", "A#", "B"};
        return names[note % 12];
    }

    private static int octave(int note) {
        return note / 12 - 1;
    }

    private boolean isNotePressed(int note) {
        for (int i = 0; i < activePointers.size(); i++) {
            if (activePointers.valueAt(i) == note) return true;
        }
        return false;
    }

    private void releasePointer(int pointerId) {
        int note = activePointers.get(pointerId, -1);
        if (note >= 0) {
            activePointers.delete(pointerId);
            if (!isNotePressed(note) && listener != null) listener.onNoteOff(note);
            invalidate();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL) {
            for (int i = activePointers.size() - 1; i >= 0; i--) {
                releasePointer(activePointers.keyAt(i));
            }
            return true;
        }

        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int note = noteAt(event.getX(pointerIndex), event.getY(pointerIndex));
            activePointers.put(pointerId, note);
            if (listener != null) listener.onNoteOn(note);
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            releasePointer(pointerId);
            return true;
        }
        return true;
    }
}
