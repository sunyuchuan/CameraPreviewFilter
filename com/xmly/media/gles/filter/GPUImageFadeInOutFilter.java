package com.xmly.media.gles.filter;

import android.graphics.Bitmap;
import android.opengl.GLES20;

import com.xmly.media.gles.utils.OpenGlUtils;

/**
 * Created by sunyc on 19-5-21.
 */

public class GPUImageFadeInOutFilter extends GPUImageImageSwitchFilter {
    private static final String TAG = "GPUImageFadeInOutFilter";
    public static final String FRAGMENT_SHADER = "" +
            "varying highp vec4 g_position;\n" +
            "varying highp vec2 textureCoordinate;\n" +
            "varying highp vec2 textureCoordinate2;\n" +
            "\n" +
            "uniform sampler2D inputImageTexture;\n" +
            "uniform sampler2D inputImageTexture2;\n" +
            "\n" +
            "uniform float offset;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "     lowp vec4 rgba1;\n" +
            "     lowp vec4 rgba2;\n" +
            "     rgba1 = texture2D(inputImageTexture, textureCoordinate);\n" +
            "     rgba2 = texture2D(inputImageTexture2, vec2(textureCoordinate.x, 1.0 - textureCoordinate.y));\n" +
            "     gl_FragColor = rgba2 * offset + rgba1 * (1.0 - offset);\n" +
            "}";
    private int mGLOffset;
    private float mOffset;

    public GPUImageFadeInOutFilter() {
        this(VERTEX_SHADER, FRAGMENT_SHADER);
    }

    public GPUImageFadeInOutFilter(String vertexShader, String fragmentShader) {
        super(vertexShader, fragmentShader);
    }

    @Override
    public void onInit() {
        super.onInit();

        mGLOffset = GLES20.glGetUniformLocation(getProgram(), "offset");
    }

    @Override
    public void onInitialized() {
        super.onInitialized();
        mOffset = 0.0f;
    }

    public void setBitmap(final Bitmap bitmap) {
        super.setBitmap(bitmap);
        runOnDraw(new Runnable() {
            public void run() {
                mOffset = 0.0f;
            }
        });
    }

    @Override
    protected void onDrawArraysPre() {
        super.onDrawArraysPre();
        if (mInputTexture2 != OpenGlUtils.NO_TEXTURE) {
            if (mOffset < 1.0f) {
                mOffset += 0.05f;
            } else if (!mSwitchCompleted) {
                if (mListener != null) mListener.onImageSwitchCompleted();
                mSwitchCompleted = true;
                mOffset = 1.0f;
            }
        }

        GLES20.glUniform1f(mGLOffset, mOffset);
    }

    public void setBlindsOffset(final float offset) {
        mOffset = offset;
        setFloat(mGLOffset, mOffset);
    }
}
