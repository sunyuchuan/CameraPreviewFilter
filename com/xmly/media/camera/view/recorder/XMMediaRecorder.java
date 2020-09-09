package com.xmly.media.camera.view.recorder;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.HashMap;

import tv.danmaku.ijk.media.player.IjkLibLoader;
import tv.danmaku.ijk.media.player.annotations.AccessedByNative;
import tv.danmaku.ijk.media.player.annotations.CalledByNative;

/**
 * Created by sunyc on 18-10-26.
 */

public class XMMediaRecorder {
    private static final String TAG = "XMMediaRecorder";
    private static boolean mIsLibLoaded = false;
    private EventHandler mEventHandler;
    private IXMCameraRecorderListener mListener = null;
    @AccessedByNative
    private long mNativeXMMediaRecorder = 0;
    private boolean mUseSoftEncoder = false;
    private boolean mAudioEnable = false;
    private boolean mVideoEnable = false;

    private static XMMediaRecorder mRecorder = null;
    private static final int RECORDER_NOP = 0;
    private static final int RECORDER_PREPARED = 1;
    private static final int RECORDER_COMPLETED = 2;
    private static final int RECORDER_ERROR = 100;
    private static final int RECORDER_INFO = 200;

    private static final int MR_MSG_ERROR = 100;
    private static final int MR_MSG_STARTED = 200;
    private static final int MR_MSG_STOPPED = 300;

    private static final IjkLibLoader sLocalLibLoader = new IjkLibLoader() {
        @Override
        public void loadLibrary(String libName) throws UnsatisfiedLinkError, SecurityException {
            String ABI = Build.CPU_ABI;
            Log.i(TAG, "ABI " + ABI + " libName " +libName);
            System.loadLibrary(libName + "-" + ABI);
        }
    };

    private static void loadLibrariesOnce(IjkLibLoader libLoader) {
        synchronized (XMMediaRecorder.class) {
            if (!mIsLibLoaded) {
                if (libLoader == null)
                    libLoader = sLocalLibLoader;

                libLoader.loadLibrary("ijkffmpeg");
                libLoader.loadLibrary("ijksdl");
                libLoader.loadLibrary("ijkplayer");
                libLoader.loadLibrary("xmrecorder");
                mIsLibLoaded = true;
            }
        }
    }

    public static XMMediaRecorder getInstance(boolean useSoftEncoder, boolean hasAudio, boolean hasVideo) {
        if(mRecorder != null)
            return mRecorder;

        mRecorder = new XMMediaRecorder(useSoftEncoder, hasAudio, hasVideo);
        return mRecorder;
    }

    private void initRecorder() {
        Looper looper;
        if ((looper = Looper.myLooper()) != null) {
            mEventHandler = new XMMediaRecorder.EventHandler(this, looper);
        } else if ((looper = Looper.getMainLooper()) != null) {
            mEventHandler = new XMMediaRecorder.EventHandler(this, looper);
        } else {
            mEventHandler = null;
        }

        native_setup(new WeakReference<XMMediaRecorder>(this), mUseSoftEncoder, mAudioEnable, mVideoEnable);
    }

    public XMMediaRecorder(boolean useSoftEncoder, boolean hasAudio, boolean hasVideo)
    {
        loadLibrariesOnce(sLocalLibLoader);
        mUseSoftEncoder = useSoftEncoder;
        mAudioEnable = hasAudio;
        mVideoEnable = hasVideo;
        initRecorder();
    }

    public XMMediaRecorder(IjkLibLoader libLoader, boolean useSoftEncoder, boolean hasAudio, boolean hasVideo)
    {
        loadLibrariesOnce(libLoader);
        mUseSoftEncoder = useSoftEncoder;
        mAudioEnable = hasAudio;
        mVideoEnable = hasVideo;
        initRecorder();
    }

    public void glMapBufferRange_put(int target, int offset, int length,
                                     int access, int w, int h,
                                     int pixelStride, int rowPadding, int format) {
        _glMapBufferRange_put(target, offset, length, access, w, h, pixelStride, rowPadding, format);
    }

