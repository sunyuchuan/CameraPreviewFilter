package com.xmly.media.video.view;

import android.util.Log;

import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.gles.utils.XMFilterType;

import java.util.ArrayList;

/**
 * Created by sunyc on 19-4-2.
 */

public class SpeedControlCallback implements XMDecoder.FrameCallback {
    private static final String TAG = "SpeedControlCallback";
    private static final int MAX_QUEUE_SIZE = 3;
    private static final int POLLING_TIME = 10; //ms
    private static final boolean CHECK_SLEEP_TIME = false;
    private static final long ONE_MILLION = 1000000L;
    private XMDecoderRenderer mRenderer = null;
    private XMMediaRecorder mRecorder = null;
    private long mPrevPresentUsec;
    private long mPrevMonoUsec;
    private long mFixedFrameDurationUsec;
    private boolean mLoopReset;
    private int mPrevFrameNum;
    private volatile boolean mIsStopRequested = false;
    private long mCurrentPosition = 0l;

    private XMFilterType mType = XMFilterType.NONE;
    private int index = 0;
    private long mStartTime = 0l;
    private long mEndTime = 0l;
    private ArrayList<XMDecoderView.Segment> mSegmentArray = null;
    private final XMFilterType[] filters = new XMFilterType[] {
            XMFilterType.FILTER_FISSION,
            XMFilterType.FILTER_SKETCH,
            XMFilterType.FILTER_SEPIA,
            XMFilterType.FILTER_INVERT,
            XMFilterType.FILTER_VIGNETTE,
            XMFilterType.FILTER_LAPLACIAN,
            XMFilterType.FILTER_GLASS_SPHERE,
            XMFilterType.FILTER_CRAYON,
            XMFilterType.FILTER_MIRROR,
            XMFilterType.FILTER_CROSSHATCH
    };

    public void init() {
        mPrevPresentUsec = 0l;
        mPrevMonoUsec = 0l;
        mLoopReset = false;
        mPrevFrameNum = -1;
        index = 0;
        mSegmentArray = null;
        mType = XMFilterType.NONE;
        stop(false);
    }

    public void setSegmentArray(ArrayList<XMDecoderView.Segment> list) {
        mSegmentArray = list;
    }

    public void setRecorder(XMMediaRecorder recorder) {
        mRecorder = recorder;
    }

    public void setRenderer(XMDecoderRenderer renderer) {
        mRenderer = renderer;
    }

    public void setFixedPlaybackRate(int fps) {
        mFixedFrameDurationUsec = (ONE_MILLION / fps);
    }

    @Override
    public void preRender(long presentationTimeUsec) {
        if (mPrevMonoUsec == 0) {
            mPrevMonoUsec = System.nanoTime() / 1000;
            mPrevPresentUsec = presentationTimeUsec;
        } else {
            long frameDelta;
            if (mLoopReset) {
                mPrevPresentUsec = presentationTimeUsec - ONE_MILLION / 30;
                mLoopReset = false;
            }

            if (mFixedFrameDurationUsec != 0) {
                frameDelta = mFixedFrameDurationUsec;
            } else {
                frameDelta = presentationTimeUsec - mPrevPresentUsec;
            }

            if (frameDelta < 0) {
                Log.w(TAG, "Weird, video times went backward");
                frameDelta = 0;
            } else if (frameDelta == 0) {
                Log.i(TAG, "Warning: current frame and previous frame had same timestamp");
            } else if (frameDelta > 10 * ONE_MILLION) {
                Log.i(TAG, "Inter-frame pause was " + (frameDelta / ONE_MILLION) +
                        "sec, capping at 5 sec");
                frameDelta = 5 * ONE_MILLION;
            }

            long desiredUsec = mPrevMonoUsec + frameDelta;  // when we want to wake up
            long nowUsec = System.nanoTime() / 1000;
            while (nowUsec < (desiredUsec - 100)) {
                long sleepTimeUsec = desiredUsec - nowUsec;
                if (sleepTimeUsec > 500000) {
                    sleepTimeUsec = 500000;
                }

                try {
                    if (CHECK_SLEEP_TIME) {
                        long startNsec = System.nanoTime();
                        Thread.sleep(sleepTimeUsec / 1000, (int) (sleepTimeUsec % 1000) * 1000);
                        long actualSleepNsec = System.nanoTime() - startNsec;
                        Log.d(TAG, "sleep=" + sleepTimeUsec + " actual=" + (actualSleepNsec/1000) +
                                " diff=" + (Math.abs(actualSleepNsec / 1000 - sleepTimeUsec)) +
                                " (usec)");
                    } else {
                        Thread.sleep(sleepTimeUsec / 1000, (int) (sleepTimeUsec % 1000) * 1000);
                    }
                } catch (InterruptedException ie) {
                }

                nowUsec = System.nanoTime() / 1000;
            }

            mPrevMonoUsec += frameDelta;
            mPrevPresentUsec += frameDelta;
        }
    }

    @Override
    public void stop(boolean stop) {
        synchronized (this) {
            mIsStopRequested = stop;
        }
    }

    public long getCurrentPosition() {
            return mCurrentPosition;
    }

    private void setCurrentPosition(long pos) {
        synchronized (this) {
            mCurrentPosition = pos;
        }
    }

    @Override
    public void offlineRender(long position) {
        setCurrentPosition(position);
        if (mPrevFrameNum == -1) {
            mPrevFrameNum = mRenderer.getDrawFrameNums();
            index = 0;
        } else {
            int curNum = mRenderer.getDrawFrameNums();
            while (curNum <= mPrevFrameNum || mRecorder.queue_sizes() > MAX_QUEUE_SIZE) {
                try {
                    Thread.sleep(POLLING_TIME, 0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                curNum = mRenderer.getDrawFrameNums();
                if (mIsStopRequested) {
                    stop(false);
                    Log.i(TAG, "stop is requested, break");
                    break;
                }
            }
            mPrevFrameNum = curNum;

            XMFilterType preType = mType;
            if (mSegmentArray != null && index < mSegmentArray.size()) {
                mStartTime = (long) mSegmentArray.get(index).start;
                mEndTime = (long) mSegmentArray.get(index).end;
            }

            if (position >= mStartTime && position <= mEndTime) {
                mType = filters[index%filters.length];
            } else if (position >= mEndTime) {
                mType = XMFilterType.NONE;
                index ++;
            } else {
                mType = XMFilterType.NONE;
            }

            if (mType != preType) {
                mRenderer.setFilter(mType);
            }
        }
    }

    @Override public void postRender() {
    }

    @Override
    public void loopReset() {
        mLoopReset = true;
    }
}


