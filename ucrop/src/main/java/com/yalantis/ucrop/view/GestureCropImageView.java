package com.yalantis.ucrop.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.yalantis.ucrop.util.RotationGestureDetector;

/**
 * Created by Oleksii Shliama (https://github.com/shliama).
 */
public class GestureCropImageView extends CropImageView {

    /**
     * 双击放大动画持续时间
     */
    private static final int DOUBLE_TAP_ZOOM_DURATION = 200;

    /**
     * 缩放手势探测器
     */
    private ScaleGestureDetector mScaleDetector;
    /**
     * 旋转手势探测器
     */
    private RotationGestureDetector mRotateDetector;
    /**
     * 手势探测器
     */
    private GestureDetector mGestureDetector;

    /**
     * 双指触摸点直线中点坐标X,Y
     */
    private float mMidPntX, mMidPntY;

    /**
     * 是否可旋转，是否可缩放
     */
    private boolean mIsRotateEnabled = true, mIsScaleEnabled = true;
    /**
     * 双击放大步数（从最小放大倍率到最大放大倍率）
     */
    private int mDoubleTapScaleSteps = 5;

    public GestureCropImageView(Context context) {
        super(context);
    }

    public GestureCropImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GestureCropImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setScaleEnabled(boolean scaleEnabled) {
        mIsScaleEnabled = scaleEnabled;
    }

    public boolean isScaleEnabled() {
        return mIsScaleEnabled;
    }

    public void setRotateEnabled(boolean rotateEnabled) {
        mIsRotateEnabled = rotateEnabled;
    }

    public boolean isRotateEnabled() {
        return mIsRotateEnabled;
    }

    public void setDoubleTapScaleSteps(int doubleTapScaleSteps) {
        mDoubleTapScaleSteps = doubleTapScaleSteps;
    }

    public int getDoubleTapScaleSteps() {
        return mDoubleTapScaleSteps;
    }

    /**
     * If it's ACTION_DOWN event - user touches the screen and all current animation must be canceled.
     * If it's ACTION_UP event - user removed all fingers from the screen and current image position must be corrected.
     * If there are more than 2 fingers - update focal point coordinates.
     * Pass the event to the gesture detectors if those are enabled.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
            cancelAllAnimations();
        }

        if (event.getPointerCount() > 1) {
            mMidPntX = (event.getX(0) + event.getX(1)) / 2;
            mMidPntY = (event.getY(0) + event.getY(1)) / 2;
        }

        mGestureDetector.onTouchEvent(event);

        if (mIsScaleEnabled) {
            mScaleDetector.onTouchEvent(event);
        }

        if (mIsRotateEnabled) {
            mRotateDetector.onTouchEvent(event);
        }

        if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
            setImageToWrapCropBounds();
        }
        return true;
    }

    @Override
    protected void init() {
        super.init();
        setupGestureListeners();
    }

    /**
     * This method calculates target scale value for double tap gesture.
     * User is able to zoom the image from min scale value
     * to the max scale value with {@link #mDoubleTapScaleSteps} double taps.
     */
    protected float getDoubleTapTargetScale() {
        // 双击放大步数（从最小放大倍率到最大放大倍率）
        return getCurrentScale() * (float) Math.pow(getMaxScale() / getMinScale(), 1.0f / mDoubleTapScaleSteps);
    }

    /**
     * 设置手势监听器
     */
    private void setupGestureListeners() {
        mGestureDetector = new GestureDetector(getContext(), new GestureListener(), null, true);
        mScaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        mRotateDetector = new RotationGestureDetector(new RotateListener());
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            postScale(detector.getScaleFactor(), mMidPntX, mMidPntY);
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            zoomImageToPosition(getDoubleTapTargetScale(), e.getX(), e.getY(), DOUBLE_TAP_ZOOM_DURATION);
            return super.onDoubleTap(e);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            postTranslate(-distanceX, -distanceY);
            return true;
        }

    }

    private class RotateListener extends RotationGestureDetector.SimpleOnRotationGestureListener {

        @Override
        public boolean onRotation(RotationGestureDetector rotationDetector) {
            postRotate(rotationDetector.getAngle(), mMidPntX, mMidPntY);
            return true;
        }

    }

}
