package com.xmly.media.video.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;

public class XMPIPView extends XMDecoderView {
    private static final String TAG = "XMPIPView";
    private static final int FPS = 25;
    private int mPIPVideoWidth = 0;
    private int mPIPVideoHeight = 0;
    private String mPIPInputPath = null;
    private XMDecoder mPIPDecoder = null;
    private XMDecoder.FrameCallback mPIPSpeedControlCallback = null;
    private Surface mPIPSurface = null;
    private XMPlayerView.Rect mRect;

    @Override
    public void onInit() {
        super.onInit();
        mPIPSpeedControlCallback = new XMDecoder.FrameCallback() {
            private static final int POLLING_TIME = 10; //ms
            private volatile boolean mIsStopRequested = false;
            @Override
            public void stop(boolean stop) {
                synchronized (this) {
                    mIsStopRequested = stop;
                }
            }

            @Override
            public void preRender(long presentationTimeUsec) {
            }

            @Override
            public void offlineRender(long position) {
                while (position > mSpeedControlCallback.getCurrentPosition()) {
                    try {
                        Thread.sleep(POLLING_TIME, 0);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if (mIsStopRequested) {
                        stop(false);
                        Log.i(TAG, "stop is requested, break");
                        break;
                    }
                }
            }

            @Override
            public void postRender() {
            }

            @Override
            public void loopReset() {
            }
        };

        try {
            mPIPDecoder = new XMDecoder(mPIPSpeedControlCallback, mPIPFeedback);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onInitialized() {
        mRenderer = new XMPIPRender(mContext, mRecorder);
        ((XMPIPRender) mRenderer).setSurfacePreparedListener(mSurfacePreparedListener);
        mRenderer.setListener(onXMPlayerRecorderListener);
        mSpeedControlCallback.setRecorder(mRecorder);
        mSpeedControlCallback.setRenderer(((XMDecoderRenderer) mRenderer));

        ((XMPIPRender) mRenderer).setPipSurfacePreparedListener(mPIPSurfacePreparedListener);
    }

    public XMPIPView(Context context) {
        super(context);
        init();
    }

    public void setPipDecoderProgressListener(XMDecoder.ProgressListener l) {
        if (mPIPDecoder != null)
            mPIPDecoder.setProgressListener(l);
    }

    public void setPipDecoderCompletionListener(XMDecoder.OnCompletionListener l) {
        if (mPIPDecoder != null)
            mPIPDecoder.setOnCompletionListener(l);
    }

    public void setPipRectCoordinate(XMPlayerView.Rect rect) {
        if (rect == null) {
            Log.e(TAG, "Rect is null");
            return;
        }

        mRect = rect;
        setPipRectCoordinate();
    }

    public void start(String inputPIPPath, String inputTemplatePath, String outputPath) throws IllegalStateException {
        synchronized (this) {
            mPIPInputPath = inputPIPPath;
        }
        start(inputTemplatePath, outputPath);
    }

    private XMDecoder.DecoderFeedback mPIPFeedback = new XMDecoder.DecoderFeedback() {
        @Override
        public void decodeStarted() {
            Log.i(TAG, "decoder started");
        }

        @Override
        public void decodeStopped() {
            Log.i(TAG, "decoder stopped");
            ((XMPIPRender) mRenderer).stopPipImageDraw();
            synchronized (this) {
                if (mPIPDecoder != null) {
                    mPIPDecoder.requestStop();
                    mPIPDecoder.waitForStop();
                }
            }
        }
    };

    private ISurfacePreparedListener mPIPSurfacePreparedListener = new ISurfacePreparedListener() {
        @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        @Override
        public void surfacePrepared(Surface surface) {
            if (mPIPSurface != null) {
                mPIPSurface.release();
            }

            try {
                mPIPSurface = surface;

                mPIPDecoder.setDataSource(mPIPInputPath, surface);
                mPIPDecoder.setLoopMode(false);

                mPIPVideoWidth = mPIPDecoder.getVideoWidth();
                mPIPVideoHeight = mPIPDecoder.getVideoHeight();
                setPipRectCoordinate();
                ((XMPIPRender) mRenderer).onPipVideoSizeChanged(mPIPVideoWidth, mPIPVideoHeight);
                ((XMPIPRender) mRenderer).init();

                mPIPDecoder.start("pip video decoder thread");
            } catch (IOException e) {
                if (surface != null) {
                    surface.release();
                }
                mPIPSurface = null;
                stop();
                e.printStackTrace();
            }
        }
    };

    @Override
    protected void setPipRectCoordinate() {
        if (mRect == null) {
            Log.e(TAG, "mRect is null");
            return;
        }

        if (mVideoHeight == 0 || mVideoWidth == 0
                || mPIPVideoHeight == 0 || mPIPVideoWidth == 0) {
            Log.w(TAG, "decoder is not prepared");
            return;
        }

        float[] buffer = new float[4];
        buffer[0] = mRect.left;
        buffer[1] = mRect.bottom;
        buffer[2] = mRect.right;
        buffer[3] = mRect.top;

        if (((buffer[2] - buffer[0]) / (buffer[3] - buffer[1])) > (mPIPVideoWidth / mPIPVideoHeight)) {
            buffer[2] = mPIPVideoWidth * (buffer[3] - buffer[1]) / mPIPVideoHeight + buffer[0];
        } else {
            buffer[1] = buffer[3] - (buffer[2] - buffer[0]) * mPIPVideoHeight / mPIPVideoWidth;
        }

        if (mVideoWidth != 0) {
            buffer[2] = (mVideoHeight * (buffer[2] - buffer[0]) + mVideoWidth * buffer[0]) / mVideoWidth;
        }

        if(mRenderer != null) {
            ((XMPIPRender) mRenderer).setPipRectCoordinate(buffer);
        }
    }

    @Override
    protected void stopDecoder() {
        super.stopDecoder();
        if (mPIPDecoder != null) {
            mPIPDecoder.requestStop();
            mPIPDecoder.waitForStop();
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    protected void releaseDecoder() {
        super.releaseDecoder();
        if (mPIPDecoder != null) {
            mPIPDecoder.requestStop();
            mPIPDecoder.waitForStop();
            mPIPDecoder = null;
        }

        if (mPIPSurface != null) {
            mPIPSurface.release();
            mPIPSurface = null;
        }
    }
}
