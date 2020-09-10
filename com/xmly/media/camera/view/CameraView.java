package com.xmly.media.camera.view;

/**
 * Created by sunyc on 18-11-20.
 */

public class CameraView {
    private static final String TAG = "CameraView";
    /*The callback interface that the APP needs to set*/
    public interface ICameraViewListener {
        /*
         *The video recording process has started working
         */
        void onRecorderStarted();

        /*
        *The recording process has stopped working,
        *and the recorded video has been generated.
        *The video file was generated successfully.
        */
        void onRecorderStopped();

        void onRecorderError();

        /*Camera preview has started*/
        void onPreviewStarted();

        /*Camera preview has stopped*/
        void onPreviewStopped();

        void onPreviewError();
    }
}
