package com.xmly.media.video.view;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.xmly.media.camera.view.CameraView;
import com.xmly.media.camera.view.recorder.IXMCameraRecorderListener;
import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.camera.view.recorder.XMMediaRecorderParams;
import com.xmly.media.gles.utils.XMFilterType;

/**
 * Created by sunyc on 19-7-9.
 */
class XMBaseView {
    private static final String TAG = "XMBaseView";
    protected XMBaseRenderer mRenderer = null;
    protected XMMediaRecorder mRecorder = null;
    protected XMMediaRecorderParams params = new XMMediaRecorderParams();
    protected GLSurfaceView mGLSurfaceView = null;
    protected Context mContext = null;
    private boolean useSoftEncoder = true;
    private boolean hasAudio = false;
    private boolean hasVideo = true;
    protected volatile boolean mImageReaderPrepared = false;
    protected volatile boolean mXMMediaRecorderPrepared = false;
    private volatile boolean isRecording = false;
    protected CameraView.ICameraViewListener mListener = null;

    public void init() {
        onInit();
        mRecorder = new XMMediaRecorder(useSoftEncoder, hasAudio, hasVideo);
        mRecorder.setListener(onXMPlayerRecorderListener);
        onInitialized();
    }

    public void onInit() {
    }

    public void onInitialized() {
    }

    public XMBaseView(Context context) {
        mContext = context;
    }

    public void setListener(CameraView.ICameraViewListener l) {
        mListener = l;
    }

    public void setGLSurfaceView(final GLSurfaceView view) {
        mGLSurfaceView = view;
        if(mRenderer != null) {
            mRenderer.setGLSurfaceView(view);
        }
    }

    protected void requestRender() {
        if (mGLSurfaceView != null) {
            mGLSurfaceView.requestRender();
        }
    }

    public void setFilter(final XMFilterType filtertype) {
        Log.i(TAG,"setFilter filter type " + filtertype);
        if (mRenderer != null) {
            mRenderer.setFilter(filtertype);
        }
    }

    protected void checkRendererStatus() {
        if (mRenderer != null) {
            mRenderer.checkRendererStatus();
        }
    }

    protected void setStatus(boolean running) {
        isRecording = running;
    }

    protected boolean getStatus() {
        return isRecording;
    }

    protected void enableGPUCopier(boolean enable) {
        if (mRenderer != null) {
            mRenderer.enableGPUCopier(enable);
        }
    }

    protected IXMCameraRecorderListener onXMPlayerRecorderListener = new IXMCameraRecorderListener() {
        @Override
        public void onImageReaderPrepared() {
            Log.i(TAG, "onImageReaderPrepared");
            mImageReaderPrepared = true;
            if(mXMMediaRecorderPrepared)
                mRecorder.start();
        }

        @Override
        public void onRecorderPrepared() {
            Log.i(TAG, "onRecorderPrepared");
            mXMMediaRecorderPrepared = true;
            if(mImageReaderPrepared)
                mRecorder.start();
        }

        @Override
        public void onRecorderStarted() {
            synchronized (this) {
                enableGPUCopier(true);
            }
            Log.i(TAG, "onRecorderStarted");
            if (mListener != null) {mListener.onRecorderStarted();}
        }

        @Override
        public void onRecorderStopped() {
            Log.i(TAG, "onRecorderStopped");
            synchronized (this) {
                enableGPUCopier(false);
                setStatus(false);
            }
            if (mListener != null) {mListener.onRecorderStopped();}
        }

        @Override
        public void onRecorderError() {
            Log.e(TAG, "onRecorderError");
            stop();
            synchronized (this) {
                setStatus(false);
            }
            if (mListener != null) {mListener.onRecorderError();}
        }

        @Override
        public void onPreviewStarted() {
            Log.i(TAG, "onPreviewStarted");
            if (mListener != null) {mListener.onPreviewStarted();}
        }

        @Override
        public void onPreviewStopped() {
            Log.i(TAG, "onPreviewStopped");
            if (mListener != null) {mListener.onPreviewStopped();}
        }

        @Override
        public void onPreviewError() {
            Log.e(TAG, "onPreviewError");
            if (mListener != null) {mListener.onPreviewError();}
        }
    };

    private void stopRecorder() {
        if (mRecorder != null) {
            mRecorder.stop();
        }
    }

    public void stop() {
        synchronized (this) {
            enableGPUCopier(false);
            stopRecorder();
        }
    }

    private void releaseRenderer() {
        if (mRenderer != null) {
            mRenderer.release();
            mRenderer = null;
        }
    }

    private void releaseRecorder() {
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder.setListener(null);
            mRecorder = null;
        }
    }

    public void release() {
        synchronized (this) {
            enableGPUCopier(false);
            releaseRecorder();
            releaseRenderer();
        }
    }
}
