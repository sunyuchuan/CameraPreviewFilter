package com.xmly.media.camera.preview;

import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.util.Log;

import com.xmly.media.camera.preview.listener.onCameraEngineListener;
import com.xmly.media.camera.preview.listener.onCameraSetupCallback;
import com.xmly.media.camera.view.utils.CameraHelper;

import java.io.IOException;
import java.util.List;

/**
 * 相机引擎 负责相机的打开\关闭\切换等。
 * Created by sunyc on 19-7-24.
 */
public class CameraEngine {
    private static final String TAG = "CameraEngine";
    private CameraParam mCameraParam = null;
    private Camera mCameraInstance = null;
    private CameraHelper mCameraHelper = null;
    private onCameraEngineListener mListener = null;
    private onCameraSetupCallback mCameraSetupCallback = null;

    public CameraEngine(CameraParam param, onCameraSetupCallback cb) {
        mCameraSetupCallback = cb;
        mCameraParam = param;
        mCameraHelper = new CameraHelper();
    }

    /**
     * 释放CameraEngine
     */
    public void release() {
        closeCamera();
        mCameraParam = null;
        mCameraHelper = null;
        mListener = null;
        mCameraSetupCallback = null;
    }

    /**
     * 设置相机事件监听器
     * @param l
     */
    public void setListener(onCameraEngineListener l) {
        mListener = l;
    }

    /**
     * 打开相机
     */
    public void openCamera() {
        openCamera(mCameraParam.mExpectedCameraId);
    }

    /**
     * 关闭相机
     */
    public void closeCamera() {
        if (mCameraInstance != null) {
            mCameraInstance.setPreviewCallbackWithBuffer(null);
            mCameraInstance.setPreviewCallback(null);
            mCameraInstance.stopPreview();
            mCameraInstance.release();
        }
        mCameraInstance = null;
        mCameraParam.mCameraInstance = null;
        if(mListener != null) {
            mListener.onCameraClosed();
        }
    }

    /**
     * 设置相机预览回调
     * @param buffer
     * @param cb
     */
    public void setPreviewCallback(byte[] buffer, Camera.PreviewCallback cb) {
        if (mCameraInstance != null) {
            mCameraInstance.addCallbackBuffer(buffer);
            mCameraInstance.setPreviewCallbackWithBuffer(cb);
        }
    }

    /**
     * 设置相机预览SurfaceTexture
     * @param st
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void setPreviewTexture(SurfaceTexture st) {
        if (mCameraInstance != null) {
            try {
                mCameraInstance.setPreviewTexture(st);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 开启相机预览
     */
    public void startPreview() {
        if(mCameraInstance != null) {
            mCameraInstance.startPreview();
        }
    }

    /**
     * 停止相机预览
     */
    public void stopPreview() {
        if(mCameraInstance != null) {
            mCameraInstance.stopPreview();
        }
    }

    /**
     * 相机切换
     */
    public void switchCamera() {
        closeCamera();
        mCameraParam.mPreviewCameraId = (mCameraParam.mPreviewCameraId + 1) % getNumberOfCameras();
        openCamera(mCameraParam.mPreviewCameraId);
    }

    private void setSceneMode(Camera.Parameters parameters) {
        List<String> supportedSceneModes = parameters.getSupportedSceneModes();
        if (supportedSceneModes != null && !supportedSceneModes.isEmpty()) {
            if (supportedSceneModes.contains(Camera.Parameters.SCENE_MODE_AUTO)) {
                parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
            }
        }
    }

    /**
     * 设置相机白平衡参数
     * @param parameters
     */
    private void setWhiteBalance(Camera.Parameters parameters) {
        List<String> supportedWhiteBalance = parameters.getSupportedWhiteBalance();
        if (supportedWhiteBalance != null && !supportedWhiteBalance.isEmpty()) {
            if (supportedWhiteBalance.contains(Camera.Parameters.WHITE_BALANCE_AUTO)) {
                parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            }
        }
    }

