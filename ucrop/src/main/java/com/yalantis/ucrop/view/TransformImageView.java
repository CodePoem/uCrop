package com.yalantis.ucrop.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import com.yalantis.ucrop.callback.BitmapLoadCallback;
import com.yalantis.ucrop.model.ExifInfo;
import com.yalantis.ucrop.util.BitmapLoadUtils;
import com.yalantis.ucrop.util.FastBitmapDrawable;
import com.yalantis.ucrop.util.RectUtils;

/**
 * Created by Oleksii Shliama (https://github.com/shliama).
 * <p/>
 * This class provides base logic to setup the image, transform it with matrix (move, scale, rotate),
 * and methods to get current matrix state.
 * 这个类提供了设置图片的基础逻辑，包括了使用矩阵变换（平移、缩放、旋转）和获取当前矩阵状态的方法
 */
public class TransformImageView extends ImageView {

    private static final String TAG = "TransformImageView";

    /**
     * 矩形四个角坐标表示个数（横坐标纵坐标各算一个） 2*4
     */
    private static final int RECT_CORNER_POINTS_COORDS = 8;
    /**
     * 矩形中心坐标表示个数（横坐标纵坐标各算一个）
     */
    private static final int RECT_CENTER_POINT_COORDS = 2;
    /**
     * 矩阵表示的值总数 3*3
     * 齐次坐标系，用高一维来表示点，可以做到将一个点的平移（加法）也表示进去
     */
    private static final int MATRIX_VALUES_COUNT = 9;
    /**
     * 当前图片四个角坐标数组
     */
    protected final float[] mCurrentImageCorners = new float[RECT_CORNER_POINTS_COORDS];
    /**
     * 当前图片中心坐标数组
     */
    protected final float[] mCurrentImageCenter = new float[RECT_CENTER_POINT_COORDS];
    /**
     * 矩阵值数组
     */
    private final float[] mMatrixValues = new float[MATRIX_VALUES_COUNT];
    /**
     * 当前图片矩阵
     */
    protected Matrix mCurrentImageMatrix = new Matrix();
    /**
     * 当前布局实际内容（除去padding）宽度，高度
     */
    protected int mThisWidth, mThisHeight;
    /**
     * 图片变换监听器
     */
    protected TransformImageListener mTransformImageListener;
    /**
     * 初始化图片角坐标数组
     */
    private float[] mInitialImageCorners;
    /**
     * 初始化图片中心坐标数组
     */
    private float[] mInitialImageCenter;
    /**
     * 位图编码状态 true已编码 false未编码
     */
    protected boolean mBitmapDecoded = false;
    /**
     * 位图铺平状态 true已铺平 false未铺平
     */
    protected boolean mBitmapLaidOut = false;
    /**
     * 位图最大大小
     */
    private int mMaxBitmapSize = 0;
    /**
     * 图片输入路径、输出路径
     */
    private String mImageInputPath, mImageOutputPath;
    /**
     * 图像信息
     */
    private ExifInfo mExifInfo;

    /**
     * Interface for rotation and scale change notifying.
     */
    public interface TransformImageListener {

        void onLoadComplete();

        void onLoadFailure(@NonNull Exception e);

        void onRotate(float currentAngle);

        void onScale(float currentScale);

    }

    public TransformImageView(Context context) {
        this(context, null);
    }

    public TransformImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TransformImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public void setTransformImageListener(TransformImageListener transformImageListener) {
        mTransformImageListener = transformImageListener;
    }

    @Override
    public void setScaleType(ScaleType scaleType) {
        if (scaleType == ScaleType.MATRIX) {
            super.setScaleType(scaleType);
        } else {
            Log.w(TAG, "Invalid ScaleType. Only ScaleType.MATRIX can be used");
        }
    }

    /**
     * Setter for {@link #mMaxBitmapSize} value.
     * Be sure to call it before {@link #setImageURI(Uri)} or other image setters.
     *
     * @param maxBitmapSize - max size for both width and height of bitmap that will be used in the view.
     */
    public void setMaxBitmapSize(int maxBitmapSize) {
        mMaxBitmapSize = maxBitmapSize;
    }

