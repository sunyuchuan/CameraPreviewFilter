package com.xmly.media.gles.filter;

import android.opengl.GLES20;

import com.xmly.media.gles.utils.OpenGlUtils;
import tv.danmaku.ijk.media.player.R;

public class GPUImageCameraInputFilter extends GPUImageOESInputFilter {
    private static final String TAG = "GPUImageCameraInputFilter";
    private float[] mTextureTransformMatrix;
    private int mTextureTransformMatrixLocation;

    public GPUImageCameraInputFilter() {
        super(OpenGlUtils.readShaderFromRawResource(R.raw.default_vertex),
                NO_FILTER_EXTERNALOES_FRAGMENT_SHADER);
    }

    @Override
    public void onInit() {
        super.onInit();
        mTextureTransformMatrixLocation = GLES20.glGetUniformLocation(mGLProgId, "textureTransform");
    }

    @Override
    public void onDrawArraysPre() {
        GLES20.glUniformMatrix4fv(mTextureTransformMatrixLocation, 1, false, mTextureTransformMatrix, 0);
    }

    public void setTextureTransformMatrix(float[] mtx) {
        mTextureTransformMatrix = mtx;
    }
}