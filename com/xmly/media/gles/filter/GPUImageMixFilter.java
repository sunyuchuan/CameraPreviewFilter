package com.xmly.media.gles.filter;

import android.graphics.Bitmap;
import android.opengl.GLES20;

import com.xmly.media.gles.utils.OpenGlUtils;

/**
 * Created by sunyc on 19-4-18.
 */

public class GPUImageMixFilter extends GPUImageFilter {
    private static final String TAG = "GPUImageMixFilter";
    public static final String FRAGMENT_SHADER = "" +
            "varying highp vec2 textureCoordinate;\n" +
            " \n" +
            "uniform sampler2D inputSubtitleTexture;\n" +
            "uniform sampler2D inputImageTexture;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "     lowp vec4 rgba;\n" +
            "     lowp vec4 subRgba;\n" +
            "     rgba = texture2D(inputImageTexture, textureCoordinate);\n" +
            "     subRgba = texture2D(inputSubtitleTexture, textureCoordinate);\n" +
            "     gl_FragColor = subRgba * subRgba.a + rgba * (1.0 - subRgba.a);\n" +
            "}";

    protected int mGLUniformSubTexture;
    public int mSubTexture = OpenGlUtils.NO_TEXTURE;

    public GPUImageMixFilter() {
        this(NO_FILTER_VERTEX_SHADER, FRAGMENT_SHADER);
    }

    public GPUImageMixFilter(String vertexShader, String fragmentShader) {
        super(vertexShader, fragmentShader);
    }

    @Override
    public void onInit() {
        super.onInit();
        mGLUniformSubTexture = GLES20.glGetUniformLocation(getProgram(), "inputSubtitleTexture");
    }

    public void setBitmap(final Bitmap bitmap) {
        if (bitmap != null && bitmap.isRecycled()) {
            return;
        }

        runOnDraw(new Runnable() {
            public void run() {
                if (bitmap == null || bitmap.isRecycled()) {
                    return;
                }

                GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
                mSubTexture = OpenGlUtils.loadTexture(bitmap, mSubTexture, false);
            }
        });
    }

    @Override
    protected void onDrawArraysPre() {
        if (mSubTexture != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mSubTexture);
            GLES20.glUniform1i(mGLUniformSubTexture, 3);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSubTexture != OpenGlUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, new int[]{mSubTexture}, 0);
            mSubTexture = OpenGlUtils.NO_TEXTURE;
        }
    }
}
