package com.xmly.media.gles.filter;

import android.graphics.Bitmap;
import android.opengl.GLES20;

import com.xmly.media.gles.utils.OpenGlUtils;

/**
 * Created by sunyc on 19-8-9.
 */
public class GPUImageImageSwitchFilter extends GPUImageTwoInputFilter {
    private static final String TAG = "ImageSwitchFilter";
    protected IImageSwitchListener mListener = null;
    protected boolean mSwitchCompleted = true;

    public GPUImageImageSwitchFilter() {
        this(VERTEX_SHADER, FRAGMENT_SHADER);
    }

    public GPUImageImageSwitchFilter(String fragmentShader) {
        this(VERTEX_SHADER, fragmentShader);
    }

    public GPUImageImageSwitchFilter(String vertexShader, String fragmentShader) {
        super(vertexShader, fragmentShader);
    }

    @Override
    public void onInit() {
        super.onInit();

        mReleaseTexture = false;
        mSwitchCompleted = true;
    }

    public void setBitmap(final Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }

        runOnDraw(new Runnable() {
            public void run() {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
                mInputTexture2 = OpenGlUtils.loadTexture(bitmap, mInputTexture2, true);
                mReleaseTexture = true;
                mSwitchCompleted = false;
            }
        });
    }

    public void setListener(IImageSwitchListener l) {
        mListener = l;
    }

    public interface IImageSwitchListener {
        void onImageSwitchCompleted();
    }
}
