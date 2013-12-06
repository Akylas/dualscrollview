package com.akylas.view;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.FocusFinder;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AnimationUtils;
import android.widget.EdgeEffect;
import android.widget.FrameLayout;
import android.widget.OverScroller;

import java.util.List;

/**
 * Layout container for a view hierarchy that can be scrolled by the user,
 * allowing it to be larger than the physical display.  A DualScrollView
 * is a {@link FrameLayout}, meaning you should place one child in it
 * containing the entire contents to scroll; this child may itself be a layout
 * manager with a complex hierarchy of objects.  A child that is often used
 * is a {@link LinearLayout} in a vertical orientation, presenting a vertical
 * array of top-level items that the user can scroll through.
 * <p>You should never use a ScrollView with a {@link ListView}, because
 * ListView takes care of its own vertical scrolling.  Most importantly, doing this
 * defeats all of the important optimizations in ListView for dealing with
 * large lists, since it effectively forces the ListView to display its entire
 * list of items to fill up the infinite container supplied by ScrollView.
 * <p>The {@link TextView} class also
 * takes care of its own scrolling, so does not require a DualScrollView, but
 * using the two together is possible to achieve the effect of a text view
 * within a larger container.
 *
 */
@SuppressLint("NewApi")
@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class DualScrollView extends FrameLayout {
	
	private static final boolean HONEYCOMB_OR_GREATER = (Build.VERSION.SDK_INT >= 11);
	private static final boolean ICE_CREAM_OR_GREATER = (Build.VERSION.SDK_INT >= 14);
	private static final boolean JELLY_BEAN_OR_GREATER = (Build.VERSION.SDK_INT >= 16);
	
    static final int ANIMATED_SCROLL_GAP = 250;

    static final float MAX_SCROLL_FACTOR = 0.5f;

    private static final String TAG = "DualScrollView";

    private long mLastScroll;

    private final Rect mTempRect = new Rect();
    private OverScroller mScroller;
    private EdgeEffect mEdgeGlowTop;
    private EdgeEffect mEdgeGlowBottom;
    private EdgeEffect mEdgeGlowLeft;
    private EdgeEffect mEdgeGlowRight;

	private boolean shouldClampScroll = true;
	
	/**
	 * Flag to indicate that we are moving focus ourselves. This is so the code
	 * that watches for focus changes initiated outside this TwoDScrollView
	 * knows that it does not have to do anything.
	 */
	private boolean mTwoDScrollViewMovedFocus;
	
   /**
     * Position of the last motion event.
     */
    private int mLastMotionY;
    private int mLastMotionX;

    /**
     * True when the layout has changed but the traversal has not come through yet.
     * Ideally the view hierarchy would keep track of this for us.
     */
    private boolean mIsLayoutDirty = true;

    /**
     * The child to give focus to in the event that a child has requested focus while the
     * layout is dirty. This prevents the scroll from being wrong if the child has not been
     * laid out before requesting focus.
     */
    private View mChildToScrollTo = null;

    /**
     * True if the user is currently dragging this ScrollView around. This is
     * not the same as 'is being flinged', which can be checked by
     * mScroller.isFinished() (flinging begins when the user lifts his finger).
     */
    private boolean mIsBeingDragged = false;

    /**
     * Determines speed during touch scrolling
     */
    private VelocityTracker mVelocityTracker;

    /**
     * When set to true, the scroll view measure its child to make it fill the currently
     * visible area.
     */
    @ViewDebug.ExportedProperty(category = "layout")
    private boolean mFillViewport;

    /**
     * Whether arrow scrolling is animated.
     */
    private boolean mSmoothScrollingEnabled = true;

    private int mTouchSlop;
    private int mMinimumVelocity;
    private int mMaximumVelocity;

    private int mOverscrollDistance;
    private int mOverflingDistance;

    /**
     * ID of the active pointer. This is used to retain consistency during
     * drags/flings if multiple pointers are used.
     */
    private int mActivePointerId = INVALID_POINTER;

    /**
     * The StrictMode "critical time span" objects to catch animation
     * stutters.  Non-null when a time-sensitive animation is
     * in-flight.  Must call finish() on them when done animating.
     * These are no-ops on user builds.
     */
//    private StrictMode.Span mScrollStrictSpan = null;  // aka "drag"
//    private StrictMode.Span mFlingStrictSpan = null;

    /**
     * Sentinel value for no current active pointer.
     * Used by {@link #mActivePointerId}.
     */
    private static final int INVALID_POINTER = -1;

    public DualScrollView(Context context) {
        this(context, null);
    }

    public DualScrollView(Context context, AttributeSet attrs) {
    	super(context, attrs);
        initScrollView();
   }

    public DualScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initScrollView();
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return true;
    }

    @Override
    protected float getTopFadingEdgeStrength() {
        if (getChildCount() == 0) {
            return 0.0f;
        }

        final int length = getVerticalFadingEdgeLength();
        if (getScrollY() < length) {
            return getScrollY() / (float) length;
        }

        return 1.0f;
    }

    @Override
    protected float getBottomFadingEdgeStrength() {
        if (getChildCount() == 0) {
            return 0.0f;
        }

        final int length = getVerticalFadingEdgeLength();
        final int bottomEdge = getHeight() - getPaddingBottom();
        final int span = getChildAt(0).getBottom() - getScrollY() - bottomEdge;
        if (span < length) {
            return span / (float) length;
        }

        return 1.0f;
    }

    @Override
    protected float getLeftFadingEdgeStrength() {
        if (getChildCount() == 0) {
            return 0.0f;
        }
        final int length = getHorizontalFadingEdgeLength();
        if (getScrollX() < length) {
            return getScrollX() / (float) length;
        }
        return 1.0f;
    }

    @Override
    protected float getRightFadingEdgeStrength() {
        if (getChildCount() == 0) {
            return 0.0f;
        }
        final int length = getHorizontalFadingEdgeLength();
        final int rightEdge = getWidth() - getPaddingRight();
        final int span = getChildAt(0).getRight() - getScrollX() - rightEdge;
        if (span < length) {
            return span / (float) length;
        }
        return 1.0f;
    }

    /**
	 * @return The maximum amount this scroll view will scroll in response to an
	 *         arrow event.
	 */
	public int getMaxScrollAmountVertical() {
		return (int) (MAX_SCROLL_FACTOR * getHeight());
	}

	public int getMaxScrollAmountHorizontal() {
		return (int) (MAX_SCROLL_FACTOR * getWidth());
	}

    private void initScrollView() {
        mScroller = new OverScroller(getContext());
        setFocusable(true);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        setWillNotDraw(false);
        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mOverscrollDistance = configuration.getScaledOverscrollDistance();
        mOverflingDistance = configuration.getScaledOverflingDistance();
    }

    @Override
    public void addView(View child) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("ScrollView can host only one direct child");
        }

        super.addView(child);
    }

    @Override
    public void addView(View child, int index) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("ScrollView can host only one direct child");
        }

        super.addView(child, index);
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("ScrollView can host only one direct child");
        }

        super.addView(child, params);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("ScrollView can host only one direct child");
        }

        super.addView(child, index, params);
    }

    /**
     * @return Returns true this ScrollView can be scrolled
     */
    private boolean canScroll() {
        return canScrollH() || canScrollV();
    }
    
    private boolean canScrollH() {
        View child = getChildAt(0);
        if (child != null) {
            int childWidth = child.getWidth();
            return (getWidth() < childWidth + getPaddingLeft()
                            + getPaddingRight());
        }
        return false;
    }
    private boolean canScrollV() {
        View child = getChildAt(0);
        if (child != null) {
            int childHeight = child.getHeight();
            return (getHeight() < childHeight + getPaddingTop()
                    + getPaddingBottom());
        }
        return false;
    }

    /**
     * Indicates whether this ScrollView's content is stretched to fill the viewport.
     *
     * @return True if the content fills the viewport, false otherwise.
     *
     * @attr ref android.R.styleable#ScrollView_fillViewport
     */
    public boolean isFillViewport() {
        return mFillViewport;
    }

    /**
     * Indicates this ScrollView whether it should stretch its content height to fill
     * the viewport or not.
     *
     * @param fillViewport True to stretch the content's height to the viewport's
     *        boundaries, false otherwise.
     *
     * @attr ref android.R.styleable#ScrollView_fillViewport
     */
    public void setFillViewport(boolean fillViewport) {
        if (fillViewport != mFillViewport) {
            mFillViewport = fillViewport;
            requestLayout();
        }
    }

    /**
     * @return Whether arrow scrolling will animate its transition.
     */
    public boolean isSmoothScrollingEnabled() {
        return mSmoothScrollingEnabled;
    }

    /**
     * Set whether arrow scrolling will animate its transition.
     * @param smoothScrollingEnabled whether arrow scrolling will animate its transition
     */
    public void setSmoothScrollingEnabled(boolean smoothScrollingEnabled) {
        mSmoothScrollingEnabled = smoothScrollingEnabled;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (!mFillViewport) {
            return;
        }

        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (heightMode == MeasureSpec.UNSPECIFIED) {
            return;
        }

        if (getChildCount() > 0) {
            final View child = getChildAt(0);
            int height = getMeasuredHeight();
            if (child.getMeasuredHeight() < height) {
                final FrameLayout.LayoutParams lp = (LayoutParams) child.getLayoutParams();

                int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
                        getPaddingLeft() + getPaddingRight(), lp.width);
                height -= getPaddingTop();
                height -= getPaddingBottom();
                int childHeightMeasureSpec =
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);

                child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Let the focused view and/or our descendants get the key first
        return super.dispatchKeyEvent(event) || executeKeyEvent(event);
    }

    /**
     * You can call this function yourself to have the scroll view perform
     * scrolling from a key event, just as if the event had been dispatched to
     * it by the view hierarchy.
     *
     * @param event The key event to execute.
     * @return Return true if the event was handled, else false.
     */
    public boolean executeKeyEvent(KeyEvent event) {
        mTempRect.setEmpty();

        if (!canScroll()) {
            if (isFocused() && event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
                View currentFocused = findFocus();
                if (currentFocused == this) currentFocused = null;
                View nextFocused = FocusFinder.getInstance().findNextFocus(this,
                        currentFocused, View.FOCUS_DOWN);
                return nextFocused != null
                        && nextFocused != this
                        && nextFocused.requestFocus(View.FOCUS_DOWN);
            }
            return false;
        }

        boolean handled = false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_UP, false);
                    } else {
                        handled = fullScroll(View.FOCUS_UP, false);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_DOWN, false);
                    } else {
                        handled = fullScroll(View.FOCUS_DOWN, false);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_LEFT, true);
                    } else {
                        handled = fullScroll(View.FOCUS_LEFT, true);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_RIGHT, true);
                    } else {
                        handled = fullScroll(View.FOCUS_RIGHT, true);
                    }
                    break;
                case KeyEvent.KEYCODE_SPACE:
                    pageScroll(event.isShiftPressed() ? View.FOCUS_UP : View.FOCUS_DOWN, false);
                    break;
            }
        }
        return handled;
    }

    private boolean inChild(int x, int y) {
        if (getChildCount() > 0) {
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();
            final View child = getChildAt(0);
            return !(y < child.getTop() - scrollY
                    || y >= child.getBottom() - scrollY
                    || x < child.getLeft() - scrollX
                    || x >= child.getRight() - scrollX);
        }
        return false;
    }

    private void initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }

    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept) {
            recycleVelocityTracker();
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }


    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onMotionEvent will be called and we do the actual
         * scrolling there.
         */

        /*
        * Shortcut the most recurring case: the user is in the dragging
        * state and he is moving his finger.  We want to intercept this
        * motion.
        */
        final int action = ev.getAction();
        if ((action == MotionEvent.ACTION_MOVE) && (mIsBeingDragged)) {
            return true;
        }

        /*
         * Don't try to intercept touch if we can't scroll anyway.
         */
        if (!canScroll()) {
            return false;
        }

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE: {
            	/*
                 * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
                 * whether the user has moved far enough from his original down touch.
                 */

                /*
                * Locally do absolute value. mLastMotionY is set to the y value
                * of the down event.
                */
                final int activePointerId = mActivePointerId;
                if (activePointerId == INVALID_POINTER) {
                    // If we don't have a valid id, the touch down wasn't on content.
                    break;
                }

                final int pointerIndex = ev.findPointerIndex(activePointerId);
                if (pointerIndex == -1) {
                    Log.e(TAG, "Invalid pointerId=" + activePointerId
                            + " in onInterceptTouchEvent");
                    break;
                }

                final int x = (int) ev.getX(pointerIndex);
                final int y = (int) ev.getY(pointerIndex);
                final int xDiff = Math.abs(x - mLastMotionX);
                final int yDiff = Math.abs(y - mLastMotionY);
                if (yDiff > mTouchSlop || xDiff > mTouchSlop) {
                    mIsBeingDragged = true;
                    mLastMotionX = x;
                    mLastMotionY = y;
                    initVelocityTrackerIfNotExists();
                    mVelocityTracker.addMovement(ev);
//                    if (mScrollStrictSpan == null) {
//                        mScrollStrictSpan = StrictMode.enterCriticalSpan("ScrollView-scroll");
//                    }
                    final ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                }
                break;
            }

            case MotionEvent.ACTION_DOWN: {
                final int x = (int) ev.getX();
                final int y = (int) ev.getY();
                if (!inChild((int) x, (int) y)) {
                    mIsBeingDragged = false;
                    recycleVelocityTracker();
                    break;
                }

                /*
                 * Remember location of down touch.
                 * ACTION_DOWN always refers to pointer index 0.
                 */
                mLastMotionX = x;
                mLastMotionY = y;
                mActivePointerId = ev.getPointerId(0);

                initOrResetVelocityTracker();
                mVelocityTracker.addMovement(ev);
                /*
                * If being flinged and user touches the screen, initiate drag;
                * otherwise don't.  mScroller.isFinished should be false when
                * being flinged.
                */
                mIsBeingDragged = !mScroller.isFinished();
//                if (mIsBeingDragged && mScrollStrictSpan == null) {
//                    mScrollStrictSpan = StrictMode.enterCriticalSpan("ScrollView-scroll");
//                }
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                /* Release the drag */
                mActivePointerId = INVALID_POINTER;
                recycleVelocityTracker();
                if (mIsBeingDragged) {
                    mIsBeingDragged = false;
                    springBack();
                }
                
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }

        /*
        * The only time we want to intercept motion events is if we are in the
        * drag mode.
        */
        return mIsBeingDragged;
    }
    
    @Override
	public void postInvalidateOnAnimation()
    {
    	if (JELLY_BEAN_OR_GREATER)
    	{
    		super.postInvalidateOnAnimation();
    	}
    }
    
    @SuppressWarnings({"UnusedParameters"})
    protected boolean overScrollBy(int deltaX, int deltaY,
            int scrollX, int scrollY,
            int scrollRangeX, int scrollRangeY,
            int maxOverScrollX, int maxOverScrollY,
            boolean isTouchEvent) {
        final int overScrollMode = getOverScrollMode();
        final boolean canScrollHorizontal =
                computeHorizontalScrollRange() > computeHorizontalScrollExtent();
        final boolean canScrollVertical =
                computeVerticalScrollRange() > computeVerticalScrollExtent();
        final boolean overScrollHorizontal = overScrollMode == OVER_SCROLL_ALWAYS ||
                (overScrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && canScrollHorizontal);
        final boolean overScrollVertical = overScrollMode == OVER_SCROLL_ALWAYS ||
                (overScrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && canScrollVertical);

        int newScrollX = scrollX + deltaX;
        if (!overScrollHorizontal) {
            maxOverScrollX = 0;
        }

        int newScrollY = scrollY + deltaY;
        if (!overScrollVertical) {
            maxOverScrollY = 0;
        }

        // Clamp values if at the limits and record
        final int left = -maxOverScrollX;
        final int right = maxOverScrollX + scrollRangeX;
        final int top = -maxOverScrollY;
        final int bottom = maxOverScrollY + scrollRangeY;

        boolean clampedX = false;
        if (newScrollX > right) {
            newScrollX = right;
            clampedX = true;
        } else if (newScrollX < left) {
            newScrollX = left;
            clampedX = true;
        }

        boolean clampedY = false;
        if (newScrollY > bottom) {
            newScrollY = bottom;
            clampedY = true;
        } else if (newScrollY < top) {
            newScrollY = top;
            clampedY = true;
        }

        onOverScrolled(newScrollX, newScrollY, clampedX, clampedY);

        return clampedX && clampedY;
    }
    
    private void springBack() 
    {
    	if (mScroller.springBack(getScrollX(), getScrollY(), 0, getScrollRangeX(), 0,
                getScrollRangeY())) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
		shouldClampScroll = true;
        initVelocityTrackerIfNotExists();
        mVelocityTracker.addMovement(ev);

        final int action = ev.getAction();
        final int rangeX = getScrollRangeX();
        final int rangeY = getScrollRangeY();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                if (getChildCount() == 0) {
                    return false;
                }
                if ((mIsBeingDragged = !mScroller.isFinished())) {
                    final ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                }

                /*
                 * If being flinged and user touches, stop the fling. isFinished
                 * will be false if being flinged.
                 */
                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
//                    if (mFlingStrictSpan != null) {
//                        mFlingStrictSpan.finish();
//                        mFlingStrictSpan = null;
//                    }
                }

                // Remember where the motion event started
                mLastMotionX = (int) ev.getX();
                mLastMotionY = (int) ev.getY();
                mActivePointerId = ev.getPointerId(0);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                if (activePointerIndex == -1) {
                    Log.e(TAG, "Invalid pointerId=" + mActivePointerId + " in onTouchEvent");
                    break;
                }
                
                final int x = (int) ev.getX(activePointerIndex);
                final int y = (int) ev.getY(activePointerIndex);
                int deltaX = mLastMotionX - x;
                int deltaY = mLastMotionY - y;
                if (rangeX == 0) deltaX = 0;
                if (rangeY == 0) deltaY = 0;
                if (!mIsBeingDragged && (Math.abs(deltaX) > mTouchSlop || Math.abs(deltaY) > mTouchSlop)) {
                    final ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                    mIsBeingDragged = true;
                    if (deltaY != 0) {
	                    if (deltaY > 0) {
	                        deltaY -= mTouchSlop;
	                    } else {
	                       deltaY += mTouchSlop;
	                    }
                    }

                    if (deltaX != 0) {
	                    if (deltaX > 0) {
	                        deltaX -= mTouchSlop;
	                    } else {
	                        deltaX += mTouchSlop;
	                    }
                    }
                }
                if (mIsBeingDragged) {
                    // Scroll to follow the motion event
                    mLastMotionX = x;
                    mLastMotionY = y;

                    final int oldX = getScrollX();
                    final int oldY = getScrollY();
                    
                    final int overscrollMode = getOverScrollMode();
                    
                    final boolean forceOverscroll = overscrollMode == OVER_SCROLL_ALWAYS;
                    final boolean canOverscroll = ICE_CREAM_OR_GREATER && (forceOverscroll || overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS);
                    
                    if (overScrollBy(deltaX, deltaY, getScrollX(), getScrollY(),
                            rangeX, rangeY, mOverscrollDistance, mOverscrollDistance, true)) {
                        // Break our velocity if we hit a scroll barrier.
//                        mVelocityTracker.clear();
                    }
                    onScrollChanged(getScrollX(), getScrollY(), oldX, oldY);

                    if (canOverscroll) {
                        if (rangeX > 0 || forceOverscroll) {
                            final int pulledToX = oldX + deltaX;
	                        if (pulledToX < 0) {
	                            mEdgeGlowLeft.onPull((float) deltaX / getWidth());
	                            if (!mEdgeGlowRight.isFinished()) {
	                                mEdgeGlowRight.onRelease();
	                            }
	                        } else if (pulledToX > rangeX) {
	                            mEdgeGlowRight.onPull((float) deltaX / getWidth());
	                            if (!mEdgeGlowLeft.isFinished()) {
	                                mEdgeGlowLeft.onRelease();
	                            }
	                        }
	
	                        if (mEdgeGlowLeft != null
	                                && (!mEdgeGlowLeft.isFinished() || !mEdgeGlowRight.isFinished())) {
	                            postInvalidateOnAnimation();
	                        }
                        }
                        if (rangeY > 0 || forceOverscroll) {
                            final int pulledToY = oldY + deltaY;
	                        if (pulledToY < 0) {
	                            mEdgeGlowTop.onPull((float) deltaY / getHeight());
	                            if (!mEdgeGlowBottom.isFinished()) {
	                                mEdgeGlowBottom.onRelease();
	                            }
	                        } else if (pulledToY > rangeY) {
	                            mEdgeGlowBottom.onPull((float) deltaY / getHeight());
	                            if (!mEdgeGlowTop.isFinished()) {
	                                mEdgeGlowTop.onRelease();
	                            }
	                        }
	                        if (mEdgeGlowTop != null
	                                && (!mEdgeGlowTop.isFinished() || !mEdgeGlowBottom.isFinished())) {
	                            postInvalidateOnAnimation();
	                        }
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mIsBeingDragged) {
                	final VelocityTracker velocityTracker = mVelocityTracker;
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int initialXVelocity = (int) velocityTracker.getXVelocity(mActivePointerId);
                    int initialYVelocity = (int) velocityTracker.getYVelocity(mActivePointerId);
                    if (getChildCount() > 0) {
                       if (rangeX == 0 || Math.abs(initialXVelocity) <= mMinimumVelocity) initialXVelocity = 0;
                        if (rangeY == 0 || Math.abs(initialYVelocity) <= mMinimumVelocity) initialYVelocity = 0;
                        
                        if (initialXVelocity != 0 || initialYVelocity != 0) {
                            fling(-initialXVelocity, -initialYVelocity);
                        } else {
                        	springBack();
                        }
                    }

                    mActivePointerId = INVALID_POINTER;
                    endDrag();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mIsBeingDragged && getChildCount() > 0) {
                	springBack();
                    mActivePointerId = INVALID_POINTER;
                    endDrag();
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = ev.getActionIndex();
                mLastMotionX = (int) ev.getX(index);
                mLastMotionY = (int) ev.getY(index);
                mActivePointerId = ev.getPointerId(index);
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                final int index = ev.findPointerIndex(mActivePointerId);
                mLastMotionX = (int) ev.getX(index);
                mLastMotionY = (int) ev.getY(index);
                break;
        }
        return true;
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionX = (int) ev.getX(newPointerIndex);
            mLastMotionY = (int) ev.getY(newPointerIndex);
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }
    
    protected float getVerticalScrollFactor() {
        return 1;
    }

    /**
     * Gets a scale factor that determines the distance the view should scroll
     * horizontally in response to {@link MotionEvent#ACTION_SCROLL}.
     * @return The horizontal scroll scale factor.
     * @hide
     */
    protected float getHorizontalScrollFactor() {
        // TODO: Should use something else.
        return getVerticalScrollFactor();
    }
    
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_SCROLL: {
                    if (!mIsBeingDragged) {
                        final float hscroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
                        final float vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                        if (hscroll != 0 || vscroll != 0) {
                            final int deltaX = (int) (vscroll * getHorizontalScrollFactor());
                            final int deltaY = (int) (vscroll * getVerticalScrollFactor());
                            final int rangeX = getScrollRangeX();
                            final int rangeY = getScrollRangeY();
                            int oldScrollX = getScrollX();
                            int oldScrollY = getScrollY();
                            int newScrollX = oldScrollX - deltaX;
                            int newScrollY = oldScrollY - deltaY;
                            if (newScrollX < 0) {
                                newScrollX = 0;
                            } else if (newScrollX > rangeX) {
                                newScrollX = rangeX;
                            }
                            if (newScrollY < 0) {
                                newScrollY = 0;
                            } else if (newScrollY > rangeY) {
                                newScrollY = rangeY;
                            }
                            if (newScrollX != oldScrollX || newScrollY != oldScrollY) {
                                super.scrollTo(newScrollX, newScrollY);
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return super.onGenericMotionEvent(event);
    }
    
    protected void invalidateParentIfNeeded() {
        if (HONEYCOMB_OR_GREATER && isHardwareAccelerated() && getParent() instanceof View) {
            ((View) getParent()).invalidate();
        }
    }
    
    @Override
    protected void onOverScrolled(int scrollX, int scrollY,
            boolean clampedX, boolean clampedY) {
//         Treat animating scrolls differently; see #computeScroll() for why.
        if (!mScroller.isFinished()) {
//        	super.scrollTo(scrollX, scrollX);
            setScrollX(scrollX);
            setScrollY(scrollY);
            invalidateParentIfNeeded();
            if (clampedX && clampedY) {
            	springBack();
            }
        } else {
            super.scrollTo(scrollX, scrollY);
        }

        awakenScrollBars();
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (super.performAccessibilityAction(action, arguments)) {
            return true;
        }
        if (!isEnabled()) {
            return false;
        }
        switch (action) {
            case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD: {
                final int viewportWidth = getWidth() - getPaddingRight() - getPaddingLeft();
                final int viewportHeight = getHeight() - getPaddingBottom() - getPaddingTop();
                final int targetScrollX = Math.min(getScrollX() + viewportWidth, getScrollRangeX());
                final int targetScrollY = Math.min(getScrollY() + viewportHeight, getScrollRangeY());
                if (targetScrollX != getScrollX() || targetScrollY != getScrollY()) {
                    smoothScrollTo(targetScrollX, targetScrollY);
                    return true;
                }
            } return false;
            case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD: {
                final int viewportWidth = getWidth() - getPaddingRight() - getPaddingLeft();
                final int viewportHeight = getHeight() - getPaddingBottom() - getPaddingTop();
                final int targetScrollX = Math.max(getScrollX() - viewportWidth, 0);
                final int targetScrollY = Math.max(getScrollY() - viewportHeight, 0);
                if (targetScrollX != getScrollX() || targetScrollY != getScrollY()) {
                    smoothScrollTo(targetScrollX, targetScrollY);
                    return true;
                }
            } return false;
        }
        return false;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(DualScrollView.class.getName());
       if (isEnabled()) {
            final int scrollRangeX = getScrollRangeX();
            final int scrollRangeY = getScrollRangeY();
            if (scrollRangeX > 0 || scrollRangeY > 0) {
                info.setScrollable(true);
                if (getScrollX() > 0 && getScrollY() > 0) {
                    info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                }
                if (getScrollX() < scrollRangeX && getScrollY() < scrollRangeY) {
                    info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(DualScrollView.class.getName());
        final boolean scrollable = (getScrollRangeX() > 0 || getScrollRangeY() > 0);
        event.setScrollable(scrollable);
        event.setScrollX(getScrollX());
        event.setScrollY(getScrollY());
        if (JELLY_BEAN_OR_GREATER) {
        	event.setMaxScrollX(getScrollRangeX());
        	event.setMaxScrollY(getScrollRangeY());
        }
    }

    private int getScrollRangeX() {
        int scrollRange = 0;
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            scrollRange = Math.max(0,
                    child.getWidth() - (getWidth() - getPaddingRight() - getPaddingLeft()));
        }
        return scrollRange;
    }

    private int getScrollRangeY() {
        int scrollRange = 0;
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            scrollRange = Math.max(0,
                    child.getHeight() - (getHeight() - getPaddingBottom() - getPaddingTop()));
        }
        return scrollRange;
    }

    /**
     * <p>
     * Finds the next focusable component that fits in the specified bounds.
     * </p>
     *
     * @param topFocus look for a candidate is the one at the top of the bounds
     *                 if topFocus is true, or at the bottom of the bounds if topFocus is
     *                 false
     * @param top      the top offset of the bounds in which a focusable must be
     *                 found
     * @param bottom   the bottom offset of the bounds in which a focusable must
     *                 be found
     * @return the next focusable component in the bounds or null if none can
     *         be found
     */
    private View findFocusableViewInBounds(boolean topFocus, int top,
            int bottom, boolean leftFocus, int left, int right) {
        List<View> focusables = getFocusables(View.FOCUS_FORWARD);
        View focusCandidate = null;

        /*
         * A fully contained focusable is one where its top is below the bound's
         * top, and its bottom is above the bound's bottom. A partially
         * contained focusable is one where some part of it is within the
         * bounds, but it also has some part that is not within bounds. A fully
         * contained focusable is preferred to a partially contained focusable.
         */
        boolean foundFullyContainedFocusable = false;

        int count = focusables.size();
        for (int i = 0; i < count; i++) {
            View view = focusables.get(i);
            int viewTop = view.getTop();
            int viewBottom = view.getBottom();
            int viewLeft = view.getLeft();
            int viewRight = view.getRight();

            if (top < viewBottom && viewTop < bottom && left < viewRight
                    && viewLeft < right) {
                /*
                 * the focusable is in the target area, it is a candidate for
                 * focusing
                 */
                final boolean viewIsFullyContained = (top < viewTop)
                        && (viewBottom < bottom) && (left < viewLeft)
                        && (viewRight < right);
                if (focusCandidate == null) {
                    /* No candidate, take this one */
                    focusCandidate = view;
                    foundFullyContainedFocusable = viewIsFullyContained;
                } else {
                    final boolean viewIsCloserToVerticalBoundary = (topFocus && viewTop < focusCandidate
                            .getTop())
                            || (!topFocus && viewBottom > focusCandidate
                                    .getBottom());
                    final boolean viewIsCloserToHorizontalBoundary = (leftFocus && viewLeft < focusCandidate
                            .getLeft())
                            || (!leftFocus && viewRight > focusCandidate
                                    .getRight());
                    if (foundFullyContainedFocusable) {
                        if (viewIsFullyContained
                                && viewIsCloserToVerticalBoundary
                                && viewIsCloserToHorizontalBoundary) {
                            /*
                             * We're dealing with only fully contained views, so
                             * it has to be closer to the boundary to beat our
                             * candidate
                             */
                            focusCandidate = view;
                        }
                    } else {
                        if (viewIsFullyContained) {
                            /*
                             * Any fully contained view beats a partially
                             * contained view
                             */
                            focusCandidate = view;
                            foundFullyContainedFocusable = true;
                        } else if (viewIsCloserToVerticalBoundary
                                && viewIsCloserToHorizontalBoundary) {
                            /*
                             * Partially contained view beats another partially
                             * contained view if it's closer
                             */
                            focusCandidate = view;
                        }
                    }
                }
            }
        }
        return focusCandidate;
    }

    /**
     * <p>Handles scrolling in response to a "page up/down" shortcut press. This
     * method will scroll the view by one page up or down and give the focus
     * to the topmost/bottommost component in the new visible area. If no
     * component is a good candidate for focus, this scrollview reclaims the
     * focus.</p>
     *
     * @param direction the scroll direction: {@link android.view.View#FOCUS_UP}
     *                  to go one page up or
     *                  {@link android.view.View#FOCUS_DOWN} to go one page down
     * @return true if the key event is consumed by this method, false otherwise
     */
    public boolean pageScroll(int direction, boolean horizontal) {
        if (!horizontal) {
            boolean down = direction == View.FOCUS_DOWN;
            int height = getHeight();

            if (down) {
                mTempRect.top = getScrollY() + height;
                int count = getChildCount();
                if (count > 0) {
                    View view = getChildAt(count - 1);
                    if (mTempRect.top + height > view.getBottom()) {
                        mTempRect.top = view.getBottom() - height;
                    }
                }
            } else {
                mTempRect.top = getScrollY() - height;
                if (mTempRect.top < 0) {
                    mTempRect.top = 0;
                }
            }
            mTempRect.bottom = mTempRect.top + height;
            return scrollAndFocus(direction, mTempRect.top, mTempRect.bottom,
                    0, 0, 0);
        }
        else {
            boolean right = direction == View.FOCUS_DOWN;
            int width = getWidth();

            if (right) {
                mTempRect.left = getScrollX() + width;
                int count = getChildCount();
                if (count > 0) {
                    View view = getChildAt(count - 1);
                    if (mTempRect.left + width > view.getRight()) {
                        mTempRect.left = view.getRight() - width;
                    }
                }
            } else {
                mTempRect.left = getScrollX() - width;
                if (mTempRect.left < 0) {
                    mTempRect.left = 0;
                }
            }
            mTempRect.right = mTempRect.left + width;
            return scrollAndFocus(0, 0, 0, direction, mTempRect.left,
                    mTempRect.right);
        }
    }

    /**
     * <p>Handles scrolling in response to a "home/end" shortcut press. This
     * method will scroll the view to the top or bottom and give the focus
     * to the topmost/bottommost component in the new visible area. If no
     * component is a good candidate for focus, this scrollview reclaims the
     * focus.</p>
     *
     * @param direction the scroll direction: {@link android.view.View#FOCUS_UP}
     *                  to go the top of the view or
     *                  {@link android.view.View#FOCUS_DOWN} to go the bottom
     * @return true if the key event is consumed by this method, false otherwise
     */
    public boolean fullScroll(int direction, boolean horizontal) {
        if (!horizontal) {
            boolean down = direction == View.FOCUS_DOWN;
            int height = getHeight();
            mTempRect.top = 0;
            mTempRect.bottom = height;
            if (down) {
                int count = getChildCount();
                if (count > 0) {
                    View view = getChildAt(count - 1);
                    mTempRect.bottom = view.getBottom();
                    mTempRect.top = mTempRect.bottom - height;
                }
            }
            return scrollAndFocus(direction, mTempRect.top, mTempRect.bottom,
                    0, 0, 0);
        } else {
            boolean right = direction == View.FOCUS_DOWN;
            int width = getWidth();
            mTempRect.left = 0;
            mTempRect.right = width;
            if (right) {
                int count = getChildCount();
                if (count > 0) {
                    View view = getChildAt(count - 1);
                    mTempRect.right = view.getBottom();
                    mTempRect.left = mTempRect.right - width;
                }
            }
            return scrollAndFocus(0, 0, 0, direction, mTempRect.top,
                    mTempRect.bottom);
        }
    }

    /**
     * <p>Scrolls the view to make the area defined by <code>top</code> and
     * <code>bottom</code> visible. This method attempts to give the focus
     * to a component visible in this area. If no component can be focused in
     * the new visible area, the focus is reclaimed by this ScrollView.</p>
     *
     * @param direction the scroll direction: {@link android.view.View#FOCUS_UP}
     *                  to go upward, {@link android.view.View#FOCUS_DOWN} to downward
     * @param top       the top offset of the new area to be made visible
     * @param bottom    the bottom offset of the new area to be made visible
     * @return true if the key event is consumed by this method, false otherwise
     */
    private boolean scrollAndFocus(int directionY, int top, int bottom,
            int directionX, int left, int right) {
        boolean handled = true;
        int height = getHeight();
        int containerTop = getScrollY();
        int containerBottom = containerTop + height;
        boolean up = directionY == View.FOCUS_UP;
        int width = getWidth();
        int containerLeft = getScrollX();
        int containerRight = containerLeft + width;
        boolean leftwards = directionX == View.FOCUS_UP;
        View newFocused = findFocusableViewInBounds(up, top, bottom, leftwards,
                left, right);
        if (newFocused == null) {
            newFocused = this;
        }
        if ((top >= containerTop && bottom <= containerBottom)
                || (left >= containerLeft && right <= containerRight)) {
            handled = false;
        } else {
            int deltaY = up ? (top - containerTop) : (bottom - containerBottom);
            int deltaX = leftwards ? (left - containerLeft)
                    : (right - containerRight);
            doScroll(deltaX, deltaY);
        }
        if (newFocused != findFocus() && newFocused.requestFocus(directionY)) {
            mTwoDScrollViewMovedFocus = true;
            mTwoDScrollViewMovedFocus = false;
        }
        return handled;
    }

    /**
     * Handle scrolling in response to an up or down arrow click.
     *
     * @param direction The direction corresponding to the arrow key that was
     *                  pressed
     * @return True if we consumed the event, false otherwise
     */
    public boolean arrowScroll(int direction, boolean horizontal) {
        View currentFocused = findFocus();
        if (currentFocused == this)
            currentFocused = null;
        View nextFocused = FocusFinder.getInstance().findNextFocus(this,
                currentFocused, direction);
        final int maxJump = horizontal ? getMaxScrollAmountHorizontal()
                : getMaxScrollAmountVertical();

        if (!horizontal) {
//          if (nextFocused != null) {
//              nextFocused.getDrawingRect(mTempRect);
//              offsetDescendantRectToMyCoords(nextFocused, mTempRect);
//              int scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
//              doScroll(0, scrollDelta);
//              nextFocused.requestFocus(direction);
//          } else {
//              // no new focus
                int scrollDelta = maxJump;
                if (direction == View.FOCUS_UP && getScrollY() < scrollDelta) {
                    scrollDelta = getScrollY();
                } else if (direction == View.FOCUS_DOWN) {
                    if (getChildCount() > 0) {
                        int daBottom = getChildAt(0).getBottom();
                        int screenBottom = getScrollY() + getHeight();
                        if (daBottom - screenBottom < maxJump) {
                            scrollDelta = daBottom - screenBottom;
                        }
                    }
                }
                if (scrollDelta == 0) {
                    return false;
                }
                doScroll(0, direction == View.FOCUS_DOWN ? scrollDelta
                        : -scrollDelta);
//          }
        } else {
//          if (nextFocused != null) {
//              nextFocused.getDrawingRect(mTempRect);
//              offsetDescendantRectToMyCoords(nextFocused, mTempRect);
//              int scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
//              doScroll(scrollDelta, 0);
//              nextFocused.requestFocus(direction);
//          } else {
                // no new focus
                int scrollDelta = maxJump;
                if (direction == View.FOCUS_UP && getScrollY() < scrollDelta) {
                    scrollDelta = getScrollY();
                } else if (direction == View.FOCUS_DOWN) {
                    if (getChildCount() > 0) {
                        int daBottom = getChildAt(0).getBottom();
                        int screenBottom = getScrollY() + getHeight();
                        if (daBottom - screenBottom < maxJump) {
                            scrollDelta = daBottom - screenBottom;
                        }
                    }
                }
                if (scrollDelta == 0) {
                    return false;
                }
                doScroll(direction == View.FOCUS_DOWN ? scrollDelta
                        : -scrollDelta, 0);
//          }
        }
        return true;
    }

    /**
     * @return whether the descendant of this scroll view is scrolled off
     *  screen.
     */
    private boolean isOffScreen(View descendant) {
        return !isWithinDeltaOfScreen(descendant, 0, getWidth(), 0, getHeight());
    }

    /**
     * @return whether the descendant of this scroll view is within delta
     *  pixels of being on the screen.
     */
    private boolean isWithinDeltaOfScreen(View descendant, int deltaX, int width, int deltaY, int height) {
        descendant.getDrawingRect(mTempRect);
        offsetDescendantRectToMyCoords(descendant, mTempRect);

        return (mTempRect.right + deltaX) >= getScrollX()
                && (mTempRect.left - deltaX) <= (getScrollX() + width)
                && (mTempRect.bottom + deltaY) >= getScrollY()
                && (mTempRect.top - deltaY) <= (getScrollY() + height);
    }

    /**
	 * Smooth scroll by a Y delta
	 * 
	 * @param delta
	 *            the number of pixels to scroll by on the Y axis
	 */
	private void doScroll(int deltaX, int deltaY) {
		if (deltaX != 0 || deltaY != 0) {
			smoothScrollBy(deltaX, deltaY);
		}
	}

    /**
     * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
     *
     * @param dx the number of pixels to scroll by on the X axis
     * @param dy the number of pixels to scroll by on the Y axis
     */
    public final void smoothScrollBy(int dx, int dy) {
        if (getChildCount() == 0) {
            // Nothing to do.
            return;
        }
        long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
        if (duration > ANIMATED_SCROLL_GAP) {
            final int height = getHeight() - getPaddingBottom() - getPaddingTop();
            final int bottom = getChildAt(0).getHeight();
            final int width = getWidth() - getPaddingRight() - getPaddingLeft();
            final int right = getChildAt(0).getWidth();
            final int maxX = Math.max(0, right - width);
            final int maxY = Math.max(0, bottom - height);
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();
            dx = Math.max(0, Math.min(scrollX + dx, maxX)) - scrollX;
            dy = Math.max(0, Math.min(scrollY + dy, maxY)) - scrollY;

            mScroller.startScroll(getScrollX(), scrollY, dx, dy);
            postInvalidateOnAnimation();
        } else {
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
//                if (mFlingStrictSpan != null) {
//                    mFlingStrictSpan.finish();
//                    mFlingStrictSpan = null;
//                }
            }
            scrollBy(dx, dy);
        }
        mLastScroll = AnimationUtils.currentAnimationTimeMillis();
    }

    /**
     * Like {@link #scrollTo}, but scroll smoothly instead of immediately.
     *
     * @param x the position where to scroll on the X axis
     * @param y the position where to scroll on the Y axis
     */
    public final void smoothScrollTo(int x, int y) {
        smoothScrollBy(x - getScrollX(), y - getScrollY());
    }

    /**
     * <p>The scroll range of a scroll view is the overall height of all of its
     * children.</p>
     */
    @Override
    protected int computeHorizontalScrollRange() {
        final int count = getChildCount();
        final int contentWidth = getWidth() - getPaddingRight() - getPaddingLeft();
        if (count == 0) {
            return contentWidth;
        }

        int scrollRange = getChildAt(0).getRight();
        final int scrollX = getScrollX();
        final int overscrollRight = Math.max(0, scrollRange - contentWidth);
        if (scrollX < 0) {
            scrollRange -= scrollX;
        } else if (scrollX > overscrollRight) {
            scrollRange += scrollX - overscrollRight;
        }

        return scrollRange;
    }

    @Override
    protected int computeVerticalScrollRange() {
        final int count = getChildCount();
        final int contentHeight = getHeight() - getPaddingBottom() - getPaddingTop();
        if (count == 0) {
            return contentHeight;
        }

        int scrollRange = getChildAt(0).getBottom();
        final int scrollY = getScrollY();
        final int overscrollBottom = Math.max(0, scrollRange - contentHeight);
        if (scrollY < 0) {
            scrollRange -= scrollY;
        } else if (scrollY > overscrollBottom) {
            scrollRange += scrollY - overscrollBottom;
        }

        return scrollRange;
    }

    @Override
    protected int computeHorizontalScrollOffset() {
        return Math.max(super.computeHorizontalScrollOffset(), 0);
    }

    @Override
    protected int computeVerticalScrollOffset() {
        return Math.max(0, super.computeVerticalScrollOffset());
    }

    @Override
    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
        ViewGroup.LayoutParams lp = child.getLayoutParams();

        int childWidthMeasureSpec;
        int childHeightMeasureSpec;

        childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec, getPaddingLeft()
                + getPaddingRight(), lp.width);

        childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
            int parentHeightMeasureSpec, int heightUsed) {
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();

        final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin
                        + widthUsed, lp.width);
        final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
                lp.topMargin + lp.bottomMargin, MeasureSpec.UNSPECIFIED);

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }
    
    @Override
	public void computeScroll() {
		if (mScroller.computeScrollOffset()) {
			// This is called at drawing time by ViewGroup. We don't want to
			// re-show the scrollbars at this point, which scrollTo will do,
			// so we replicate most of scrollTo here.
			//
			// It's a little odd to call onScrollChanged from inside the
			// drawing.
			//
			// It is, except when you remember that computeScroll() is used to
			// animate scrolling. So unless we want to defer the
			// onScrollChanged()
			// until the end of the animated scrolling, we don't really have a
			// choice here.
			//
			// I agree. The alternative, which I think would be worse, is to
			// post
			// something and tell the subclasses later. This is bad because
			// there
			// will be a window where mScrollX/Y is different from what the app
			// thinks it is.
			//
			int oldX = getScrollX();
			int oldY = getScrollY();
			int x = mScroller.getCurrX();
			int y = mScroller.getCurrY();
			if (oldX != x || oldY != y) { 
				final int rangeX = getScrollRangeX();
                final int rangeY = getScrollRangeY();
				final int overscrollMode = getOverScrollMode();
				final boolean forceOverscroll = overscrollMode == OVER_SCROLL_ALWAYS;
                final boolean canOverscroll = ICE_CREAM_OR_GREATER && (forceOverscroll || overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS);
                overScrollBy(x - oldX, y - oldY, oldX, oldY, rangeX, rangeY,
                		mOverflingDistance, mOverflingDistance, false);
				onScrollChanged(getScrollX(), getScrollY(), oldX, oldY);
				if (canOverscroll) {
					if (rangeX > 0 || forceOverscroll) {
	                    if (x < 0 && oldX >= 0) {
	                        mEdgeGlowLeft.onAbsorb((int) mScroller.getCurrVelocity());
	                    } else if (x > rangeX && oldX <= rangeX) {
	                        mEdgeGlowRight.onAbsorb((int) mScroller.getCurrVelocity());
	                    }
					}

					if (rangeY > 0 || forceOverscroll) {
	                    if (y < 0 && oldY >= 0) {
	                        mEdgeGlowTop.onAbsorb((int) mScroller.getCurrVelocity());
	                    } else if (y > rangeY && oldY <= rangeY) {
	                        mEdgeGlowBottom.onAbsorb((int) mScroller.getCurrVelocity());
	                    }
					}
				}
			}
			// Keep on drawing until the animation has finished.
			postInvalidate();
		}
	}

    /**
     * Scrolls the view to the given child.
     *
     * @param child the View to scroll to
     */
    private void scrollToChild(View child) {
        child.getDrawingRect(mTempRect);

        /* Offset from child's local coordinates to ScrollView coordinates */
        offsetDescendantRectToMyCoords(child, mTempRect);

        int scrollXDelta = computeScrollXDeltaToGetChildRectOnScreen(mTempRect);
        int scrollYDelta = computeScrollYDeltaToGetChildRectOnScreen(mTempRect);

        if (scrollXDelta != 0 || scrollYDelta != 0 ) {
            scrollBy(scrollXDelta, scrollYDelta);
        }
    }

    /**
     * If rect is off screen, scroll just enough to get it (or at least the
     * first screen size chunk of it) on screen.
     *
     * @param rect      The rectangle.
     * @param immediate True to scroll immediately without animation
     * @return true if scrolling was performed
     */
    private boolean scrollToChildRect(Rect rect, boolean immediate) {
        final int deltaX = computeScrollXDeltaToGetChildRectOnScreen(rect);
        final int deltaY = computeScrollYDeltaToGetChildRectOnScreen(rect);
        final boolean scroll = (deltaX != 0 || deltaY != 0);
        if (scroll) {
            if (immediate) {
                scrollBy(deltaX, deltaY);
            } else {
                smoothScrollBy(deltaX, deltaY);
            }
        }
        return scroll;
    }

    /**
     * Compute the amount to scroll in the Y direction in order to get
     * a rectangle completely on the screen (or, if taller than the screen,
     * at least the first screen size chunk of it).
     *
     * @param rect The rect.
     * @return The scroll delta.
     */
    protected int computeScrollYDeltaToGetChildRectOnScreen(Rect rect) {
        if (getChildCount() == 0) return 0;

        int height = getHeight();
        int screenTop = getScrollY();
        int screenBottom = screenTop + height;

        int fadingEdge = getVerticalFadingEdgeLength();

        // leave room for top fading edge as long as rect isn't at very top
        if (rect.top > 0) {
            screenTop += fadingEdge;
        }

        // leave room for bottom fading edge as long as rect isn't at very bottom
        if (rect.bottom < getChildAt(0).getHeight()) {
            screenBottom -= fadingEdge;
        }

        int scrollYDelta = 0;

        if (rect.bottom > screenBottom && rect.top > screenTop) {
            // need to move down to get it in view: move down just enough so
            // that the entire rectangle is in view (or at least the first
            // screen size chunk).

            if (rect.height() > height) {
                // just enough to get screen size chunk on
                scrollYDelta += (rect.top - screenTop);
            } else {
                // get entire rect at bottom of screen
                scrollYDelta += (rect.bottom - screenBottom);
            }

            // make sure we aren't scrolling beyond the end of our content
            int bottom = getChildAt(0).getBottom();
            int distanceToBottom = bottom - screenBottom;
            scrollYDelta = Math.min(scrollYDelta, distanceToBottom);

        } else if (rect.top < screenTop && rect.bottom < screenBottom) {
            // need to move up to get it in view: move up just enough so that
            // entire rectangle is in view (or at least the first screen
            // size chunk of it).

            if (rect.height() > height) {
                // screen size chunk
                scrollYDelta -= (screenBottom - rect.bottom);
            } else {
                // entire rect at top
                scrollYDelta -= (screenTop - rect.top);
            }

            // make sure we aren't scrolling any further than the top our content
            scrollYDelta = Math.max(scrollYDelta, -getScrollY());
        }
        return scrollYDelta;
    }

    protected int computeScrollXDeltaToGetChildRectOnScreen(Rect rect) {
        if (getChildCount() == 0) return 0;

        int width = getWidth();
        int screenLeft = getScrollX();
        int screenRight = screenLeft + width;

        int fadingEdge = getHorizontalFadingEdgeLength();

        // leave room for top fading edge as long as rect isn't at very top
        if (rect.left > 0) {
            screenLeft += fadingEdge;
        }

        // leave room for bottom fading edge as long as rect isn't at very bottom
        if (rect.right < getChildAt(0).getWidth()) {
            screenRight -= fadingEdge;
        }

        int scrollXDelta = 0;

        if (rect.right > screenRight && rect.right > screenLeft) {
            // need to move down to get it in view: move down just enough so
            // that the entire rectangle is in view (or at least the first
            // screen size chunk).

            if (rect.width() > width) {
                // just enough to get screen size chunk on
                scrollXDelta += (rect.left - screenLeft);
            } else {
                // get entire rect at bottom of screen
                scrollXDelta += (rect.right - screenRight);
            }

            // make sure we aren't scrolling beyond the end of our content
            int right = getChildAt(0).getRight();
            int distanceToRight = right - screenRight;
            scrollXDelta = Math.min(scrollXDelta, distanceToRight);

        } else if (rect.left < screenLeft && rect.right < screenRight) {
            // need to move up to get it in view: move up just enough so that
            // entire rectangle is in view (or at least the first screen
            // size chunk of it).

            if (rect.width() > width) {
                // screen size chunk
                scrollXDelta -= (screenRight - rect.right);
            } else {
                // entire rect at top
                scrollXDelta -= (screenLeft - rect.left);
            }

            // make sure we aren't scrolling any further than the top our content
            scrollXDelta = Math.max(scrollXDelta, -getScrollX());
        }
        return scrollXDelta;
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        if (!mTwoDScrollViewMovedFocus) {
        if (!mIsLayoutDirty) {
            scrollToChild(focused);
        } else {
            // The child may not be laid out yet, we can't compute the scroll yet
            mChildToScrollTo = focused;
        }
    }
        super.requestChildFocus(child, focused);
    }


    /**
     * When looking for focus in children of a scroll view, need to be a little
     * more careful not to give focus to something that is scrolled off screen.
     *
     * This is more expensive than the default {@link android.view.ViewGroup}
     * implementation, otherwise this behavior might have been made the default.
     */
    @Override
    protected boolean onRequestFocusInDescendants(int direction,
            Rect previouslyFocusedRect) {

        // convert from forward / backward notation to up / down / left / right
        // (ugh).
        if (direction == View.FOCUS_FORWARD) {
            direction = View.FOCUS_DOWN;
        } else if (direction == View.FOCUS_BACKWARD) {
            direction = View.FOCUS_UP;
        }

        final View nextFocus = previouslyFocusedRect == null ?
                FocusFinder.getInstance().findNextFocus(this, null, direction) :
                FocusFinder.getInstance().findNextFocusFromRect(this,
                        previouslyFocusedRect, direction);

        if (nextFocus == null) {
            return false;
        }

        if (isOffScreen(nextFocus)) {
            return false;
        }

        return nextFocus.requestFocus(direction, previouslyFocusedRect);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle,
            boolean immediate) {
        // offset into coordinate space of this scroll view
        rectangle.offset(child.getLeft() - child.getScrollX(),
                child.getTop() - child.getScrollY());

        return scrollToChildRect(rectangle, immediate);
    }

    @Override
    public void requestLayout() {
        mIsLayoutDirty = true;
        super.requestLayout();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
//
//        if (mScrollStrictSpan != null) {
//            mScrollStrictSpan.finish();
//            mScrollStrictSpan = null;
//        }
//        if (mFlingStrictSpan != null) {
//            mFlingStrictSpan.finish();
//            mFlingStrictSpan = null;
//        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mIsLayoutDirty = false;
        // Give a child focus if it needs it
        if (mChildToScrollTo != null && isViewDescendantOf(mChildToScrollTo, this)) {
            scrollToChild(mChildToScrollTo);
        }
        mChildToScrollTo = null;

        // Calling this with the present values causes it to re-claim them
        handleScrollTo(getScrollX(), getScrollY());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        View currentFocused = findFocus();
        if (null == currentFocused || this == currentFocused)
            return;

        // If the currently-focused view was visible on the screen when the
        // screen was at the old height, then scroll the screen to make that
        // view visible with the new screen height.
        if (isWithinDeltaOfScreen(currentFocused, 0, oldw, 0, oldh)) {
            currentFocused.getDrawingRect(mTempRect);
            offsetDescendantRectToMyCoords(currentFocused, mTempRect);
            int scrollDeltaX = computeScrollXDeltaToGetChildRectOnScreen(mTempRect);
            int scrollDeltaY = computeScrollYDeltaToGetChildRectOnScreen(mTempRect);
            doScroll(scrollDeltaX, scrollDeltaY);
        }
    }

    /**
     * Return true if child is a descendant of parent, (or equal to the parent).
     */
    private static boolean isViewDescendantOf(View child, View parent) {
        if (child == parent) {
            return true;
        }

        final ViewParent theParent = child.getParent();
        return (theParent instanceof ViewGroup) && isViewDescendantOf((View) theParent, parent);
    }

    /**
     * Fling the scroll view
     *
     * @param velocityY The initial velocity in the Y direction. Positive
     *                  numbers mean that the finger/cursor is moving down the screen,
     *                  which means we want to scroll towards the top.
     */
    public void fling(int velocityX, int velocityY) {
        if (getChildCount() > 0) {
            int height = getHeight() - getPaddingBottom() - getPaddingTop();
            int bottom = getChildAt(0).getHeight();
            int width = getWidth() - getPaddingRight() - getPaddingLeft();
            int right = getChildAt(0).getWidth();

            mScroller.fling(getScrollX(), getScrollY(), velocityX, velocityY, 0,  Math.max(0, right - width), 0,
                    Math.max(0, bottom - height), width/2, height/2);

//            if (mFlingStrictSpan == null) {
//                mFlingStrictSpan = StrictMode.enterCriticalSpan("ScrollView-fling");
//            }

            postInvalidateOnAnimation();
        }
    }

    private void endDrag() {
        mIsBeingDragged = false;

        recycleVelocityTracker();

        if (ICE_CREAM_OR_GREATER && mEdgeGlowTop != null) {
            mEdgeGlowTop.onRelease();
            mEdgeGlowBottom.onRelease();
            mEdgeGlowLeft.onRelease();
            mEdgeGlowRight.onRelease();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>This version also clamps the scrolling to the bounds of our child.
     */
    public void handleScrollTo(int x, int y) {
        if (shouldClampScroll) {
	        // we rely on the fact the View.scrollBy calls scrollTo.
	        if (getChildCount() > 0) {
	            View child = getChildAt(0);
	            x = clamp(x, getWidth() - getPaddingRight() - getPaddingLeft(), child.getWidth());
	            y = clamp(y, getHeight() - getPaddingBottom() - getPaddingTop(), child.getHeight());
	        }
        }
        if (x != getScrollX() || y != getScrollY()) {
            super.scrollTo(x, y);
        }
    }

    @Override
    public void scrollTo(int x, int y) {
//      shouldClampScroll = false;
        handleScrollTo(x, y);
    }

    public void setShouldClamp(boolean clamp) {
        shouldClampScroll = clamp;
    }

    @Override
    public void setOverScrollMode(int mode) {
    	if (ICE_CREAM_OR_GREATER) {
	        if (mode != OVER_SCROLL_NEVER) {
	            if (mEdgeGlowTop == null) {
	                Context context = getContext();
	                mEdgeGlowTop = new EdgeEffect(context);
	                mEdgeGlowBottom = new EdgeEffect(context);
	                mEdgeGlowLeft = new EdgeEffect(context);
	                mEdgeGlowRight = new EdgeEffect(context);
	            }
	        } else {
	            mEdgeGlowTop = null;
	            mEdgeGlowBottom = null;
	            mEdgeGlowLeft = null;
	            mEdgeGlowRight = null;
	        }
    	}
        super.setOverScrollMode(mode);
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (ICE_CREAM_OR_GREATER && mEdgeGlowTop != null) {
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();
            final int width = getWidth() - getPaddingLeft() - getPaddingRight();
            final int height = getHeight() - getPaddingTop() - getPaddingBottom();
            if (!mEdgeGlowTop.isFinished()) {
                final int restoreCount = canvas.save();

                canvas.translate(getPaddingLeft() + scrollX, Math.min(0, scrollY));
                mEdgeGlowTop.setSize(width, getHeight());
                if (mEdgeGlowTop.draw(canvas)) {
                    postInvalidateOnAnimation();
                }
                canvas.restoreToCount(restoreCount);
            }
            if (!mEdgeGlowBottom.isFinished()) {
                final int restoreCount = canvas.save();
                canvas.translate(getPaddingLeft() + scrollX,
                        Math.max(getScrollRangeY(), scrollY) + height);
                canvas.rotate(180, width/2, 0);
                mEdgeGlowBottom.setSize(width, height);
                if (mEdgeGlowBottom.draw(canvas)) {
                    postInvalidateOnAnimation();
                }
                canvas.restoreToCount(restoreCount);
            }

            if (!mEdgeGlowLeft.isFinished()) {
                final int restoreCount = canvas.save();

                canvas.translate(Math.min(0, scrollX), getPaddingTop() + scrollY + height);
                canvas.rotate(-90, 0, 0);
                mEdgeGlowLeft.setSize(height, width);
                if (mEdgeGlowLeft.draw(canvas)) {
                    postInvalidateOnAnimation();
                }
                canvas.restoreToCount(restoreCount);
            }
            if (!mEdgeGlowRight.isFinished()) {
                final int restoreCount = canvas.save();

                canvas.translate(Math.max(getScrollRangeX(), scrollX) + getWidth(), getPaddingTop() + scrollY);
                canvas.rotate(90, 0, 0);
                mEdgeGlowRight.setSize(height, width);
                if (mEdgeGlowRight.draw(canvas)) {
                    postInvalidateOnAnimation();
                }
                canvas.restoreToCount(restoreCount);
            }
        }
    }

    private static int clamp(int n, int my, int child) {
        if (my >= child || n < 0) {
            /* my >= child is this case:
             *                    |--------------- me ---------------|
             *     |------ child ------|
             * or
             *     |--------------- me ---------------|
             *            |------ child ------|
             * or
             *     |--------------- me ---------------|
             *                                  |------ child ------|
             *
             * n < 0 is this case:
             *     |------ me ------|
             *                    |-------- child --------|
             *     |-- getScrollX() --|
             */
            return 0;
        }
        if ((my+n) > child) {
            /* this case:
             *                    |------ me ------|
             *     |------ child ------|
             *     |-- getScrollX() --|
             */
            return child-my;
        }
        return n;
    }
}
