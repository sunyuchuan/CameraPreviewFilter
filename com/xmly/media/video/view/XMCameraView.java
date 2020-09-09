package com.xmly.media.video.view;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.View;

import com.xmly.media.camera.view.utils.CameraManager;
import com.xmly.media.camera.view.utils.ICameraCallback;

import java.util.HashMap;

/**
 * Created by sunyc on 19-3-1.
 */

public class XMCameraView extends XMBaseView implements ICameraCallback {
    private static final String TAG = "XMCameraView";
    private static final int FPS = 15;
    private CameraManager mCamera = null;
    private int mCameraPreviewWidth = 0;
    private int mCameraPreviewHeight = 0;
    private int mCameraOuptutFps = 0;

    @Override
    public void onInit() {
        super.onInit();
        mCamera = new CameraManager();
        mCamera.setCameraCallback(this);
        mCamera.setListener(onXMPlayerRecorderListener);
    }

    @Override
    public void onInitialized() {
        super.onInitialized();
        mRenderer = new XMCameraRenderer(mContext, mRecorder);
        mRenderer.setListener(onXMPlayerRecorderListener);
    }

    public XMCameraView(Context context) {
        super(context);
        init();
    }

    public void startRecorder(String outputPath) throws IllegalStateException {
        synchronized (this) {
            if (getStatus()) {
                Log.d(TAG, "start : this is running, pls waiting stop");
                throw new IllegalStateException();
            }

            startRecorder_l(outputPath, ((XMCameraRenderer) mRenderer).getCameraOutputWidth(), ((XMCameraRenderer) mRenderer).getCameraOutputHeight());
            setStatus(true);
        }
    }

    public void stopRecorder() {
        super.stop();
    }

    public void setWindowRotation(int windowRotation) {
        if (mCamera != null)
            mCamera.setWindowRotation(windowRotation);
    }

    public void setExpectedFps(int fps) {
        if (mCamera != null)
            mCamera.setExpectedFps(fps);
    }

    public void setExpectedResolution(int w, int h) {
        if (mCamera != null)
            mCamera.setExpectedResolution(w, h);
    }

    /*Turn on camera preview*/
    public void startCameraPreview() {
        if(mRenderer != null)
            ((XMCameraRenderer) mRenderer).cleanRunOnSetupCamera();

        if (mCamera != null)
            mCamera.onResume();

        if (mGLSurfaceView != null) {
            mGLSurfaceView.clearAnimation();
            mGLSurfaceView.setVisibility(View.VISIBLE);
        }
    }

    /*stop camera preview*/
    public void stopCameraPreview() {
        mCameraPreviewWidth = 0;
        mCameraPreviewHeight = 0;
        mCameraOuptutFps = 0;

        if(mRenderer != null) {
            ((XMCameraRenderer) mRenderer).cleanRunOnSetupCamera();
        }

        if (mGLSurfaceView != null) {
            mGLSurfaceView.setVisibility(View.GONE);
            mGLSurfaceView.clearAnimation();
        }

        if (mCamera != null)
            mCamera.onRelease();
    }

    /*Switch to front or rear camera*/
    public void switchCamera() {
        if (mCamera != null) {
            mCamera.switchCamera();
            requestRender();
        }
    }

    @Override
    public void setUpCamera(final Camera camera, final int degrees, final boolean flipHorizontal,
                            final boolean flipVertical) {
        mCameraPreviewWidth = camera.getParameters().getPreviewSize().width;
        mCameraPreviewHeight = camera.getParameters().getPreviewSize().height;

        int[] range = new int[2];
        camera.getParameters().getPreviewFpsRange(range);
        mCameraOuptutFps = range[0] / 1000;
        if(range[1] != range[0])
        {
            Log.w(TAG, "camera output fps is dynamic, range from " + range[0] + " to " + range[1]);
            mCameraOuptutFps = 15;
        }
        Log.i(TAG, "PreviewSize = " + mCameraPreviewWidth + "x" + mCameraPreviewHeight + " mCameraOuptutFps " + mCameraOuptutFps);

        if (mRenderer != null) {
            ((XMCameraRenderer) mRenderer).setUpCamera(camera, degrees, flipHorizontal, flipVertical);
        }
    }

    public void release() {
        super.release();
        synchronized (this) {
            releaseCamera();
        }
    }

    private void startRecorder_l(String outputPath, int outputWidth, int outputHeight) {
        if (mRecorder != null) {
            Log.i(TAG, "startRecorder outputPath " + outputPath);
            mImageReaderPrepared = false;
            mXMMediaRecorderPrepared = false;
            checkRendererStatus();

            HashMap<String, String> config = new HashMap<String, String>();
            config.put("width", String.valueOf(outputWidth));
            config.put("height", String.valueOf(outputHeight));
            config.put("bit_rate", String.valueOf(700000));
            config.put("fps", String.valueOf(mCameraOuptutFps));
            config.put("gop_size", String.valueOf((int) (params.gop_size * mCameraOuptutFps)));
            config.put("crf", String.valueOf(params.crf));
            config.put("multiple", String.valueOf(params.multiple));
            config.put("max_b_frames", String.valueOf(params.max_b_frames));
            config.put("CFR", String.valueOf(params.FALSE));
            config.put("output_filename", outputPath);
            config.put("preset", params.preset);
            config.put("tune", params.tune);
            if (!mRecorder.setConfigParams(config)) {
                Log.e(TAG, "setConfigParams failed, exit");
                config.clear();
                return;
            }

            config.clear();
            mRecorder.prepareAsync();
        }
    }

    private void releaseCamera() {
        if (mCamera != null)
            mCamera.releaseInstance();
    }
}
