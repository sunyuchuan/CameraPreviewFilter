package com.xmly.media.camera.preview.render;

import android.os.Handler;
import android.os.Message;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;

/**
 * 渲染线程Handler
 * Created by sunyc on 19-7-24.
 */
public class CameraRenderHandler extends Handler {
    // Surface创建
    public static final int MSG_SURFACE_CREATED = 0x001;
    // Surface改变
    public static final int MSG_SURFACE_CHANGED = 0x002;
    // Surface销毁
    public static final int MSG_SURFACE_DESTROYED = 0x003;
    // 渲染
    public static final int MSG_REQUEST_RENDER = 0x004;
    // 开始录制
    public static final int MSG_START_RECORDING = 0x005;
    // 停止录制
    public static final int MSG_STOP_RECORDING = 0x006;
    // 预览帧回调
    public static final int MSG_PREVIEW_CALLBACK = 0x007;

    private WeakReference<CameraRenderThread> mWeakRenderThread = null;

    public CameraRenderHandler(CameraRenderThread thread) {
        super(thread.getLooper());
        mWeakRenderThread = new WeakReference<CameraRenderThread>(thread);
    }

    /**
     * 释放
     */
    public void release() {
        if (mWeakRenderThread != null) {
            mWeakRenderThread.clear();
            mWeakRenderThread = null;
        }
    }

    @Override
    public void handleMessage(Message msg) {
        if (mWeakRenderThread == null || mWeakRenderThread.get() == null) {
            return;
        }

        CameraRenderThread thread = mWeakRenderThread.get();
        switch (msg.what) {
            // onSurfaceCreated
            case MSG_SURFACE_CREATED:
                thread.onSurfaceCreated((SurfaceHolder)msg.obj);
                break;
            // onSurfaceChanged
            case MSG_SURFACE_CHANGED:
                thread.onSurfaceChanged(msg.arg1, msg.arg2);
                break;
            // onSurfaceDestroyed;
            case MSG_SURFACE_DESTROYED:
                thread.onSurfaceDestroyed();
                break;
            // 渲染一次
            case MSG_REQUEST_RENDER:
                thread.onDrawFrame();
                break;
            // 开始录制
            case MSG_START_RECORDING:
                thread.onStartRecording();
                break;
            // 停止录制
            case MSG_STOP_RECORDING:
                thread.onStopRecording();
                break;
            // 预览帧回调
            case MSG_PREVIEW_CALLBACK:
                thread.onPreviewCallback((byte[])msg.obj);
                break;
            default:
                throw new IllegalStateException("Can not handle message what is: " + msg.what);
        }
    }
}
