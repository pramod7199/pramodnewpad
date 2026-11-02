package com.pramod.piano;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;

public final class LockableHorizontalScrollView extends HorizontalScrollView {
    private boolean scrollLocked;

    public LockableHorizontalScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setScrollLocked(boolean locked) {
        scrollLocked = locked;
    }

    public boolean isScrollLocked() {
        return scrollLocked;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return !scrollLocked && super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return scrollLocked || super.onTouchEvent(event);
    }
}
