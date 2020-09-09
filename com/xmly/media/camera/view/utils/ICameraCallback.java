package com.xmly.media.camera.view.utils;

import android.hardware.Camera;

/**
 * Created by sunyc on 19-4-10.
 */

public interface ICameraCallback {
    void setUpCamera(final Camera camera, final int degrees, final boolean flipHorizontal,
                            final boolean flipVertical);
}