    public boolean setConfigParams(HashMap<String, String> params) {
        return _setConfigParams(params);
    }

    public void setListener(IXMCameraRecorderListener l)
    {
        mListener = l;
    }

    public void prepareAsync() {
        _prepareAsync();
    }

    public void start() {
        _start();
    }

    public void stop() {
        _stop();
    }

    public void release() {
        _release();
        mEventHandler = null;
        mListener = null;
        mRecorder = null;
    }

    public int queue_sizes() {
        return _queue_sizes();
    }

    public void put(byte[] data, int w, int h, int pixelStride, int rowPadding,
                    int rotate_degrees, boolean flipHorizontal, boolean flipVertical) {
        if(data != null && w > 0 && h > 0)
            _put(data, w, h, pixelStride, rowPadding, rotate_degrees, flipHorizontal, flipVertical);
    }

    public native void glReadPixels(int x, int y, int width, int height, int format, int type);
    public native void NV21toABGR(byte[] yuv, int width, int height, byte[] gl_out);
    private native void _glMapBufferRange_put(int target, int offset, int length, int access, int w, int h, int pixelStride, int rowPadding, int format);
    private native boolean _setConfigParams(HashMap<String, String> params);
    private native void _start();
    private native void _stop();
    private native int _queue_sizes();
    private native void _put(byte[] data, int w, int h, int pixelStride, int rowPadding, int rotate_degrees, boolean flipHorizontal, boolean flipVertical);
    private native void native_setup(Object CameraRecoder_this, boolean useSoftEncoder, boolean audioEnable, boolean videoEnable);
    public native void native_finalize();
    private native void _reset();
    private native void _release();
    private native void _prepareAsync();

    @CalledByNative
    private static void postEventFromNative(Object weakThiz, int what,
                                            int arg1, int arg2, Object obj) {
        if (weakThiz == null)
            return;

        XMMediaRecorder recorder = (XMMediaRecorder) ((WeakReference) weakThiz).get();
        if (recorder == null) {
            return;
        }

        if (recorder.mEventHandler != null) {
            Message m = recorder.mEventHandler.obtainMessage(what, arg1, arg2, obj);
            recorder.mEventHandler.sendMessage(m);
        }
    }

    private final void onRecorderPrepared() {
        if(mListener != null)
            mListener.onRecorderPrepared();
    }

    private final void onRecorderStarted() {
        if(mListener != null)
            mListener.onRecorderStarted();
    }

    private final void onRecorderStopped() {
        if(mListener != null)
            mListener.onRecorderStopped();
    }

    private final void onRecorderError() {
        if(mListener != null)
            mListener.onRecorderError();
    }

    private static class EventHandler extends Handler {
        private final WeakReference<XMMediaRecorder> mWeakRecoder;

        public EventHandler(XMMediaRecorder recorder, Looper looper) {
            super(looper);
            mWeakRecoder = new WeakReference<XMMediaRecorder>(recorder);
        }

        @Override
        public void handleMessage(Message msg) {
            XMMediaRecorder recoder = mWeakRecoder.get();
            if (recoder == null || recoder.mNativeXMMediaRecorder == 0) {
                return;
            }

            switch (msg.what) {
                case RECORDER_NOP:
                    Log.i(TAG, "msg_loop nop");
                    break;
                case RECORDER_PREPARED:
                    recoder.onRecorderPrepared();
                    return;
                case RECORDER_INFO:
                    if(msg.arg1 == MR_MSG_STARTED)
                        recoder.onRecorderStarted();
                    else if(msg.arg1 == MR_MSG_STOPPED)
                        recoder.onRecorderStopped();
                    break;
                case RECORDER_COMPLETED:
                    Log.i(TAG, "RECORDER_COMPLETED");
                    return;
                case RECORDER_ERROR:
                    Log.i(TAG, "RECORDER_ERROR");
                    recoder.onRecorderError();
                    return;
                default:
                    Log.i(TAG, "Unknown message type " + msg.what);
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        Log.i(TAG, "finalize");
        try {
            native_finalize();
        } finally {
            super.finalize();
        }
    }
}
