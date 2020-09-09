package com.xmly.media.camera.preview.listener;

/**
 * 相机录制回调
 * Created by sunyc on 19-7-26.
 */

public interface onCameraRecorderListener {
    //编码器初始化完成
    void onRecorderPrepared();
    //录制开始
    void onRecorderStarted();
    //录制停止
    void onRecorderStopped();
    //录制失败
    void onRecorderError();
}
