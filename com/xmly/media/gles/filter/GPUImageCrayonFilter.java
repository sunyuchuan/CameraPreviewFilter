package com.xmly.media.gles.filter;

import android.opengl.GLES20;

import com.xmly.media.gles.utils.OpenGlUtils;

import tv.danmaku.ijk.media.player.R;

public class GPUImageCrayonFilter extends GPUImageFilter {

	private int mSingleStepOffsetLocation;
	//1.0 - 5.0
	private int mStrengthLocation;

	public GPUImageCrayonFilter() {
		super(NO_FILTER_VERTEX_SHADER, OpenGlUtils.readShaderFromRawResource(R.raw.crayon));
	}

	public void onInit() {
        super.onInit();
        mSingleStepOffsetLocation = GLES20.glGetUniformLocation(getProgram(), "singleStepOffset");
        mStrengthLocation = GLES20.glGetUniformLocation(getProgram(), "strength");
        setFloat(mStrengthLocation, 2.0f);
    }

    public void onDestroy() {
        super.onDestroy();
    }

    public void onInitialized(){
        super.onInitialized();
        setFloat(mStrengthLocation, 0.5f);
    }

    private void setTexelSize(final float w, final float h) {
		setFloatVec2(mSingleStepOffsetLocation, new float[] {1.0f / w, 1.0f / h});
	}

    public void onInputSizeChanged(final int width, final int height) {
        super.onInputSizeChanged(width, height);
        setTexelSize(width, height);
    }
}
