package com.yalantis.ucrop.task;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.yalantis.ucrop.callback.BitmapCropCallback;
import com.yalantis.ucrop.model.CropParameters;
import com.yalantis.ucrop.model.ExifInfo;
import com.yalantis.ucrop.model.ImageState;
import com.yalantis.ucrop.util.FileUtils;
import com.yalantis.ucrop.util.ImageHeaderParser;

import java.io.File;
import java.io.IOException;

/**
 * Crops part of image that fills the crop bounds.
 * <p/>
 * First image is downscaled if max size was set and if resulting image is larger that max size.
 * Then image is rotated accordingly.
 * Finally new Bitmap object is created and saved to file.
 */
public class BitmapCropTask extends AsyncTask<Void, Void, Throwable> {

    private static final String TAG = "BitmapCropTask";

    /**
     * 加载C/C++ Library
     */
    static {
        System.loadLibrary("ucrop");
    }

    /**
     * 视图位图
     */
    private Bitmap mViewBitmap;

    /**
     * 裁剪矩形
     */
    private final RectF mCropRect;
    /**
     * 当前图片矩形
     */
    private final RectF mCurrentImageRect;

    /**
     * 当前缩放比例，当前旋转角度
     */
    private float mCurrentScale, mCurrentAngle;
    /**
     * 结果图片最大尺寸X，结果图片最大尺寸Y
     */
    private final int mMaxResultImageSizeX, mMaxResultImageSizeY;

    /**
     * 压缩格式
     */
    private final Bitmap.CompressFormat mCompressFormat;
    /**
     * 压缩质量
     */
    private final int mCompressQuality;
    /**
     * 图片输入路径字符串，图片输出路径字符串
     */
    private final String mImageInputPath, mImageOutputPath;
    /**
     * 图像信息
     */
    private final ExifInfo mExifInfo;
    /**
     * 位图裁剪回调
     */
    private final BitmapCropCallback mCropCallback;

    /**
     * 裁剪图片宽，高
     */
    private int mCroppedImageWidth, mCroppedImageHeight;
    /**
     * 裁剪偏移量X，Y
     */
    private int cropOffsetX, cropOffsetY;

    public BitmapCropTask(@Nullable Bitmap viewBitmap, @NonNull ImageState imageState, @NonNull CropParameters cropParameters,
                          @Nullable BitmapCropCallback cropCallback) {

        mViewBitmap = viewBitmap;
        mCropRect = imageState.getCropRect();
        mCurrentImageRect = imageState.getCurrentImageRect();

        mCurrentScale = imageState.getCurrentScale();
        mCurrentAngle = imageState.getCurrentAngle();
        mMaxResultImageSizeX = cropParameters.getMaxResultImageSizeX();
        mMaxResultImageSizeY = cropParameters.getMaxResultImageSizeY();

        mCompressFormat = cropParameters.getCompressFormat();
        mCompressQuality = cropParameters.getCompressQuality();

        mImageInputPath = cropParameters.getImageInputPath();
        mImageOutputPath = cropParameters.getImageOutputPath();
        mExifInfo = cropParameters.getExifInfo();

        mCropCallback = cropCallback;
    }

    @Override
    @Nullable
    protected Throwable doInBackground(Void... params) {
        if (mViewBitmap == null) {
            return new NullPointerException("ViewBitmap is null");
        } else if (mViewBitmap.isRecycled()) {
            return new NullPointerException("ViewBitmap is recycled");
        } else if (mCurrentImageRect.isEmpty()) {
            return new NullPointerException("CurrentImageRect is empty");
        }

        float resizeScale = resize();

        try {
            crop(resizeScale);
            mViewBitmap = null;
        } catch (Throwable throwable) {
            return throwable;
        }

        return null;
    }

    /**
     * 调整大小
     * @return 调整大小后的缩放比例
     */
    private float resize() {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mImageInputPath, options);

        // 是否交换了对边 即原来水平变竖直 原来竖直变水平
        boolean swapSides = mExifInfo.getExifDegrees() == 90 || mExifInfo.getExifDegrees() == 270;

        // 计算原图和当前视图位图的缩放比例（考虑交换了对边的情况）
        float scaleX = (swapSides ? options.outHeight : options.outWidth) / (float) mViewBitmap.getWidth();
        float scaleY = (swapSides ? options.outWidth : options.outHeight) / (float) mViewBitmap.getHeight();

        float resizeScale = Math.min(scaleX, scaleY);

