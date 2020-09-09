package com.xmly.media.camera.view.recorder;

/**
 * Created by sunyc on 18-10-26.
 */

public interface IXMCameraRecorderListener {
    void onImageReaderPrepared();
    void onRecorderPrepared();
    void onRecorderStarted();
    void onRecorderStopped();
    void onRecorderError();
    void onPreviewStarted();
    void onPreviewStopped();
    void onPreviewError();
}