    /**
     * 检查摄像头是否支持闪光灯并关闭
     * @param parameters
     * @return
     */
    private boolean closeFlashLight(Camera.Parameters parameters) {
        if (parameters.getFlashMode() == null) {
            return false;
        }

        List<String> supportedFlashModes = parameters.getSupportedFlashModes();
        if (supportedFlashModes == null
                || supportedFlashModes.isEmpty()) {
            return false;
        }

        if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        }
        return true;
    }

    /**
     * 设置相机预览数据格式为NV12
     * @param parameters
     */
    private void setPreviewFormat(Camera.Parameters parameters) {
        int format = ImageFormat.NV21;
        parameters.setPreviewFormat(format);

    }

    /**
     * 设置相机输出帧率
     * @param parameters
     */
    private void setPreviewFps(Camera.Parameters parameters) {
        int[] range = getPreviewFps(mCameraParam.mExpectedFps, parameters.getSupportedPreviewFpsRange());
        parameters.setPreviewFpsRange(range[0], range[1]);
        if (range[0] == range[1]) {
            mCameraParam.mPreviewFps = range[0];
        } else {
            mCameraParam.mPreviewFps = (range[0] + range[1]) / 2;
        }
        mCameraParam.mPreviewFps = mCameraParam.mPreviewFps / 1000;
    }

    /**
     * 设置相机预览视频分分辨率
     * @param parameters
     */
    private void setPreviewSize(Camera.Parameters parameters) {
        Camera.Size preSize = getPreviewSize(
                mCameraInstance.new Size(mCameraParam.mExpectedWidth, mCameraParam.mExpectedHeight),
                parameters.getSupportedPreviewSizes());
        parameters.setPreviewSize(preSize.width, preSize.height);
        mCameraParam.mPreviewWidth = preSize.width;
        mCameraParam.mPreviewHeight = preSize.height;
    }

    /**
     * 设置相机的聚焦模式
     * @param parameters
     */
    private void setFocusModes(Camera.Parameters parameters) {
        List<String> supportedFocusModes = parameters.getSupportedFocusModes();
        if (supportedFocusModes != null && !supportedFocusModes.isEmpty()) {
            if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } else if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                mCameraInstance.autoFocus(null);
            } else {
                parameters.setFocusMode(supportedFocusModes.get(0));
            }
        }
    }

    /**
     * 设置相机参数
     */
    private void setParameters(Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        setFocusModes(parameters);
        closeFlashLight(parameters);
        setPreviewSize(parameters);
        setPreviewFps(parameters);
        setPreviewFormat(parameters);
        setWhiteBalance(parameters);
        setSceneMode(parameters);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            parameters.setRecordingHint(true);
        }
        camera.setParameters(parameters);
    }

    /**
     * 检查相机ID是否有效
     * @param id 相机ID
     * @return 返回有效的相机ID
     */
    private int checkCameraID(int id) {
        int cameraId = -1;
        int cameraNum = getNumberOfCameras();
        if(cameraNum > id) {
            cameraId = id;
        } else if(cameraNum == 1) {
            cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        }

        return cameraId;
    }

    /**
     * 打开相机
     * @param id 希望启动的相机ID
     */
    private void openCamera(final int id) {
        if((mCameraParam.mPreviewCameraId = checkCameraID(id)) < 0) {
            if (mListener != null) {
                mListener.onCameraError();
            }
            Log.e(TAG,"Didn't find the camera that id is " + id);
            return;
        }

        mCameraInstance = getCameraInstance(mCameraParam.mPreviewCameraId);
        if (mCameraInstance == null) {
            if (mListener != null) {
                mListener.onCameraError();
            }
            Log.e(TAG,"open camera " + mCameraParam.mPreviewCameraId + " failed");
            return;
        }
        mCameraParam.mCameraInstance = mCameraInstance;
        setParameters(mCameraInstance);

        mCameraParam.mOrientation = mCameraHelper.getCameraDisplayOrientation(mCameraParam.mWindowRotation, mCameraParam.mPreviewCameraId);
        CameraHelper.CameraInfo2 cameraInfo = new CameraHelper.CameraInfo2();
        mCameraHelper.getCameraInfo(mCameraParam.mPreviewCameraId, cameraInfo);
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            mCameraParam.mFlipHorizontal = true;
            mCameraParam.mFlipVertical = false;
        } else {
            if (mCameraParam.mOrientation == 90 || mCameraParam.mOrientation == 270) {
                mCameraParam.mFlipHorizontal = false;
                mCameraParam.mFlipVertical = true;
            } else {
                mCameraParam.mFlipHorizontal = true;
                mCameraParam.mFlipVertical = false;
            }
        }
        mCameraSetupCallback.setUpCamera(mCameraParam);
        if (mListener != null) {
            mListener.onCameraOpened();
        }
    }

    private Camera getCameraInstance(final int id) {
        Camera c = null;
        try {
            c = mCameraHelper.openCamera(id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return c;
    }

    private int getNumberOfCameras() {
        return mCameraHelper.getNumberOfCameras();
    }

    /**
     * 寻找与期望帧率最接近的fps
     * 优先考虑固定帧率，即range[0] == range[1]
     * 其次考虑range[0] range[1]与期望fps的差值绝对值
     * @param expectedFps
     * @param fpsRanges
     * @return
     */
    private int[] getPreviewFps(int expectedFps, List<int[]> fpsRanges) {
        expectedFps *= 1000;
        int[] closestRange = fpsRanges.get(0);
        int measure = Math.abs(closestRange[0] - expectedFps) + Math.abs(closestRange[1] - expectedFps);
        for (int[] range : fpsRanges) {
            Log.i(TAG, "range[0] "+range[0]+" range[1] "+range[1]);
            if (range[0] == range[1]) {
                int curMeasure = Math.abs(range[0] - expectedFps) + Math.abs(range[1] - expectedFps);
                if (curMeasure < measure) {
                    closestRange = range;
                    measure = curMeasure;
                }
            }
        }
        Log.i(TAG, "closestRange[0] "+closestRange[0]+" closestRange[1] "+closestRange[1]);
        return closestRange;
    }

    /**
     * 从相机支持的分辨率列表中找到最接近期望分辨率的值
     * 优先考虑宽高比接近，其次考虑w*h数值
     * @param resolution
     * @param sizes
     * @return
     */
    private Camera.Size getPreviewSize(Camera.Size resolution, List<Camera.Size> sizes) {
        float diff_d = 100f;
        float xdy = (float) resolution.width / (float) resolution.height;
        long diff_m = Long.MAX_VALUE;
        long xmy = resolution.width * resolution.height;
        Camera.Size best = sizes.get(0);
        for (Camera.Size size : sizes) {
            Log.i(TAG, "size.width "+size.width+" size.height "+size.height);
            if (size.equals(resolution)) {
                Log.i(TAG, "best.width "+size.width+" best.height "+size.height);
                return size;
            }

            float tmp_d = Math.abs(((float) size.width / (float) size.height) - xdy);
            if (tmp_d <= diff_d) {
                diff_d = tmp_d;
                long tmp_m = Math.abs(size.width * size.height - xmy);
                if(tmp_m < diff_m)
                {
                    diff_m = tmp_m;
                    best = size;
                }
            }
        }
        Log.i(TAG, "best.width "+best.width+" best.height "+best.height);
        return best;
    }

    /**
     * 获取Camera实例
     * @return
     */
    public Camera getCameraInstance() {
        return mCameraInstance;
    }

    /**
     * 获取CameraParam
     * @return
     */
    public CameraParam getCameraParam() {
        return mCameraParam;
    }

    /**
     * 得到录制宽
     *
     * @return 宽
     */
    public int getRecordWidth() {
        return mCameraParam.mRecordWidth;
    }

    /**
     * 得到录制高
     *
     * @return
     */
    public int getRecordHeight() {
        return mCameraParam.mRecordHeight;
    }

    /**
     * 得到预览宽
     *
     * @return 宽
     */
    public int getPreviewWidth() {
        return mCameraParam.mPreviewWidth;
    }

    /**
     * 得到预览高
     *
     * @return
     */
    public int getPreviewHeight() {
        return mCameraParam.mPreviewHeight;
    }

    /**
     * 得到相机实际输出帧率
     *
     * @return 帧率
     */
    public int getPreviewFps() {
        return mCameraParam.mPreviewFps;
    }
}