        mCurrentScale /= resizeScale;

        // 如果设置了结果图片最大尺寸X或者结果图片最大尺寸Y的需要再做对应的判断
        resizeScale = 1;
        if (mMaxResultImageSizeX > 0 && mMaxResultImageSizeY > 0) {
            float cropWidth = mCropRect.width() / mCurrentScale;
            float cropHeight = mCropRect.height() / mCurrentScale;

            // 如果裁剪后的尺寸大于设置的图片最大尺寸 需要再做缩放
            if (cropWidth > mMaxResultImageSizeX || cropHeight > mMaxResultImageSizeY) {

                scaleX = mMaxResultImageSizeX / cropWidth;
                scaleY = mMaxResultImageSizeY / cropHeight;
                resizeScale = Math.min(scaleX, scaleY);

                mCurrentScale /= resizeScale;
            }
        }
        return resizeScale;
    }

    /**
     * 裁剪
     * @param resizeScale 调整大小后的缩放比例
     * @return 是否裁剪成功
     * @throws IOException 可能抛出IO异常
     */
    private boolean crop(float resizeScale) throws IOException {
        ExifInterface originalExif = new ExifInterface(mImageInputPath);

        cropOffsetX = Math.round((mCropRect.left - mCurrentImageRect.left) / mCurrentScale);
        cropOffsetY = Math.round((mCropRect.top - mCurrentImageRect.top) / mCurrentScale);
        mCroppedImageWidth = Math.round(mCropRect.width() / mCurrentScale);
        mCroppedImageHeight = Math.round(mCropRect.height() / mCurrentScale);

        boolean shouldCrop = shouldCrop(mCroppedImageWidth, mCroppedImageHeight);
        Log.i(TAG, "Should crop: " + shouldCrop);

        if (shouldCrop) {
            boolean cropped = cropCImg(mImageInputPath, mImageOutputPath,
                    cropOffsetX, cropOffsetY, mCroppedImageWidth, mCroppedImageHeight,
                    mCurrentAngle, resizeScale, mCompressFormat.ordinal(), mCompressQuality,
                    mExifInfo.getExifDegrees(), mExifInfo.getExifTranslation());
            if (cropped && mCompressFormat.equals(Bitmap.CompressFormat.JPEG)) {
                ImageHeaderParser.copyExif(originalExif, mCroppedImageWidth, mCroppedImageHeight, mImageOutputPath);
            }
            return cropped;
        } else {
            FileUtils.copyFile(mImageInputPath, mImageOutputPath);
            return false;
        }
    }

    /**
     * Check whether an image should be cropped at all or just file can be copied to the destination path.
     * For each 1000 pixels there is one pixel of error due to matrix calculations etc.
     * 检查是否一张图片是应该被裁剪还是只是需要将文件拷贝给目标输出路径。
     * 对于每1000像素允许有1像素的矩阵计算错误等。
     *
     * @param width  - crop area width 裁剪区域宽度
     * @param height - crop area height 裁剪区域高度
     * @return - true if image must be cropped, false - if original image fits requirements
     */
    private boolean shouldCrop(int width, int height) {
        int pixelError = 1;
        // 对于每1000像素允许有1像素的矩阵计算错误
        pixelError += Math.round(Math.max(width, height) / 1000f);
        return (mMaxResultImageSizeX > 0 && mMaxResultImageSizeY > 0)
                || Math.abs(mCropRect.left - mCurrentImageRect.left) > pixelError
                || Math.abs(mCropRect.top - mCurrentImageRect.top) > pixelError
                || Math.abs(mCropRect.bottom - mCurrentImageRect.bottom) > pixelError
                || Math.abs(mCropRect.right - mCurrentImageRect.right) > pixelError
                || mCurrentAngle != 0;
    }

    @SuppressWarnings("JniMissingFunction")
    native public static boolean
    cropCImg(String inputPath, String outputPath,
             int left, int top, int width, int height,
             float angle, float resizeScale,
             int format, int quality,
             int exifDegrees, int exifTranslation) throws IOException, OutOfMemoryError;

    @Override
    protected void onPostExecute(@Nullable Throwable t) {
        if (mCropCallback != null) {
            if (t == null) {
                Uri uri = Uri.fromFile(new File(mImageOutputPath));
                mCropCallback.onBitmapCropped(uri, cropOffsetX, cropOffsetY, mCroppedImageWidth, mCroppedImageHeight);
            } else {
                mCropCallback.onCropFailure(t);
            }
        }
    }

}
