package com.xmly.media.video.view;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by sunyc on 19-4-2.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class XMDecoder implements Runnable {
    private static final String TAG = "XMDecoder";
    private static final boolean VERBOSE = false;
    private static final int MSG_DECODER_STARTED = 0;
    private static final int MSG_DECODER_STOPPED = 1;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private volatile boolean mIsStopRequested;
    private String mStreamPath = null;
    private Surface mOutputSurface = null;
    private FrameCallback mFrameCallback;
    private DecoderFeedback mFeedback;
    private Thread mThread;
    private LocalHandler mLocalHandler;
    private final Object mObjectLock = new Object();
    private volatile boolean isRunning = false;
    private volatile boolean mLoop = false;

    ProgressListener mProgressListener = new ProgressListener() {
        @Override
        public void onProgress(int progress) {
            Log.d(TAG, "progress " + progress);
        }
    };

    OnCompletionListener mOnCompletionListener = new OnCompletionListener() {
        @Override
        public void onCompletion() {
            Log.d(TAG, "onCompletion");
        }
    };

    private int mVideoWidth;
    private int mVideoHeight;
    private int mVideoFps = 0;
    private long mDuration;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public XMDecoder(FrameCallback frameCallback, DecoderFeedback feedback)
            throws IOException {
        mFrameCallback = frameCallback;
        mFeedback = feedback;
        mLocalHandler = new LocalHandler();
    }

    public void setDataSource(String streamPath, Surface outputSurface)
            throws IOException {
        mStreamPath = streamPath;
        mOutputSurface = outputSurface;
        synchronized (mObjectLock) {
            mIsStopRequested = false;
        }
        MediaExtractor extractor = null;
        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(mStreamPath);
            int trackIndex = selectTrack(extractor);
            if (trackIndex < 0) {
                throw new RuntimeException("No video track found in " + mStreamPath);
            }
            extractor.selectTrack(trackIndex);

            MediaFormat format = extractor.getTrackFormat(trackIndex);
            mVideoWidth = format.getInteger(MediaFormat.KEY_WIDTH);
            mVideoHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
            mVideoFps = format.getInteger(MediaFormat.KEY_FRAME_RATE);
            mDuration = format.getLong(MediaFormat.KEY_DURATION);

            if (VERBOSE) {
                Log.d(TAG, "Video size is " + mVideoWidth + "x" + mVideoHeight);
            }
        } finally {
            if (extractor != null) {
                extractor.release();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void doExtract(MediaExtractor extractor, int trackIndex, MediaCodec decoder,
                           FrameCallback frameCallback) {
        final int TIMEOUT_USEC = 10000;
        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        int inputChunk = 0;
        long firstInputTimeNsec = -1;

        boolean outputDone = false;
        boolean inputDone = false;
        while (!outputDone) {
            if (VERBOSE) Log.d(TAG, "loop");
            if (mIsStopRequested) {
                Log.d(TAG, "Stop requested");
                return;
            }

            if (!inputDone) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    if (firstInputTimeNsec == -1) {
                        firstInputTimeNsec = System.nanoTime();
                    }
                    ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                    int chunkSize = extractor.readSampleData(inputBuf, 0);
                    if (chunkSize < 0) {
                        // End of stream -- send empty frame with EOS flag set.
                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        if (VERBOSE) Log.d(TAG, "sent input EOS");
                    } else {
                        if (extractor.getSampleTrackIndex() != trackIndex) {
                            Log.w(TAG, "WEIRD: got sample from track " +
                                    extractor.getSampleTrackIndex() + ", expected " + trackIndex);
                        }
                        long presentationTimeUs = extractor.getSampleTime();
                        decoder.queueInputBuffer(inputBufIndex, 0, chunkSize,
                                presentationTimeUs, 0 /*flags*/);
                        if (VERBOSE) {
                            Log.d(TAG, "submitted frame " + inputChunk + " to dec, size=" +
                                    chunkSize);
                        }
                        inputChunk++;
                        extractor.advance();
                    }
                } else {
                    if (VERBOSE) Log.d(TAG, "input buffer not available");
                }
            }

            if (!outputDone) {
                int decoderStatus = decoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) Log.d(TAG, "no output from decoder available");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not important for us, since we're using Surface
                    if (VERBOSE) Log.d(TAG, "decoder output buffers changed");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG, "decoder output format changed: " + newFormat);
                } else if (decoderStatus < 0) {
                    throw new RuntimeException(
                            "unexpected result from decoder.dequeueOutputBuffer: " +
                                    decoderStatus);
                } else { // decoderStatus >= 0
                    if (firstInputTimeNsec != 0) {
                        // Log the delay from the first buffer of input to the first buffer
                        // of output.
                        long nowNsec = System.nanoTime();
                        Log.d(TAG, "startup lag " + ((nowNsec-firstInputTimeNsec) / 1000000.0) + " ms");
                        firstInputTimeNsec = 0;
                    }
                    boolean doLoop = false;
                    if (VERBOSE) Log.d(TAG, "surface decoder given buffer " + decoderStatus +
                            " (size=" + mBufferInfo.size + ")");
                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        if (VERBOSE) Log.d(TAG, "output EOS");
                        if (mLoop) {
                            doLoop = true;
                        } else {
                            outputDone = true;
                            mOnCompletionListener.onCompletion();
                        }
                    }

                    boolean doRender = (mBufferInfo.size != 0);
                    if (doRender && frameCallback != null) {
                        //frameCallback.preRender(mBufferInfo.presentationTimeUs);
                        frameCallback.offlineRender(mBufferInfo.presentationTimeUs / 1000);
                        mProgressListener.onProgress((int) (100 * ((float) mBufferInfo.presentationTimeUs / (float) mDuration)));
                    }
                    decoder.releaseOutputBuffer(decoderStatus, doRender);
                    if (doRender && frameCallback != null) {
                        frameCallback.postRender();
                    }

                    if (doLoop) {
                        Log.d(TAG, "Reached EOS, looping");
                        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                        inputDone = false;
                        decoder.flush();    // reset decoder state
                        frameCallback.loopReset();
                    }
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private static int selectTrack(MediaExtractor extractor) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                if (VERBOSE) {
                    Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
                }
                return i;
            }
        }

        return -1;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void decode() throws IOException {
        MediaExtractor extractor = null;
        MediaCodec decoder = null;

        if (mStreamPath == null) {
            throw new NullPointerException("source file path is null");
        }

        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(mStreamPath);
            int trackIndex = selectTrack(extractor);
            if (trackIndex < 0) {
                throw new RuntimeException("No video track found in " + mStreamPath);
            }
            extractor.selectTrack(trackIndex);

            MediaFormat format = extractor.getTrackFormat(trackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, mOutputSurface, null, 0);
            decoder.start();

            doExtract(extractor, trackIndex, decoder, mFrameCallback);
        } finally {
            if (decoder != null) {
                decoder.stop();
                decoder.release();
                decoder = null;
            }
            if (extractor != null) {
                extractor.release();
                extractor = null;
            }
        }
    }

    @Override
    public void run() {
        try {
            mLocalHandler.sendMessage(
                    mLocalHandler.obtainMessage(MSG_DECODER_STARTED, mFeedback));
            decode();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        } finally {
            synchronized (mObjectLock) {
                isRunning = false;
                mObjectLock.notifyAll();
            }

            mLocalHandler.sendMessage(
                    mLocalHandler.obtainMessage(MSG_DECODER_STOPPED, mFeedback));
        }
    }

    public void start(String name) {
        synchronized (mObjectLock) {
            if (isRunning) {
                return;
            }

            mThread = new Thread(this, name);
            mThread.start();
            isRunning = true;
        }
    }

    public void waitForStop() {
        synchronized (mObjectLock) {
            while (isRunning) {
                try {
                    mObjectLock.wait();
                } catch (InterruptedException ie) {
                    // discard
                }
            }
        }
    }

    public void setProgressListener(ProgressListener listener) {
        synchronized (mObjectLock) {
            mProgressListener = listener;
        }
    }

    public void setOnCompletionListener(OnCompletionListener listener) {
        synchronized (mObjectLock) {
            mOnCompletionListener = listener;
        }
    }

    public int getVideoWidth() {
        return mVideoWidth;
    }

    public int getVideoHeight() {
        return mVideoHeight;
    }

    public int getVideoFps() {
        Log.i(TAG, "mVideoFps " + mVideoFps);
        return mVideoFps < 1 ? 25 : mVideoFps;
    }

    public void setLoopMode(boolean loopMode) {
        synchronized (mObjectLock) {
            mLoop = loopMode;
        }
    }

    public void requestStop() {
        synchronized (mObjectLock) {
            mIsStopRequested = true;
        }
        mFrameCallback.stop(true);
    }

    public interface ProgressListener {
        void onProgress(int progress);
    }

    public interface OnCompletionListener {
        void onCompletion();
    }

    public interface DecoderFeedback {
        void decodeStarted();
        void decodeStopped();
    }

    public interface FrameCallback {
        void stop(boolean stop);
        void preRender(long presentationTimeUsec);
        void offlineRender(long ms);
        void postRender();
        void loopReset();
    }

    private static class LocalHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
            DecoderFeedback fb = (DecoderFeedback) msg.obj;
            switch (what) {
                case MSG_DECODER_STARTED:
                    fb.decodeStarted();
                    break;
                case MSG_DECODER_STOPPED:
                    fb.decodeStopped();
                    break;
                default:
                    throw new RuntimeException("Unknown msg " + what);
            }
        }
    }
}
