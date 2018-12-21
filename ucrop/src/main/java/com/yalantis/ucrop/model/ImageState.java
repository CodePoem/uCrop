package com.yalantis.ucrop.model;

import android.graphics.RectF;

/**
 * 图片状态
 * Created by Oleksii Shliama [https://github.com/shliama] on 6/21/16.
 */
public class ImageState {

    /**
     * 裁剪矩形
     */
    private RectF mCropRect;
    /**
     * 当前图片矩形
     */
    private RectF mCurrentImageRect;
    /**
     * 当前缩放比例和当前旋转角度
     */
    private float mCurrentScale, mCurrentAngle;

    public ImageState(RectF cropRect, RectF currentImageRect, float currentScale, float currentAngle) {
        mCropRect = cropRect;
        mCurrentImageRect = currentImageRect;
        mCurrentScale = currentScale;
        mCurrentAngle = currentAngle;
    }

    public RectF getCropRect() {
        return mCropRect;
    }

    public RectF getCurrentImageRect() {
        return mCurrentImageRect;
    }

    public float getCurrentScale() {
        return mCurrentScale;
    }

    public float getCurrentAngle() {
        return mCurrentAngle;
    }
}
