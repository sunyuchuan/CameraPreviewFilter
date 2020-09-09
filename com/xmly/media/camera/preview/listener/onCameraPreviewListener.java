package com.xmly.media.camera.preview.listener;

/**
 * 相机预览回调
 * Created by sunyc on 19-7-26.
 */

public interface onCameraPreviewListener {
    //预览已经开始
    void onPreviewStarted();
    //预览已经停止
    void onPreviewStopped();
    //预览失败
    void onPreviewError();
}
