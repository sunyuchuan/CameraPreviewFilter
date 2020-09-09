package com.xmly.media.camera.view.utils;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Build;
import android.util.Log;

import com.xmly.media.camera.view.recorder.IXMCameraRecorderListener;

import java.util.List;

/**
 * Created by sunyc on 18-10-26.
 */

public class CameraManager {
    private final static String TAG = "CameraManager";
    private static CameraManager mInstance = null;
    private int mExpectedWidth = 960;
    private int mExpectedHeight = 540;
    private int mFps = 15;
    private int mCurrentCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private Camera mCameraInstance;
    private CameraHelper mCameraHelper;
    private ICameraCallback mCameraCallback;
    private int mWindowRotation = 1;
    private IXMCameraRecorderListener mListener = null;

    public static CameraManager getInstance() {
        if (mInstance == null) {
            mInstance = new CameraManager();
        }
        return mInstance;
    }

    public CameraManager() {
        mCameraHelper = new CameraHelper();
    }

    public void setCameraCallback(ICameraCallback callback)
    {
        mCameraCallback = callback;
    }

    public void setWindowRotation(int rotation)
    {
        mWindowRotation = rotation;
        Log.i(TAG,"mWindowRotation " + rotation);
    }

    public void setExpectedFps(int fps) {
        mFps = fps;
        Log.i(TAG, "mFps " + fps);
    }

    public void setExpectedResolution(int w, int h) {
        mExpectedWidth = w;
        mExpectedHeight = h;
        Log.i(TAG,"mExpectedWidth " + w + ", mExpectedHeight " + h);
    }

    public void onResume() {
        setUpCamera(mCurrentCameraId);
    }

    public void onRelease() {
        releaseCamera();
    }

    public void releaseInstance() {
        if (mCameraInstance != null) {
            synchronized (mCameraInstance) {
                mCameraInstance.setPreviewCallbackWithBuffer(null);
                mCameraInstance.setPreviewCallback(null);
                mCameraInstance.stopPreview();
                mCameraInstance.release();
                if(mListener != null)
                    mListener.onPreviewStopped();
            }
        }
        mCameraInstance = null;
        mCameraHelper = null;
        mCameraCallback = null;
        mListener = null;
        mInstance = null;
    }

    public void startPreview() {
        if(mCameraInstance != null)
            mCameraInstance.startPreview();
    }

    public void stopPreview() {
        if(mCameraInstance != null)
            mCameraInstance.stopPreview();
    }

    public void switchCamera() {
        releaseCamera();
        mCurrentCameraId = (mCurrentCameraId + 1) % mCameraHelper.getNumberOfCameras();
        setUpCamera(mCurrentCameraId);
    }

    public void setListener(IXMCameraRecorderListener l) {
        mListener = l;
    }

    private void setUpCamera(final int id) {
        int cameraId = -1;
        int cameraNum = getNumberOfCameras();
        Log.i(TAG,"the number of cameras is " + cameraNum);
        if(cameraNum > id) {
            cameraId = id;
        } else if(cameraNum == 1) {
            cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        } else {
            mListener.onPreviewError();
            Log.e(TAG,"Didn't find the camera");
            return;
        }

        mCameraInstance = getCameraInstance(cameraId);
        if (mCameraInstance == null) {
            mListener.onPreviewError();
            Log.e(TAG,"get camera " + cameraId + " fail");
            return;
        }
        mCurrentCameraId = cameraId;

        Camera.Parameters parameters = mCameraInstance.getParameters();
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

        Camera.Size preSize = getPreviewSize(mCameraInstance.new Size(mExpectedWidth, mExpectedHeight), parameters.getSupportedPreviewSizes());
        parameters.setPreviewSize(preSize.width, preSize.height);

        int[] range = getFpsRange(mFps, parameters.getSupportedPreviewFpsRange());
        parameters.setPreviewFpsRange(range[0], range[1]);

        int format = ImageFormat.NV21;
        parameters.setPreviewFormat(format);

        List<String> supportedFlashModes = parameters.getSupportedFlashModes();
        if (supportedFlashModes != null && !supportedFlashModes.isEmpty()) {
            if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            }
        }

        List<String> supportedWhiteBalance = parameters.getSupportedWhiteBalance();
        if (supportedWhiteBalance != null && !supportedWhiteBalance.isEmpty()) {
            if (supportedWhiteBalance.contains(Camera.Parameters.WHITE_BALANCE_AUTO)) {
                parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            }
        }

        List<String> supportedSceneModes = parameters.getSupportedSceneModes();
        if (supportedSceneModes != null && !supportedSceneModes.isEmpty()) {
            if (supportedSceneModes.contains(Camera.Parameters.SCENE_MODE_AUTO)) {
                parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            parameters.setRecordingHint(true);
        }

        mCameraInstance.setParameters(parameters);

        int orientation = mCameraHelper.getCameraDisplayOrientation(mWindowRotation, cameraId);
        Log.i(TAG,"mWindowRotation " + mWindowRotation + " cameraId " + cameraId + ", result of orientation is " + orientation);
        CameraHelper.CameraInfo2 cameraInfo = new CameraHelper.CameraInfo2();
        mCameraHelper.getCameraInfo(cameraId, cameraInfo);
        boolean flipHorizontal = false;
        boolean flipVertical = false;
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            flipHorizontal = true;
            flipVertical = false;
        } else {
            if (orientation == 90 || orientation == 270) {
                flipHorizontal = false;
                flipVertical = true;
            } else {
                flipHorizontal = true;
                flipVertical = false;
            }
        }
        mCameraCallback.setUpCamera(mCameraInstance, orientation, flipHorizontal, flipVertical);
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

    private int[] getFpsRange(int expectedFps, List<int[]> fpsRanges) {
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

    private void releaseCamera() {
        if (mCameraInstance != null) {
            synchronized (mCameraInstance) {
                mCameraInstance.setPreviewCallbackWithBuffer(null);
                mCameraInstance.setPreviewCallback(null);
                mCameraInstance.stopPreview();
                mCameraInstance.release();
            }
            mCameraInstance = null;
        }
        mListener.onPreviewStopped();
    }
}
