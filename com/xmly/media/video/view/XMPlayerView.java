package com.xmly.media.video.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.View;

import com.xmly.media.camera.view.utils.CameraManager;
import com.xmly.media.camera.view.utils.ICameraCallback;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by sunyc on 19-3-1.
 */
public class XMPlayerView extends XMBaseView implements ICameraCallback {
    private static final String TAG = "XMPlayerView";
    private static final float VIEW_ASPECT_RATIO = 4f / 3f;
    private static final int SLEEP_TIME = 100; //ms
    private static final int FPS = 25;
    private float videoAspectRatio = VIEW_ASPECT_RATIO;
    private CameraManager mCamera = null;
    private XMPlayer mPlayer = null;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;
    private int mCameraPreviewWidth = 0;
    private int mCameraPreviewHeight = 0;
    private int mCameraOuptutFps = 0;
    private Rect mRect = null;
    private SubtitleParser mSubtitleParser = null;
    private Subtitle.TextCanvasParam mTextCanvasParam = null;
    private volatile boolean mSubThreadAbort = false;
    private ArrayList<Subtitle> mSubtitleList = null;
    private Subtitle mSubtitle = null;
    private volatile int mSubIndex = 0;
    private Thread mSubThread = null;

    @Override
    public void onInit() {
        super.onInit();
        mCamera = new CameraManager();
        mCamera.setCameraCallback(this);
        mCamera.setListener(onXMPlayerRecorderListener);
        mPlayer = new XMPlayer();
        mPlayer.setOnCompletionListener(mOnCompletionListener);
        mPlayer.setOnInfoListener(mOnInfoListener);
    }

    @Override
    public void onInitialized() {
        super.onInitialized();
        mRenderer = new XMPlayerRenderer(mContext, mRecorder);
        ((XMPlayerRenderer) mRenderer).setSurfacePreparedListener(mPlayer);
        mRenderer.setListener(onXMPlayerRecorderListener);
        mSubtitleParser = new SubtitleParser();
    }

    public XMPlayerView(Context context) {
        super(context);
        init();
    }

    public void drawSubtitle(String str, String headImage) {
        if (mRenderer != null && mSubtitleParser != null) {
            Bitmap bmp = mSubtitleParser.drawTextToBitmap(str, headImage);
            if (bmp != null) {
                ((XMPlayerRenderer) mRenderer).loadSubBitmap(bmp);
            } else {
                invisibleSubtitle();
            }
        }
    }

    public long getCurrentPosition() {
        if (mPlayer != null) {
            return mPlayer.getCurrentPosition();
        }

        return 0;
    }

    public long getDuration() {
        if (mPlayer != null) {
            return mPlayer.getDuration();
        }

        return 0;
    }

    private void setSubtitleParam(Subtitle.TextCanvasParam param, ArrayList<Subtitle> list) {
        mSubtitleList = list;
        if (mSubtitleParser != null) {
            mSubtitleParser.initSubtitleParser(param);
        }
    }

    public void start(String videoPath, String outputPath, Subtitle.TextCanvasParam param, ArrayList<Subtitle> list) throws IllegalStateException {
        synchronized (this) {
            if (getStatus()) {
                Log.d(TAG, "startPlayer : player is running, pls waiting player stop");
                throw new IllegalStateException();
            }

            if (mPlayer != null) {
                mPlayer.init();
                try {
                    mPlayer.setDataSource(videoPath);
                } catch (Exception e) {
                    Log.e(TAG, "Exception " + e.toString());
                    return;
                }
                mVideoWidth = mPlayer.getVideoWidth();
                mVideoHeight = mPlayer.getVideoHeight();
                if (param != null) {
                    param.setCanvasSize(mVideoWidth, mVideoHeight);
                }
                setSubtitleParam(param, list);
                setPipRectCoordinate(mRect);
                startRecorder_l(outputPath, mVideoWidth, mVideoHeight);
                if (mRenderer != null) {
                    ((XMPlayerRenderer) mRenderer).prepareVideoSurface();
                    ((XMPlayerRenderer) mRenderer).onVideoSizeChanged(mVideoWidth, mVideoHeight);
                }
                calculateVideoAspectRatio(mVideoWidth, mVideoHeight);
            }

            setStatus(true);
        }
    }

