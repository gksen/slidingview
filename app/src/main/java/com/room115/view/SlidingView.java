package com.room115.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Scroller;

import com.gksen.view.R;

/**
 * @author Konstantin Gvozdyuk (kgv@115room.com)
 */
public class SlidingView extends ViewGroup {

    public static final float OPEN_FACTOR = 0.5f;
    private int mStaticLayerId;
    private int mSlidingLayerId;
    private View mStaticLayer;
    private View mSlidingLayer;
    private GestureDetector mGestureDetector;
    private float mOpenFactor = OPEN_FACTOR;
    private Wipers mWipers;
    private boolean isFling;
    private float mLastX;
    private float mLastY;
    private int mScaledTouchSlop;
    private OnOpenViewListener mOpenListener;
    private OnSelectViewListener mSelectListener;
    private boolean mTouchable = true;
    private boolean needNotifySliding = true;
    private boolean isOpened;
    private boolean isActive;

    public SlidingView(Context context) {
        this(context, null);
    }

    public SlidingView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlidingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.SlidingView, defStyleAttr, 0);
        try {
            mStaticLayerId = a.getResourceId(R.styleable.SlidingView_staticLayer, 0);
            mSlidingLayerId = a.getResourceId(R.styleable.SlidingView_slidingLayer, 0);
        } finally {
            a.recycle();
        }

        if (mStaticLayerId == 0) {
            throw new IllegalArgumentException("The staticLayer attribute is required and must refer "
                    + "to a valid child.");
        }

        if (mSlidingLayerId == 0) {
            throw new IllegalArgumentException("The slidingLayer attribute is required and must refer "
                    + "to a valid child.");
        }

        if (mStaticLayerId == mSlidingLayerId) {
            throw new IllegalArgumentException("The staticLayer and slidingLayer attributes must refer "
                    + "to different children.");
        }

        mGestureDetector = new GestureDetector(context, new GestureListener());
        mWipers = new Wipers();
        mScaledTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mStaticLayer = findViewById(mStaticLayerId);
        if (mStaticLayer == null) {
            throw new IllegalArgumentException("The staticLayer attribute is must refer to an"
                    + " existing child.");
        }

        mStaticLayer.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return false;
            }
        });
        ViewGroup staticLayer = (ViewGroup) this.mStaticLayer;
        int childCount = staticLayer.getChildCount();
        for (int i = 0; i < childCount; i++) {
            staticLayer.getChildAt(i).setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return false;
                }
            });
        }
        mSlidingLayer = findViewById(mSlidingLayerId);
        if (mSlidingLayer == null) {
            throw new IllegalArgumentException("The slidingLayer attribute is must refer to an existing child.");
        }

        mSlidingLayer.setOnTouchListener(new OnTouchListener() {
            GestureDetector gestureDetector = new GestureDetector(getContext(),
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onSingleTapUp(MotionEvent e) {
                            if (isOpened) {
                                close();
                                return true;
                            }
                            return false;
                        }
                    });

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return false;
            }
        });

        mSlidingLayer.bringToFront();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int childCount = getChildCount();

        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);

        final int childCount = getChildCount();
        if (childCount > 2) {
            throw new RuntimeException("SlidingView can not contain more than 2 child!");
        }

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final int childWidthSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec
                    .EXACTLY);
            final int childHeightSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec),
                    MeasureSpec.EXACTLY);

            child.measure(childWidthSpec, childHeightSpec);
        }
    }

    public boolean isOpened() {
        return isOpened;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setSelectListener(OnSelectViewListener listener) {
        mSelectListener = listener;
    }

    public void setOpenListener(OnOpenViewListener listener) {
        mOpenListener = listener;
    }

    public void setTouchable(boolean touchable) {
        mTouchable = touchable;
    }

    public void open() {
        int distance = getRightLimit() - mSlidingLayer.getLeft();
        mWipers.scrollTo(distance);
    }

    public void close() {
        int distance = -mSlidingLayer.getLeft();
        mWipers.scrollTo(distance);
    }

    private void slideLayer(int x) {
        if (needNotifySliding && mOpenListener != null) {
            mOpenListener.onSliding();
            needNotifySliding = false;
        }

        int left = mSlidingLayer.getLeft();
        int destination = left + x;

        if (destination < 0) {
            destination = -left;
        } else if (destination > getRightLimit()) {
            destination = getRightLimit() - left;
        } else {
            destination = x;
        }

        if (destination != 0) {
            mSlidingLayer.offsetLeftAndRight(destination);
        }
    }

    private int getRightLimit() {
        return (int) (getMeasuredWidth() * getOpenFactor());
    }

    private float getOpenFactor() {
        return mOpenFactor;
    }

    public void setOpenFactor(float factor) {
        mOpenFactor = factor;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mLastX = ev.getX();
                mLastY = ev.getY();

                Rect slideRect = new Rect();
                mSlidingLayer.getHitRect(slideRect);
                return slideRect.contains((int) mLastX, (int) mLastY);
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
//                restore();
                break;
            case MotionEvent.ACTION_MOVE:
                float x = ev.getX();
                float y = ev.getY();
                float xDelta = Math.abs(x - mLastX);
                float yDelta = Math.abs(y - mLastY);

//                float yDeltaTotal = y - mStartY;
                if (xDelta > yDelta && Math.abs(xDelta) > mScaledTouchSlop) {
//                    mIsBeingDragged = true;
//                    mStartY = y;
                    return true;
                }
                break;
        }

        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mTouchable) {
            return false;
        }

        mGestureDetector.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_CANCEL:
                restore();
            case MotionEvent.ACTION_UP:
                if (!isFling) {
                    restore();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                float y = event.getY();

                float xDelta = Math.abs(x - mLastX);
                float yDelta = Math.abs(y - mLastY);

                if (xDelta > yDelta && Math.abs(xDelta) > mScaledTouchSlop) {
//                    mIsBeingDragged = true;
                }

                mLastX = x;
                mLastY = y;
                break;
        }

        return true;
    }

    public void setActive(boolean active) {
        isActive = active;
        mStaticLayer.setSelected(active);
        mSlidingLayer.setSelected(active);
        if (active) {
            if (mSelectListener != null) {
                mSelectListener.onSelect();
            }
        } else {
            if (mSelectListener != null) {
                mSelectListener.onDeselect();
            }
            // ?
//            close();
        }
    }

    private void restore() {
        boolean needOpen = mSlidingLayer.getLeft() > (getRightLimit() / 2);
        if (needOpen) {
            open();
        } else {
            close();
        }
    }

    private class Wipers implements Runnable {

        Scroller mScroller;
        int mLastX;

        Wipers() {
            mScroller = new Scroller(getContext()/*, new BounceInterpolator()*/);
        }

        void scrollTo(int distance) {
            stop();
            mScroller.startScroll(0, 0, distance, 0, 1000);
            mLastX = 0;

            post(this);
        }

        void stop() {
            mScroller.forceFinished(true);
        }

        @Override
        public void run() {
            if (!mScroller.isFinished() && mScroller.computeScrollOffset()) {
                mLastX = mScroller.getCurrX() - mLastX;
                int delta = mLastX;
                if (delta != 0) {
                    slideLayer(delta);
                }
                postDelayed(this, 16);
            }
            if (mScroller.isFinished()) {
                isFling = false;
                needNotifySliding = true;
                if (mSlidingLayer.getLeft() == 0) {
                    isOpened = false;
                    if (mOpenListener != null) {
                        mOpenListener.onClosed();
                    }
                }
                if (mSlidingLayer.getLeft() == getRightLimit()) {
                    isOpened = true;
                    if (mOpenListener != null) {
                        mOpenListener.onOpened();
                    }
                }
            }
        }

    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        final float density = getContext().getResources().getDisplayMetrics().density;
        private int mFlingDistance = (int) (25 * density);
        private int mMinimumVelocity = ViewConfiguration.get(getContext()).getScaledMinimumFlingVelocity();

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return super.onSingleTapUp(e);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            mWipers.stop();
            slideLayer((int) -distanceX);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (Math.abs(e1.getX() - e2.getX()) > mFlingDistance
                    && Math.abs(velocityX) > mMinimumVelocity) {
                if (velocityX > 0) {
                    open();
                } else {
                    close();
                }
                isFling = true;
                return true;
            }
            return super.onFling(e1, e2, velocityX, velocityY);
        }
    }

    public interface OnSelectViewListener {

        void onSelect();

        void onDeselect();
    }

    public interface OnOpenViewListener {

        void onOpened();

        void onClosed();

        void onSliding();
    }

}
