package com.xmly.media.gles.filter;

import android.graphics.Bitmap;
import android.opengl.GLES20;

import com.xmly.media.gles.utils.OpenGlUtils;

/**
 * Created by sunyc on 19-5-21.
 */
public class GPUImageBlindsFilter extends GPUImageImageSwitchFilter {
    private static final String TAG = "GPUImageBlindsFilter";
    public static final String FRAGMENT_SHADER = "" +
            "varying highp vec4 g_position;\n" +
            "varying highp vec2 textureCoordinate;\n" +
            "varying highp vec2 textureCoordinate2;\n" +
            "\n" +
            "uniform sampler2D inputImageTexture;\n" +
            "uniform sampler2D inputImageTexture2;\n" +
            " \n" +
            "uniform float unitWidth;\n" +
            "uniform float offset;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "     float modPart = mod(textureCoordinate.x, unitWidth);\n" +
            "     float solidPart = offset * unitWidth;\n" +
            "     if (modPart < solidPart)\n" +
            "     {\n" +
            "         gl_FragColor = texture2D(inputImageTexture2, vec2(textureCoordinate.x, 1.0 - textureCoordinate.y));\n" +
            "     } else {\n" +
            "         gl_FragColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            "     }\n" +
            "}";
    private int mGLUnitWidth;
    private int mGLOffset;
    private float mUnitWidth;
    private float mOffset;

    public GPUImageBlindsFilter() {
        this(VERTEX_SHADER, FRAGMENT_SHADER);
    }

    public GPUImageBlindsFilter(String vertexShader, String fragmentShader) {
        super(vertexShader, fragmentShader);
    }

    @Override
    public void onInit() {
        super.onInit();

        mGLUnitWidth = GLES20.glGetUniformLocation(getProgram(), "unitWidth");
        mGLOffset = GLES20.glGetUniformLocation(getProgram(), "offset");
    }

    @Override
    public void onInitialized() {
        super.onInitialized();
        mUnitWidth = 0.1f;
        mOffset = 0.0f;
    }

    public void setBitmap(final Bitmap bitmap) {
        super.setBitmap(bitmap);
        runOnDraw(new Runnable() {
            public void run() {
                mUnitWidth = 0.1f;
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

        GLES20.glUniform1f(mGLUnitWidth, mUnitWidth);
        GLES20.glUniform1f(mGLOffset, mOffset);
    }

    public void setBlindsWinNumber(final float winNumber) {
        mUnitWidth = 1.0f / winNumber;
        setFloat(mGLUnitWidth, mUnitWidth);
    }

    public void setBlindsOffset(final float offset) {
        mOffset = offset;
        setFloat(mGLOffset, mOffset);
    }
}