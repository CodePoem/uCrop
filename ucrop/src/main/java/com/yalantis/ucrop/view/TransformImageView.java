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
     * 旋转、缩放接口回调
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

    /**
     * 设置图片变换监听器
     * @param transformImageListener 图片变换监听器
     */
    public void setTransformImageListener(TransformImageListener transformImageListener) {
        mTransformImageListener = transformImageListener;
    }

    /**
     * 复写setScaleType这个方法是是为了保证传入的scaleType一定是ScaleType.MATRI
     * @param scaleType 图片的测量类型scaleType
     */
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
     * 设置最大位图大小值。
     * 确保在setImageURI(Uri)方法或者其他设置图片源的方法前调用。
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

    /**
     * 复写setImageBitmap是为了包一层自己写的FastBitmapDrawable
     * @param bitmap 图片位图
     */
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
     * 这个方法需要一个Uri的参数（表示图片源），将图片编码成特定大小的位图。
     *
     * @param imageUri - image Uri
     * @throws Exception - can throw exception if having problems with decoding Uri or OOM.
     * 如果在对Uri编码过程中出现问题或者内存溢出会向外抛出异常。
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
     * 获取当前图片缩放比例 1.0f表示原图大小 2.0f表示原图放大2倍
     * @return - current image scale value.
     * [1.0f - for original image, 2.0f - for 200% scaled image, etc.]
     */
    public float getCurrentScale() {
        return getMatrixScale(mCurrentImageMatrix);
    }

    /**
     * 根据矩阵对象计算当前图片缩放比例
     * This method calculates scale value for given Matrix object.
     */
    public float getMatrixScale(@NonNull Matrix matrix) {
        return (float) Math.sqrt(Math.pow(getMatrixValue(matrix, Matrix.MSCALE_X), 2)
                + Math.pow(getMatrixValue(matrix, Matrix.MSKEW_Y), 2));
    }

    /**
     * 获取当前图片旋转角度
     * @return - current image rotation angle.
     */
    public float getCurrentAngle() {
        return getMatrixAngle(mCurrentImageMatrix);
    }

    /**
     *  根据矩阵对象计算当前图片旋转角度
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
     * 通过矩阵平移图片
     * @param deltaX - horizontal shift 水平位移
     * @param deltaY - vertical shift 竖直位移
     */
    public void postTranslate(float deltaX, float deltaY) {
        if (deltaX != 0 || deltaY != 0) {
            mCurrentImageMatrix.postTranslate(deltaX, deltaY);
            setImageMatrix(mCurrentImageMatrix);
        }
    }

    /**
     * This method scales current image.
     * 通过矩阵缩放图片
     * @param deltaScale - scale value 缩放比例
     * @param px         - scale center X 缩放中心x坐标值
     * @param py         - scale center Y 缩放中心y左边值
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
     * 通过矩阵旋转图片
     * @param deltaAngle - rotation angle 旋转角度
     * @param px         - rotation center X 旋转中心中心x坐标值
     * @param py         - rotation center Y 旋转中心y左边值
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

    /**
     * 初始化
     */
    protected void init() {
        // 设置图片测量类型为矩阵类型
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
     * 当图片铺平的时候一定会被调用，初始化图片四个角和中心坐标数组
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
     * 这个方法返回矩阵中对应给定的索引的值
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
     * 这个方法用于打印矩阵的MTRANS_X、MTRANS_Y、缩放比例、旋转角度。
     * 可用于调试。
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
     * 这个方法更新矩阵中存储的图片的四个角和中心点坐标
     * {@link #mCurrentImageCorners} and {@link #mCurrentImageCenter} arrays.
     * Those are used for several calculations.
     */
    private void updateCurrentImagePoints() {
        mCurrentImageMatrix.mapPoints(mCurrentImageCorners, mInitialImageCorners);
        mCurrentImageMatrix.mapPoints(mCurrentImageCenter, mInitialImageCenter);
    }

}
