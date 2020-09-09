package com.xmly.media.camera.preview.listener;

/**
 * Created by sunyc on 19-8-1.
 */

public interface onCameraEngineListener {
    //打开相机
    void onCameraOpened();
    //关闭相机
    void onCameraClosed();
    //相机打开失败
    void onCameraError();
}
