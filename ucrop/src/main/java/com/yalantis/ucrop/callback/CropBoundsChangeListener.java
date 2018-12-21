package com.yalantis.ucrop.callback;

/**
 * Interface for crop bound change notifying.
 * 裁剪边框改变回调接口
 */
public interface CropBoundsChangeListener {

    /**
     * 裁剪比例变化回调
     * @param cropRatio 裁剪比例
     */
    void onCropAspectRatioChanged(float cropRatio);

}