    public int getMaxBitmapSize() {
        if (mMaxBitmapSize <= 0) {
            mMaxBitmapSize = BitmapLoadUtils.calculateMaxBitmapSize(getContext());
        }
        return mMaxBitmapSize;
    }

    @Override
    public void setImageBitmap(final Bitmap bitmap) {
        setImageDrawable(new FastBitmapDrawable(bitmap));
    }

    public String getImageInputPath() {
        return mImageInputPath;
    }

    public String getImageOutputPath() {
        return mImageOutputPath;
    }

    public ExifInfo getExifInfo() {
        return mExifInfo;
    }

    /**
     * This method takes an Uri as a parameter, then calls method to decode it into Bitmap with specified size.
     *
     * @param imageUri - image Uri
     * @throws Exception - can throw exception if having problems with decoding Uri or OOM.
     */
    public void setImageUri(@NonNull Uri imageUri, @Nullable Uri outputUri) throws Exception {
        int maxBitmapSize = getMaxBitmapSize();

        BitmapLoadUtils.decodeBitmapInBackground(getContext(), imageUri, outputUri, maxBitmapSize, maxBitmapSize,
                new BitmapLoadCallback() {

                    @Override
                    public void onBitmapLoaded(@NonNull Bitmap bitmap, @NonNull ExifInfo exifInfo, @NonNull String imageInputPath, @Nullable String imageOutputPath) {
                        mImageInputPath = imageInputPath;
                        mImageOutputPath = imageOutputPath;
                        mExifInfo = exifInfo;

                        mBitmapDecoded = true;
                        setImageBitmap(bitmap);
                    }

                    @Override
                    public void onFailure(@NonNull Exception bitmapWorkerException) {
                        Log.e(TAG, "onFailure: setImageUri", bitmapWorkerException);
                        if (mTransformImageListener != null) {
                            mTransformImageListener.onLoadFailure(bitmapWorkerException);
                        }
                    }
                });
    }

    /**
     * @return - current image scale value.
     * [1.0f - for original image, 2.0f - for 200% scaled image, etc.]
     */
    public float getCurrentScale() {
        return getMatrixScale(mCurrentImageMatrix);
    }

    /**
     * This method calculates scale value for given Matrix object.
     */
    public float getMatrixScale(@NonNull Matrix matrix) {
        return (float) Math.sqrt(Math.pow(getMatrixValue(matrix, Matrix.MSCALE_X), 2)
                + Math.pow(getMatrixValue(matrix, Matrix.MSKEW_Y), 2));
    }

    /**
     * @return - current image rotation angle.
     */
    public float getCurrentAngle() {
        return getMatrixAngle(mCurrentImageMatrix);
    }

    /**
     * This method calculates rotation angle for given Matrix object.
     */
    public float getMatrixAngle(@NonNull Matrix matrix) {
        return (float) -(Math.atan2(getMatrixValue(matrix, Matrix.MSKEW_X),
                getMatrixValue(matrix, Matrix.MSCALE_X)) * (180 / Math.PI));
    }

    @Override
    public void setImageMatrix(Matrix matrix) {
        super.setImageMatrix(matrix);
        mCurrentImageMatrix.set(matrix);
        updateCurrentImagePoints();
    }

    @Nullable
    public Bitmap getViewBitmap() {
        if (getDrawable() == null || !(getDrawable() instanceof FastBitmapDrawable)) {
            return null;
        } else {
            return ((FastBitmapDrawable) getDrawable()).getBitmap();
        }
    }

    /**
     * This method translates current image.
     *
     * @param deltaX - horizontal shift
     * @param deltaY - vertical shift
     */
    public void postTranslate(float deltaX, float deltaY) {
        if (deltaX != 0 || deltaY != 0) {
            mCurrentImageMatrix.postTranslate(deltaX, deltaY);
            setImageMatrix(mCurrentImageMatrix);
        }
    }

