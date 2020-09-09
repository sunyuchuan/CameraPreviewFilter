package com.xmly.media.video.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by sunyc on 19-3-1.
 */
public class XMDecoderView extends XMBaseView {
    private static final String TAG = "XMDecoderView";
    private static final int FPS = 25;
    protected int mVideoWidth = 0;
    protected int mVideoHeight = 0;
    private int mVideoFps = 0;
    private XMDecoder mDecoder = null;
    protected SpeedControlCallback mSpeedControlCallback = null;
    private String mInputPath = null;
    private String mOutputPath = null;
    private Surface mSurface = null;
    private ArrayList<Segment> mSegmentArray = null;

    @Override
    public void onInit() {
        super.onInit();
        mSpeedControlCallback = new SpeedControlCallback();
        try {
            mDecoder = new XMDecoder(mSpeedControlCallback, mFeedback);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onInitialized() {
        super.onInitialized();
        mRenderer = new XMDecoderRenderer(mContext, mRecorder);
        ((XMDecoderRenderer) mRenderer).setSurfacePreparedListener(mSurfacePreparedListener);
        mRenderer.setListener(onXMPlayerRecorderListener);
        mSpeedControlCallback.setRecorder(mRecorder);
        mSpeedControlCallback.setRenderer(((XMDecoderRenderer) mRenderer));
    }

    public XMDecoderView(Context context) {
        super(context);
        init();
    }

    public void setDecoderProgressListener(XMDecoder.ProgressListener l) {
        if (mDecoder != null)
            mDecoder.setProgressListener(l);
    }

    public void setDecoderCompletionListener(XMDecoder.OnCompletionListener l) {
        if (mDecoder != null)
            mDecoder.setOnCompletionListener(l);
    }

    public void setSegmentArray(float[] start, float[] end) {
        if (start.length != end.length)
            return;

        mSegmentArray = new ArrayList<Segment>();
        for (int i = 0; i < start.length; i++) {
            mSegmentArray.add(new Segment(start[i], end[i]));
        }

        if (mSpeedControlCallback != null) {
            mSpeedControlCallback.setSegmentArray(mSegmentArray);
        }
    }

    protected void setPipRectCoordinate() {
    }

    /**
     * start offline processing video
     *
     * @param inputPath  input video path
     * @param outputPath output video path
     * @throws IllegalStateException
     */
    public void start(String inputPath, String outputPath) throws IllegalStateException {
        synchronized (this) {
            if (getStatus()) {
                Log.d(TAG, "start : Decoder is running, pls waiting decoder stop");
                throw new IllegalStateException();
            }

            mInputPath = inputPath;
            mOutputPath = outputPath;
            ((XMDecoderRenderer) mRenderer).prepareVideoSurface();
            setStatus(true);
        }
    }

    /**
     * stop decoder
     */
    @Override
    public void stop() {
        super.stop();
        synchronized (this) {
            stopDecoder();
        }
    }

    /**
     * release decoder
     */
    @Override
    public void release() {
        super.release();
        synchronized (this) {
            releaseDecoder();
        }
    }

    private void startRecorder_l(String outputPath, int outputWidth, int outputHeight) {
        if (mRecorder != null) {
            Log.i(TAG, "startRecorder_l outputPath " + outputPath);
            mImageReaderPrepared = false;
            mXMMediaRecorderPrepared = false;
            checkRendererStatus();
            enableGPUCopier(true);

            HashMap<String, String> config = new HashMap<String, String>();
            config.put("width", String.valueOf(outputWidth));
            config.put("height", String.valueOf(outputHeight));
            config.put("bit_rate", String.valueOf(params.bitrate));
            config.put("fps", String.valueOf(mVideoFps));
            config.put("gop_size", String.valueOf((int) (params.gop_size * mVideoFps)));
            config.put("crf", String.valueOf(params.crf));
            config.put("multiple", String.valueOf(params.multiple));
            config.put("max_b_frames", String.valueOf(params.max_b_frames));
            config.put("CFR", String.valueOf(params.TRUE));
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

    protected void stopDecoder() {
        if (mDecoder != null) {
            mDecoder.requestStop();
            mDecoder.waitForStop();
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    protected void releaseDecoder() {
        if (mDecoder != null) {
            mDecoder.requestStop();
            mDecoder.waitForStop();
            mDecoder = null;
        }

        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
    }

    private XMDecoder.DecoderFeedback mFeedback = new XMDecoder.DecoderFeedback() {
        @Override
        public void decodeStarted() {
            Log.i(TAG, "decoder started");
        }

        @Override
        public void decodeStopped() {
            Log.i(TAG, "decoder stopped");
            stop();
        }
    };

    protected ISurfacePreparedListener mSurfacePreparedListener = new ISurfacePreparedListener() {
        @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        @Override
        public void surfacePrepared(Surface surface) {
            if (mSurface != null) {
                mSurface.release();
            }

            try {
                mSurface = surface;

                mSpeedControlCallback.init();
                mDecoder.setDataSource(mInputPath, surface);
                mDecoder.setLoopMode(false);
                mVideoFps = mDecoder.getVideoFps();
                mSpeedControlCallback.setFixedPlaybackRate(mVideoFps);
                mSpeedControlCallback.setSegmentArray(mSegmentArray);

                mVideoWidth = mDecoder.getVideoWidth();
                mVideoHeight = mDecoder.getVideoHeight();
                setPipRectCoordinate();
                ((XMDecoderRenderer) mRenderer).onVideoSizeChanged(mVideoWidth, mVideoHeight);

                startRecorder_l(mOutputPath, mVideoWidth, mVideoHeight);
                mDecoder.start("XM MediaCodec Decoder");
            } catch (IOException e) {
                if (surface != null) {
                    surface.release();
                }
                mSurface = null;
                stop();
                e.printStackTrace();
            }
        }
    };

    public class Segment {
        public float start;
        public float end;

        public Segment(float start, float end) {
            this.start = start;
            this.end = end;
        }
    }
}
