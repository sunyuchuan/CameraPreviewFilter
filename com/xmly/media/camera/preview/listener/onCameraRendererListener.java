package com.xmly.media.camera.preview.listener;

/**
 * Created by sunyc on 19-7-31.
 */

public interface onCameraRendererListener {
    //渲染器surface状态
    void onSurfaceCreated();
    void onSurfaceChanged();
    void onSurfaceDestroyed();
    //渲染器开始渲染
    void onRendererStarted();
    //完成一次预览帧绘制
    void onFrameAvailable(int texId);
}