    /**
     * This method scales current image.
     *
     * @param deltaScale - scale value
     * @param px         - scale center X
     * @param py         - scale center Y
     */
    public void postScale(float deltaScale, float px, float py) {
        if (deltaScale != 0) {
            mCurrentImageMatrix.postScale(deltaScale, deltaScale, px, py);
            setImageMatrix(mCurrentImageMatrix);
            if (mTransformImageListener != null) {
                mTransformImageListener.onScale(getMatrixScale(mCurrentImageMatrix));
            }
        }
    }

    /**
     * This method rotates current image.
     *
     * @param deltaAngle - rotation angle
     * @param px         - rotation center X
     * @param py         - rotation center Y
     */
    public void postRotate(float deltaAngle, float px, float py) {
        if (deltaAngle != 0) {
            mCurrentImageMatrix.postRotate(deltaAngle, px, py);
            setImageMatrix(mCurrentImageMatrix);
            if (mTransformImageListener != null) {
                mTransformImageListener.onRotate(getMatrixAngle(mCurrentImageMatrix));
            }
        }
    }

    protected void init() {
        setScaleType(ScaleType.MATRIX);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed || (mBitmapDecoded && !mBitmapLaidOut)) {

            left = getPaddingLeft();
            top = getPaddingTop();
            right = getWidth() - getPaddingRight();
            bottom = getHeight() - getPaddingBottom();
            mThisWidth = right - left;
            mThisHeight = bottom - top;

            onImageLaidOut();
        }
    }

    /**
     * When image is laid out {@link #mInitialImageCenter} and {@link #mInitialImageCenter}
     * must be set.
     */
    protected void onImageLaidOut() {
        final Drawable drawable = getDrawable();
        if (drawable == null) {
            return;
        }

        float w = drawable.getIntrinsicWidth();
        float h = drawable.getIntrinsicHeight();

        Log.d(TAG, String.format("Image size: [%d:%d]", (int) w, (int) h));

        RectF initialImageRect = new RectF(0, 0, w, h);
        mInitialImageCorners = RectUtils.getCornersFromRect(initialImageRect);
        mInitialImageCenter = RectUtils.getCenterFromRect(initialImageRect);

        mBitmapLaidOut = true;

        if (mTransformImageListener != null) {
            mTransformImageListener.onLoadComplete();
        }
    }

    /**
     * This method returns Matrix value for given index.
     *
     * @param matrix     - valid Matrix object
     * @param valueIndex - index of needed value. See {@link Matrix#MSCALE_X} and others.
     * @return - matrix value for index
     */
    protected float getMatrixValue(@NonNull Matrix matrix, @IntRange(from = 0, to = MATRIX_VALUES_COUNT) int valueIndex) {
        matrix.getValues(mMatrixValues);
        return mMatrixValues[valueIndex];
    }

    /**
     * This method logs given matrix X, Y, scale, and angle values.
     * Can be used for debug.
     */
    @SuppressWarnings("unused")
    protected void printMatrix(@NonNull String logPrefix, @NonNull Matrix matrix) {
        float x = getMatrixValue(matrix, Matrix.MTRANS_X);
        float y = getMatrixValue(matrix, Matrix.MTRANS_Y);
        float rScale = getMatrixScale(matrix);
        float rAngle = getMatrixAngle(matrix);
        Log.d(TAG, logPrefix + ": matrix: { x: " + x + ", y: " + y + ", scale: " + rScale + ", angle: " + rAngle + " }");
    }

    /**
     * This method updates current image corners and center points that are stored in
     * {@link #mCurrentImageCorners} and {@link #mCurrentImageCenter} arrays.
     * Those are used for several calculations.
     */
    private void updateCurrentImagePoints() {
        mCurrentImageMatrix.mapPoints(mCurrentImageCorners, mInitialImageCorners);
        mCurrentImageMatrix.mapPoints(mCurrentImageCenter, mInitialImageCenter);
    }

}