    private void stopPlayer() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
        }

        mVideoWidth = 0;
        mVideoHeight = 0;
        setPipRectCoordinate(mRect);
        if (mCameraPreviewHeight != 0 && mRect != null) {
            float aspectRatio = (mRect.right - mRect.left) / (mRect.top - mRect.bottom);
            calculateVideoAspectRatio((int) (aspectRatio * mCameraPreviewHeight), mCameraPreviewHeight);
        }
    }

    @Override
    public void stop() {
        super.stop();
        setSubThreadAbort(true);
        if (mSubThread != null) {
            try {
                mSubThread.join();
                mSubThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        synchronized (this) {
            stopPlayer();
            //stopCameraPreview();
        }
    }

    private void releaseSubtitleParser() {
        if (mSubtitleParser != null) {
            mSubtitleParser.release();
            mSubtitleParser = null;
        }
    }

    private void releaseCamera() {
        if (mCamera != null)
            mCamera.releaseInstance();
    }

    private void releasePlayer() {
        if(mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
    }

    @Override
    public void release() {
        super.release();
        setSubThreadAbort(true);
        if (mSubThread != null) {
            try {
                mSubThread.join();
                mSubThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        synchronized (this) {
            releaseSubtitleParser();
            releasePlayer();
            releaseCamera();
        }
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
            ((XMPlayerRenderer) mRenderer).cleanRunOnSetupCamera();

        if (mCamera != null)
            mCamera.onResume();

        mGLSurfaceView.clearAnimation();
        mGLSurfaceView.setVisibility(View.VISIBLE);
    }

    /*stop camera preview*/
    public void stopCameraPreview() {
        mRect = null;
        mCameraPreviewWidth = 0;
        mCameraPreviewHeight = 0;
        mCameraOuptutFps = 0;

        if(mRenderer != null) {
            ((XMPlayerRenderer) mRenderer).cleanRunOnSetupCamera();
            ((XMPlayerRenderer) mRenderer).releaseCamera();
        }

        mGLSurfaceView.setVisibility(View.GONE);
        mGLSurfaceView.clearAnimation();

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

        if (!getStatus()) {
            if (mRect != null) {
                float aspectRatio = (mRect.right - mRect.left) / (mRect.top - mRect.bottom);
                calculateVideoAspectRatio((int) (aspectRatio * mCameraPreviewHeight), mCameraPreviewHeight);
            }
        }

        int[] range = new int[2];
        camera.getParameters().getPreviewFpsRange(range);
        mCameraOuptutFps = range[0] / 1000;
        if(range[1] != range[0])
        {
            Log.w(TAG, "camera output fps is dynamic, range from " + range[0] + " to " + range[1]);
            mCameraOuptutFps = 15;
        }
        Log.i(TAG, "PreviewSize = " + mCameraPreviewWidth + "x" + mCameraPreviewHeight + " mCameraOuptutFps " + mCameraOuptutFps);

        if (mRenderer != null)
            ((XMPlayerRenderer) mRenderer).setUpCamera(camera, degrees, flipHorizontal, flipVertical);
    }

    public void setPipRectCoordinate(Rect rect) {
        if (rect == null) {
            Log.e(TAG, "Rect is null");
            return;
        }

        mRect = rect;
        float[] buffer = new float[4];
        buffer[0] = rect.left;
        buffer[1] = rect.bottom;
        buffer[2] = rect.right;
        buffer[3] = rect.top;

        if (mRenderer != null) {
            float aspectRatio = (buffer[2] - buffer[0]) / (buffer[3] - buffer[1]);
            ((XMPlayerRenderer) mRenderer).setCameraOutputAspectRatio(aspectRatio);
        }

        if (mVideoWidth != 0) {
            buffer[2] = (mVideoHeight * (buffer[2] - buffer[0]) + mVideoWidth * buffer[0]) / mVideoWidth;
        }
        if(mRenderer != null) {
            ((XMPlayerRenderer) mRenderer).setPipRectCoordinate(buffer);
        }
    }

    private void calculateVideoAspectRatio(int videoWidth, int videoHeight) {
        if (videoWidth > 0 && videoHeight > 0) {
            videoAspectRatio = (float) videoWidth / videoHeight;
        }

        //mGLSurfaceView.requestLayout();
        //mGLSurfaceView.invalidate();
    }

    private void startRecorder_l(String outputPath, int outputWidth, int outputHeight) {
        if (mRecorder != null) {
            Log.i(TAG, "startRecorder_l outputPath " + outputPath);
            mImageReaderPrepared = false;
            mXMMediaRecorderPrepared = false;
            checkRendererStatus();

            HashMap<String, String> config = new HashMap<String, String>();
            config.put("width", String.valueOf(outputWidth));
            config.put("height", String.valueOf(outputHeight));
            config.put("bit_rate", String.valueOf(mPlayer.getVideoBitRate()));
            config.put("fps", String.valueOf(mPlayer.getVideoFps()));
            config.put("gop_size", String.valueOf((int) (params.gop_size * mPlayer.getVideoFps())));
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

    private void invisibleSubtitle() {
        if (mRenderer != null) {
            ((XMPlayerRenderer) mRenderer).stopSubtitle();
        }
    }

    private void initSubThread() {
        setSubThreadAbort(false);
        mSubIndex = 0;
        if (mSubtitleList != null && mSubIndex < mSubtitleList.size()) {
            mSubtitle = mSubtitleList.get(mSubIndex);
        }
    }

    private synchronized void setSubThreadAbort(boolean abort) {
        mSubThreadAbort = abort;
    }

    private synchronized boolean getSubThreadAbort() {
        return mSubThreadAbort;
    }

    class SubtitleThread extends Thread {
        @Override
        public void run() {
            int prevStartTime = -1;
            int prevEndTime = -1;
            while (!getSubThreadAbort()) {
                if (mSubtitle != null) {
                    if (getCurrentPosition() >= mSubtitle.start_time && getCurrentPosition() <= mSubtitle.end_time) {
                        if (prevStartTime != mSubtitle.start_time) {
                            prevStartTime = mSubtitle.start_time;
                            drawSubtitle(mSubtitle.str, mSubtitle.headImagePath);
                        }
                    } else if (getCurrentPosition() >= mSubtitle.end_time) {
                        if (prevEndTime != mSubtitle.end_time) {
                            prevEndTime = mSubtitle.end_time;
                            invisibleSubtitle();
                            mSubIndex++;
                            if (mSubIndex < mSubtitleList.size()) {
                                mSubtitle = mSubtitleList.get(mSubIndex);
                            } else {
                                Log.i(TAG, "mSubIndex is invalid");
                                break;
                            }
                        }
                    }
                } else {
                    Log.i(TAG, "mSubtitle null");
                    break;
                }

                try {
                    Thread.sleep(SLEEP_TIME, 0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            Log.i(TAG, "SubtitleThread exit");
        }
    }

    private MediaPlayer.OnInfoListener mOnInfoListener =
            new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer mp, int what, int extra) {
                    Log.i(TAG, "what " + what + ", extra " + extra);
                    switch (what) {
                        case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                            initSubThread();
                            mSubThread = new SubtitleThread();
                            mSubThread.start();

                            enableGPUCopier(true);
                            break;
                        default:
                            break;
                    }
                    return true;
                }
            };

    private MediaPlayer.OnCompletionListener mOnCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.i(TAG, "onCompletion");
                    stop();
                }
            };

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
