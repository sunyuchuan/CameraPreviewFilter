package com.xmly.media.gles.filter;

import android.opengl.GLES20;

import com.xmly.media.gles.utils.OpenGlUtils;

import tv.danmaku.ijk.media.player.R;

/**
 * Created by sunyc on 19-3-13.
 */
public class GPUImageBeautyFilterOptimization extends GPUImageFilter {
    private int mWidth;
    private int mHeight;
    private int mOpacity;
    private float level;

    public GPUImageBeautyFilterOptimization() {
        this(0.7f);
    }

    public GPUImageBeautyFilterOptimization(float level) {
        this(level, R.raw.beauty_optimization);
    }

    public GPUImageBeautyFilterOptimization(float level, final int resourceId) {
        super(NO_FILTER_VERTEX_SHADER,
                OpenGlUtils.readShaderFromRawResource(resourceId));
        this.level = level;
    }

    public void onInit() {
        super.onInit();
        mWidth = GLES20.glGetUniformLocation(getProgram(), "width");
        mHeight = GLES20.glGetUniformLocation(getProgram(), "height");
        mOpacity = GLES20.glGetUniformLocation(getProgram(), "opacity");
        setBeautyLevel(level);
    }

    public void onInitialized() {
        super.onInitialized();
    }

    @Override
    public void onOutputSizeChanged(final int width, final int height) {
        super.onOutputSizeChanged(width, height);
        setInteger(mWidth, width);
        setInteger(mHeight, height);
    }

    public void setBeautyLevel(float level) {
        setFloat(mOpacity, level);
    }
}
