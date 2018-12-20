package com.yalantis.ucrop.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.yalantis.ucrop.callback.BitmapLoadCallback;
import com.yalantis.ucrop.task.BitmapLoadTask;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Oleksii Shliama (https://github.com/shliama).
 * 位图加载工具
 */
public class BitmapLoadUtils {

    private static final String TAG = "BitmapLoadUtils";

    /**
     * 在后台对位图进行编码
     * @param context 上下文
     * @param uri 图片源Uri
     * @param outputUri 图片输出Uri
     * @param requiredWidth 要求的宽度
     * @param requiredHeight 要求的高度
     * @param loadCallback 加载回调
     */
    public static void decodeBitmapInBackground(@NonNull Context context,
                                                @NonNull Uri uri, @Nullable Uri outputUri,
                                                int requiredWidth, int requiredHeight,
                                                BitmapLoadCallback loadCallback) {
        // AsyncTask异步执行
        new BitmapLoadTask(context, uri, outputUri, requiredWidth, requiredHeight, loadCallback).execute();
    }

    /**
     * 将矩阵转换为位图
     * @param bitmap 接受转换后位图
     * @param transformMatrix 转换矩阵
     * @return 转换后位图
     */
    public static Bitmap transformBitmap(@NonNull Bitmap bitmap, @NonNull Matrix transformMatrix) {
        try {
            Bitmap converted = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), transformMatrix, true);
            if (!bitmap.sameAs(converted)) {
                bitmap = converted;
            }
        } catch (OutOfMemoryError error) {
            Log.e(TAG, "transformBitmap: ", error);
        }
        return bitmap;
    }

    /**
     * 计算对图像像素的缩放比例 假设值为2，表示decode后的图像的像素为原图像的1/2。
     * @param options 包含图像信息的BitmapFactory.Options
     * @param reqWidth 要求的宽度
     * @param reqHeight 要求的高度
     * @return 图像像素的缩放比例
     */
    public static int calculateInSampleSize(@NonNull BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width lower or equal to the requested height and width.
            // 计算出对图像像素的缩放比例始终是2的次方并且保证了缩放后的宽高要小于等于要求的宽高
            while ((height / inSampleSize) > reqHeight || (width / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * 获取图像方向
     * @param context 上下文
     * @param imageUri 图片源Uri
     * @return 图像方向
     */
    public static int getExifOrientation(@NonNull Context context, @NonNull Uri imageUri) {
        int orientation = ExifInterface.ORIENTATION_UNDEFINED;
        try {
            InputStream stream = context.getContentResolver().openInputStream(imageUri);
            if (stream == null) {
                return orientation;
            }
            orientation = new ImageHeaderParser(stream).getOrientation();
            close(stream);
        } catch (IOException e) {
            Log.e(TAG, "getExifOrientation: " + imageUri.toString(), e);
        }
        return orientation;
    }

    /**
     * 获取图像旋转角度
     * @param exifOrientation 图像方向
     * @return 图像旋转角度
     */
    public static int exifToDegrees(int exifOrientation) {
        int rotation;
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
            case ExifInterface.ORIENTATION_TRANSPOSE:
                rotation = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                rotation = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
            case ExifInterface.ORIENTATION_TRANSVERSE:
                rotation = 270;
                break;
            default:
                rotation = 0;
        }
        return rotation;
    }

    /**
     * 获取图像旋转变换值
     * @param exifOrientation 图像方向
     * @return 图像旋转变换值
     */
    public static int exifToTranslation(int exifOrientation) {
        int translation;
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
            case ExifInterface.ORIENTATION_TRANSPOSE:
            case ExifInterface.ORIENTATION_TRANSVERSE:
                translation = -1;
                break;
            default:
                translation = 1;
        }
        return translation;
    }

    /**
     * This method calculates maximum size of both width and height of bitmap.
     * It is twice the device screen diagonal for default implementation (extra quality to zoom image).
     * Size cannot exceed max texture size.
     * 这个方法计算位图最大宽高尺寸，默认为屏幕对角线的平方，尺寸不能超过最大纹理尺寸。
     *
     * @return - max bitmap size in pixels.
     */
    @SuppressWarnings({"SuspiciousNameCombination", "deprecation"})
    public static int calculateMaxBitmapSize(@NonNull Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display;
        int width, height;
        Point size = new Point();

        if (wm != null) {
            display = wm.getDefaultDisplay();
            display.getSize(size);
        }

        width = size.x;
        height = size.y;

        // Twice the device screen diagonal as default
        // 默认屏幕对角线的平方
        int maxBitmapSize = (int) Math.sqrt(Math.pow(width, 2) + Math.pow(height, 2));

        // Check for max texture size via Canvas
        // 不能超过Canvas最大纹理尺寸
        Canvas canvas = new Canvas();
        final int maxCanvasSize = Math.min(canvas.getMaximumBitmapWidth(), canvas.getMaximumBitmapHeight());
        if (maxCanvasSize > 0) {
            maxBitmapSize = Math.min(maxBitmapSize, maxCanvasSize);
        }

        // Check for max texture size via GL
        // 不能超过GL最大纹理尺寸
        final int maxTextureSize = EglUtils.getMaxTextureSize();
        if (maxTextureSize > 0) {
            maxBitmapSize = Math.min(maxBitmapSize, maxTextureSize);
        }

        Log.d(TAG, "maxBitmapSize: " + maxBitmapSize);
        return maxBitmapSize;
    }

    @SuppressWarnings("ConstantConditions")
    public static void close(@Nullable Closeable c) {
        if (c != null && c instanceof Closeable) { // java.lang.IncompatibleClassChangeError: interface not implemented
            try {
                c.close();
            } catch (IOException e) {
                // silence
            }
        }
    }

}