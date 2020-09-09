package com.xmly.media.camera.preview.recorder;

import android.os.Handler;
import android.os.Message;

import java.lang.ref.WeakReference;

/**
 * 相机录制线程Handler
 * Created by sunyc on 19-7-24.
 */

public class CameraRecorderHandler extends Handler {
    //开始录制
    public static final int MSG_START_RECORDING = 0x001;
    //停止录制
    public static final int MSG_STOP_RECORDING = 0x002;
    //帧有效
    public static final int MSG_FRAME_AVAILABLE = 0x003;
    //退出线程
    public static final int MSG_THREAD_QUIT = 0x004;

    private WeakReference<CameraRecorderThread> mWeakRecorderThread = null;

    public CameraRecorderHandler(CameraRecorderThread thread) {
        mWeakRecorderThread = new WeakReference<CameraRecorderThread>(thread);
    }

    /**
     * 释放
     */
    public void release() {
        if (mWeakRecorderThread != null) {
            mWeakRecorderThread.clear();
            mWeakRecorderThread = null;
        }
    }

    @Override
    public void handleMessage(Message msg) {
        if (mWeakRecorderThread == null || mWeakRecorderThread.get() == null) {
            return;
        }

        CameraRecorderThread thread = mWeakRecorderThread.get();
        switch (msg.what) {
            //开始录制
            case MSG_START_RECORDING:
                thread.handleStartRecord();
                break;
            //停止录制
            case MSG_STOP_RECORDING:
                thread.handleStopRecord();
                break;
            //帧有效, 开始从gles读取数据
            case MSG_FRAME_AVAILABLE:
                thread.handleFrameAvailable(msg.arg1);
                break;
            //退出线程
            case MSG_THREAD_QUIT:
                thread.handleStopThread();
                break;
            default:
                throw new IllegalStateException("Can not handle message what is: " + msg.what);
        }
    }
}
