package com.xmly.media.gles.utils;

import android.content.Context;
import android.os.Environment;

/**
 * Created by sunyc on 18-11-13.
 */

public class GPUImageParams {
    public static Context context;

    public static String videoPath = Environment.getExternalStorageDirectory().getPath();
    public static String videoName = "GPUImage_test.mp4";

    public static int beautyLevel = 5;

    public GPUImageParams() {

    }
}
