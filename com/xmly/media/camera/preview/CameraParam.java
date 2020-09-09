package com.xmly.media.camera.preview;

import android.hardware.Camera;

/**
 * 相机参数 相机操作所需要的参数
 * Created by sunyc on 19-7-24.
 */

public class CameraParam {
    private static final String TAG = "CameraParam";
    //手机的方向 1 竖屏 0 横屏
    public int mWindowRotation = 1;
    //期望预览宽
    public int mExpectedWidth = 960;
    //期望预览高
    public int mExpectedHeight = 540;
    //期望帧率
    public int mExpectedFps = 15;
    //实际预览宽
    public int mPreviewWidth = 960;
    //实际预览高
    public int mPreviewHeight = 540;
    //录制视频宽
    public int mRecordWidth = mPreviewWidth;
    //录制视频高
    public int mRecordHeight = mPreviewHeight;
    //实际预览帧率
    public int mPreviewFps = 15;
    //期望打开的相机ID
    public int mExpectedCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
    //实际打开的相机ID
    public int mPreviewCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
    //相机输出图像的旋转角度
    public int mOrientation = 0;
    //相机输出图像的水平翻转
    public boolean mFlipHorizontal = false;
    //相机输出图像的垂直翻转
    public boolean mFlipVertical = false;
    //相机对象
    public Camera mCameraInstance = null;

    private void reset() {
        mWindowRotation = 1;
        mExpectedWidth = 960;
        mExpectedHeight = 540;
        mExpectedFps = 15;
        mPreviewWidth = 960;
        mPreviewHeight = 540;
        mRecordWidth = mPreviewWidth;
        mRecordHeight = mPreviewHeight;
        mPreviewFps = 15;
        mExpectedCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        mPreviewCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        mOrientation = 0;
        mFlipHorizontal = false;
        mFlipVertical = false;
        mCameraInstance = null;
    }

    public CameraParam() {
        reset();
    }

    public void release() {
        mCameraInstance = null;
    }
}
