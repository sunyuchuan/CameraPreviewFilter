package com.xmly.media.video.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.xmly.media.gles.filter.GPUImageFilterFactory;
import com.xmly.media.gles.utils.XMFilterType;

import java.util.HashMap;

/**
 * Created by sunyc on 19-5-16.
 */

public class XMImageView extends XMBaseView {
    private static final String TAG = "XMImageView";
    private int mOutputWidth = 0;
    private int mOutputHeight = 0;
    private volatile boolean mRefreshThreadAbort = false;
    private int mRefreshRate = 15;
    private long mRefreshTime = 0l;
    private Object mSynOperation = new Object();
    private boolean isRunning = false;

    @Override
    public void onInitialized() {
        super.onInitialized();
        mRenderer = new XMImageRenderer(mContext, mRecorder);
        mRenderer.setListener(onXMPlayerRecorderListener);
    }

    public XMImageView(Context context) {
        super(context);
        init();
    }

    /**
     * start image preview
     * @param firstImagePath the first image of path,The first one is displayed by default
     * @param outWidth output video of width
     * @param outHeight output video of height
     * @param outFps output video of frame_rate
     * @throws IllegalStateException
     */
    public void startPreview(String firstImagePath, int outWidth, int outHeight, int outFps) throws IllegalStateException {
        synchronized (mSynOperation) {
            if (getRefreshStatus()) {
                Log.w(TAG, "refresh thread already running");
                onXMPlayerRecorderListener.onPreviewStarted();
                return;
            }

            Log.i(TAG, "startPreview refresh rate is " + outFps);
            mOutputWidth = align(outWidth, 2);
            mOutputHeight = align(outHeight, 2);
            if (mRenderer != null) {
                ((XMImageRenderer) mRenderer).setVideoSize(mOutputWidth, mOutputHeight);
                ((XMImageRenderer) mRenderer).setNextImage(firstImagePath);
            }

            initRefreshThread(outFps);
            Thread thread = new RefreshThread();
            thread.start();
            while (!getRefreshStatus()) {
                try {
                    mSynOperation.wait();
                } catch (InterruptedException ie) {
                    Log.e(TAG,"InterruptedException " + ie.toString());
                }
            }
        }
        onXMPlayerRecorderListener.onPreviewStarted();
    }

    /**
     * stop image preview
     */
    public void stopPreview() {
        synchronized (mSynOperation) {
            if (!getRefreshStatus()) {
                onXMPlayerRecorderListener.onPreviewStopped();
                return;
            }

            setRefreshThreadAbort(true);
            while (getRefreshStatus()) {
                try {
                    mSynOperation.wait();
                } catch (InterruptedException ie) {
                    Log.e(TAG, "InterruptedException " + ie.toString());
                }
            }
        }
        onXMPlayerRecorderListener.onPreviewStopped();
    }

    /**
     * start video recording
     * @param outputPath output video of path
     * @param outputWidth output video of width
     * @param outputHeight output video of height
     */
    public void startRecorder(String outputPath, int outputWidth, int outputHeight) {
        synchronized (mSynOperation) {
            if (getStatus()) {
                Log.w(TAG, "Recorder is running, exit");
                onXMPlayerRecorderListener.onRecorderStarted();
                return;
            }

            mOutputWidth = align(outputWidth, 2);
            mOutputHeight = align(outputHeight, 2);
            if (mRenderer != null) {
                ((XMImageRenderer) mRenderer).setVideoSize(mOutputWidth, mOutputHeight);
            }

            startRecorder_l(outputPath, mOutputWidth, mOutputHeight);
            setStatus(true);
        }
    }

    public void stopRecorder() {
        super.stop();
    }

    public void release() {
        super.release();
    }

    public void setLogo(String logoPath, Rect rect) {
        synchronized (mSynOperation) {
            if (logoPath == null || rect == null)
                return;

            float[] logoRect = new float[]{rect.left, rect.bottom, rect.right, rect.top};
            if (mRenderer != null) {
                Bitmap bmp = GPUImageFilterFactory.decodeBitmap(logoPath);
                ((XMImageRenderer) mRenderer).setLogo(bmp, logoRect);
            }
        }
    }

    public void setImage(String imagePath) {
        if (imagePath == null)
            return;

        synchronized (mSynOperation) {
            if (mRenderer != null) {
                ((XMImageRenderer) mRenderer).setImage(imagePath);
            }
        }
    }

    public void setFilter(final XMFilterType filtertype) {
        synchronized (mSynOperation) {
            super.setFilter(filtertype);
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
            config.put("bit_rate", String.valueOf((int) (700000 * ((float) (outputWidth * outputHeight) / (float) (540 * 960)))));
            config.put("fps", String.valueOf(mRefreshRate));
            config.put("gop_size", String.valueOf((int) (params.gop_size * mRefreshRate)));
            config.put("crf", String.valueOf(params.crf));
            config.put("multiple", String.valueOf(params.multiple));
            config.put("max_b_frames", String.valueOf(params.max_b_frames));
            config.put("CFR", String.valueOf(params.FALSE));
            config.put("output_filename", outputPath);
            config.put("preset", params.preset);
            config.put("tune", params.tune);
            if (!mRecorder.setConfigParams(config)) {
                Log.e(TAG, "setConfigParams failed, exit");
                enableGPUCopier(false);
                config.clear();
                return;
            }

            config.clear();
            mRecorder.prepareAsync();
        }
    }

    private boolean getRefreshStatus() {
        return isRunning;
    }

    private void setRefreshStatus(boolean running) {
        isRunning = running;
    }

    private boolean getRefreshThreadAbort() {
        return mRefreshThreadAbort;
    }

    private void setRefreshThreadAbort(boolean abort) {
        mRefreshThreadAbort = abort;
    }

    private void initRefreshThread(int fps) {
        setRefreshThreadAbort(false);
        mRefreshRate = fps;
        mRefreshTime = 0l;
    }

    private int align(int x, int align)
    {
        return ((( x ) + (align) - 1) / (align) * (align));
    }

    class RefreshThread extends Thread {
        @Override
        public void run() {
            synchronized (mSynOperation) {
                setRefreshStatus(true);
                mSynOperation.notify();
            }

            while (!getRefreshThreadAbort()) {
                try {
                    Thread.sleep((int) (1000 / mRefreshRate), 0);
                    mRefreshTime ++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                requestRender();
            }

            synchronized (mSynOperation) {
                setRefreshStatus(false);
                mSynOperation.notify();
            }
            Log.i(TAG, "RefreshThread exit");
        }
    }

    public static class Rect {
        public float left;
        public float bottom;
        public float right;
        public float top;

        public Rect() {
        }

        public Rect(float left, float bottom, float right, float top) {
            this.left = left;
            this.bottom = bottom;
            this.right = right;
            this.top = top;
        }
    }
}
