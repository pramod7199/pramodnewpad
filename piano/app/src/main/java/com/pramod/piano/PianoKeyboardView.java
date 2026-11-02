package com.pramod.piano;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.LinearGradient;
import android.graphics.Shader;
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
    private int transposeSemitones;

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

    void setTransposeSemitones(int semitones) {
        transposeSemitones = semitones;
        invalidate();
    }

    boolean hasActiveTouches() {
        return activePointers.size() > 0;
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
        int fallbackHeight = Math.round(240f
                * getResources().getDisplayMetrics().density);
        int height = resolveSize(fallbackHeight, heightMeasureSpec);
        setMeasuredDimension(getKeyboardWidth(), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float height = getHeight();
        float keybedTop = 5f * density;
        float keybedBottom = height - 5f * density;
        RectF casing = new RectF(0, 0, getWidth(), height);

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 0, height,
                new int[]{0xFF363B43, 0xFF111419, 0xFF050608},
                null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(casing, 8f * density, 8f * density, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        paint.setColor(0xFF737982);
        canvas.drawRoundRect(casing, 8f * density, 8f * density, paint);
        paint.setStyle(Paint.Style.FILL);

        int whiteIndex = 0;
        for (int note = LOWEST_NOTE; note <= HIGHEST_NOTE; note++) {
            if (isBlack(note)) continue;
            float left = whiteIndex * whiteKeyWidth;
            float right = left + whiteKeyWidth;
            float gap = Math.max(1f, density);
            float keyBottom = keybedBottom - 2f * density;
            paint.setShader(new LinearGradient(0, keybedTop, 0, keyBottom,
                    isNotePressed(note)
                            ? new int[]{0xFFD8E9FF, 0xFF76B0FF, 0xFF397FD8}
                            : new int[]{0xFFFFFFFF, 0xFFF9F8F3, 0xFFE6E2D9},
                    null, Shader.TileMode.CLAMP));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(new RectF(left + gap, keybedTop, right - gap, keyBottom),
                    2f * density, 2f * density, paint);
            paint.setShader(null);
            paint.setColor(0xFF111827);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, density));
            if (isNotePressed(note)) paint.setColor(0xFF1458A8);
            canvas.drawRoundRect(new RectF(left + gap, keybedTop, right - gap, keyBottom),
                    2f * density, 2f * density, paint);
            drawLabel(canvas, note, (left + right) * 0.5f,
                    keyBottom - 10f * density,
                    0xFF111827, 9f);
            whiteIndex++;
        }

        whiteIndex = 0;
        float blackKeyBottom = keybedTop + (keybedBottom - keybedTop) * blackKeyHeight;
        for (int note = LOWEST_NOTE; note <= HIGHEST_NOTE; note++) {
            if (isBlack(note)) {
                float centerX = whiteIndex * whiteKeyWidth;
                float left = centerX - blackKeyWidth * 0.5f;
                float right = centerX + blackKeyWidth * 0.5f;
                paint.setStyle(Paint.Style.FILL);
                paint.setShader(new LinearGradient(left, keybedTop, right, blackKeyBottom,
                        isNotePressed(note)
                                ? new int[]{0xFF9CCBFF, 0xFF347FD4, 0xFF17457D}
                                : new int[]{0xFF41464D, 0xFF15181D, 0xFF050608},
                        null, Shader.TileMode.CLAMP));
                float radius = 3f * density;
                RectF blackKey = new RectF(left, keybedTop, right, blackKeyBottom);
                canvas.drawRoundRect(blackKey, radius, radius, paint);
                paint.setShader(null);
                paint.setColor(isNotePressed(note) ? 0xFFC6E1FF : 0xFF050608);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth((isNotePressed(note) ? 2f : 1f) * density);
                canvas.drawRoundRect(blackKey, radius, radius, paint);
                drawLabel(canvas, note, centerX,
                        blackKeyBottom - 8f * density, 0xFFFFFFFF, 7f);
            } else {
                whiteIndex++;
            }
        }
    }

    private void drawLabel(Canvas canvas, int note, float centerX, float baseline,
                           int color, float sizeSp) {
        int soundingNote = note + transposeSemitones;
        String label = noteName(soundingNote) + octave(soundingNote);
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
        float density = getResources().getDisplayMetrics().density;
        float keybedTop = 5f * density;
        float blackKeyBottom = keybedTop
                + (getHeight() - 10f * density) * blackKeyHeight;
        if (y >= keybedTop && y <= blackKeyBottom) {
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
        int soundingNote = note + transposeSemitones;
        for (int i = 0; i < activePointers.size(); i++) {
            if (activePointers.valueAt(i) == soundingNote) return true;
        }
        return false;
    }

    private void releasePointer(int pointerId) {
        int note = activePointers.get(pointerId, -1);
        if (note >= 0) {
            activePointers.delete(pointerId);
            releaseNoteIfUnused(note);
            invalidate();
        }
    }

    private void updatePointerNote(int pointerId, int newNote) {
        int previousNote = activePointers.get(pointerId, -1);
        if (previousNote == newNote) return;
        if (previousNote >= 0) {
            activePointers.delete(pointerId);
            releaseNoteIfUnused(previousNote);
        }
        boolean noteWasAlreadyActive = isNoteActive(newNote);
        activePointers.put(pointerId, newNote);
        if (!noteWasAlreadyActive && listener != null) listener.onNoteOn(newNote);
        invalidate();
    }

    private void releaseNoteIfUnused(int note) {
        if (isNoteActive(note)) return;
        if (listener != null) listener.onNoteOff(note);
    }

    private boolean isNoteActive(int note) {
        for (int i = 0; i < activePointers.size(); i++) {
            if (activePointers.valueAt(i) == note) return true;
        }
        return false;
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
            updatePointerNote(pointerId,
                    noteAt(event.getX(pointerIndex), event.getY(pointerIndex))
                            + transposeSemitones);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                int movedPointerId = event.getPointerId(i);
                if (activePointers.indexOfKey(movedPointerId) >= 0) {
                    updatePointerNote(movedPointerId,
                            noteAt(event.getX(i), event.getY(i)) + transposeSemitones);
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            releasePointer(pointerId);
            return true;
        }
        return true;
    }
}
