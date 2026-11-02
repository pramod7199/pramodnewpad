package com.pramod.piano;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
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
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whiteKeyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whitePressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whiteEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blackKeyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blackPressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blackEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SparseIntArray activePointers = new SparseIntArray();
    private final SparseIntArray externalNotes = new SparseIntArray();
    private float whiteKeyWidth;
    private float blackKeyWidth;
    private final float blackKeyHeight;
    private NoteListener listener;
    private int transposeSemitones;

    public PianoKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        whiteKeyWidth = 42f * density;
        blackKeyWidth = whiteKeyWidth * 0.62f;
        blackKeyHeight = 0.62f;
        backgroundPaint.setColor(0xFF090B0E);
        backgroundPaint.setStyle(Paint.Style.FILL);
        whiteKeyPaint.setColor(0xFFF7F4EC);
        whiteKeyPaint.setStyle(Paint.Style.FILL);
        whitePressedPaint.setColor(0xFF8DBBFA);
        whitePressedPaint.setStyle(Paint.Style.FILL);
        whiteEdgePaint.setStyle(Paint.Style.STROKE);
        whiteEdgePaint.setStrokeWidth(Math.max(1f, density));
        blackKeyPaint.setColor(0xFF111318);
        blackKeyPaint.setStyle(Paint.Style.FILL);
        blackPressedPaint.setColor(0xFF397FD8);
        blackPressedPaint.setStyle(Paint.Style.FILL);
        blackEdgePaint.setStyle(Paint.Style.STROKE);
        labelPaint.setStyle(Paint.Style.FILL);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        setWillNotDraw(false);
    }

    void setNoteListener(NoteListener listener) {
        this.listener = listener;
    }

    void setTransposeSemitones(int semitones) {
        transposeSemitones = semitones;
        invalidate();
    }

    void changeZoom(float factor) {
        float baseWidth = 42f * getResources().getDisplayMetrics().density;
        whiteKeyWidth = Math.max(baseWidth * 0.65f,
                Math.min(baseWidth * 1.65f, whiteKeyWidth * factor));
        blackKeyWidth = whiteKeyWidth * 0.62f;
        requestLayout();
        invalidate();
    }

    void setExternalNote(int note, boolean pressed) {
        if (pressed) {
            externalNotes.put(note, 1);
        } else {
            externalNotes.delete(note);
        }
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
        int densityHeight = Math.round(240f
                * getResources().getDisplayMetrics().density);
        int desiredWidth = getKeyboardWidth();
        int measuredHeight = resolveSize(densityHeight, heightMeasureSpec);
        setMeasuredDimension(
                desiredWidth,
                measuredHeight > 0 ? measuredHeight : densityHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float height = getHeight();
        float keybedTop = 5f * density;
        float keybedBottom = height - 5f * density;
        canvas.drawRect(0, 0, getWidth(), height, backgroundPaint);

        int whiteIndex = 0;
        for (int note = LOWEST_NOTE; note <= HIGHEST_NOTE; note++) {
            if (isBlack(note)) continue;
            float left = whiteIndex * whiteKeyWidth;
            float right = left + whiteKeyWidth;
            float keyBottom = keybedBottom - 2f * density;
            boolean pressed = isNotePressed(note);
            RectF key = new RectF(left + density, keybedTop, right - density, keyBottom);
            canvas.drawRect(key, pressed ? whitePressedPaint : whiteKeyPaint);
            whiteEdgePaint.setColor(pressed ? 0xFF174E91 : 0xFFAAA69E);
            canvas.drawRect(key, whiteEdgePaint);
            backgroundPaint.setColor(0xFFD7D2C7);
            canvas.drawRect(left + 2f * density, keyBottom - 3f * density,
                    right - 2f * density, keyBottom, backgroundPaint);
            drawLabel(canvas, note, (left + right) * 0.5f,
                    keyBottom - 10f * density,
                    0xFF111827, 10f);
            backgroundPaint.setColor(0xFF090B0E);
            whiteIndex++;
        }

        whiteIndex = 0;
        float blackKeyBottom = keybedTop + (keybedBottom - keybedTop) * blackKeyHeight;
        for (int note = LOWEST_NOTE; note <= HIGHEST_NOTE; note++) {
            if (isBlack(note)) {
                float centerX = blackKeyCenterX(whiteIndex);
                float left = centerX - blackKeyWidth * 0.5f;
                float right = centerX + blackKeyWidth * 0.5f;
                boolean pressed = isNotePressed(note);
                RectF blackKey = new RectF(left, keybedTop, right, blackKeyBottom);
                canvas.drawRoundRect(blackKey, 3f * density, 3f * density,
                        pressed ? blackPressedPaint : blackKeyPaint);
                blackEdgePaint.setColor(pressed ? 0xFFC6E1FF : 0xFF050608);
                blackEdgePaint.setStrokeWidth((pressed ? 2f : 1f) * density);
                canvas.drawRoundRect(blackKey, 3f * density, 3f * density, blackEdgePaint);
                drawLabel(canvas, note, centerX,
                        blackKeyBottom - 8f * density, 0xFFFFFFFF, 8f);
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
        labelPaint.setColor(color);
        labelPaint.setTextSize(sizeSp * density);
        canvas.drawText(label, centerX, baseline, labelPaint);
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
                    float centerX = blackKeyCenterX(whiteIndex);
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

    private float blackKeyCenterX(int whiteIndex) {
        return whiteIndex * whiteKeyWidth;
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
        if (externalNotes.get(soundingNote, 0) > 0) return true;
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
