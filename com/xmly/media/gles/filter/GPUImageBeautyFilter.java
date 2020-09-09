package com.xmly.media.gles.filter;

import android.opengl.GLES20;

import com.xmly.media.gles.utils.GPUImageParams;
import com.xmly.media.gles.utils.OpenGlUtils;

import tv.danmaku.ijk.media.player.R;

/**
 * Created by sunyc on 18-11-13.
 */

public class GPUImageBeautyFilter extends GPUImageFilter {

    private int mSingleStepOffsetLocation;
    private int mParamsLocation;
    private int beauty_level = 5;

    public GPUImageBeautyFilter() {
        this(5);
    }

    public GPUImageBeautyFilter(int level) {
        this(level, R.raw.beauty);
    }

    public GPUImageBeautyFilter(int level, final int resourceId) {
        super(NO_FILTER_VERTEX_SHADER,
                OpenGlUtils.readShaderFromRawResource(resourceId));
        beauty_level = level;
    }

    public void onInit() {
        super.onInit();
        mSingleStepOffsetLocation = GLES20.glGetUniformLocation(getProgram(), "singleStepOffset");
        mParamsLocation = GLES20.glGetUniformLocation(getProgram(), "params");
    }

    public void onInitialized() {
        super.onInitialized();
        setBeautyLevel(beauty_level);
    }

    private void setTexelSize(final float w, final float h) {
        setFloatVec2(mSingleStepOffsetLocation, new float[] {2.0f / w, 2.0f / h});
    }

    @Override
    public void onOutputSizeChanged(final int width, final int height) {
        super.onOutputSizeChanged(width, height);
        setTexelSize(width, height);
    }

    public void setBeautyLevel(int level){
        switch (level) {
            case 1:
                setFloat(mParamsLocation, 1.0f);
                break;
            case 2:
                setFloat(mParamsLocation, 0.8f);
                break;
            case 3:
                setFloat(mParamsLocation,0.6f);
                break;
            case 4:
                setFloat(mParamsLocation, 0.4f);
                break;
            case 5:
                setFloat(mParamsLocation,0.33f);
                break;
            default:
                break;
        }
    }

    public void onBeautyLevelChanged() {
        setBeautyLevel(GPUImageParams.beautyLevel);
    }
}