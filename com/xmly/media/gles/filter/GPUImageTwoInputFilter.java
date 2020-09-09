package com.xmly.media.gles.filter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.xmly.media.gles.utils.OpenGlUtils;
import com.xmly.media.gles.utils.TextureRotationUtil;

/**
 * Created by sunyc on 19-7-11.
 */
public class GPUImageTwoInputFilter extends GPUImageFilter {
    private static final String TAG = "GPUImageTwoInputFilter";
    public static final String VERTEX_SHADER = " " +
            "attribute vec4 position;\n" +
            "attribute vec4 inputTextureCoordinate;\n" +
            "attribute vec4 inputTextureCoordinate2;\n" +
            " \n" +
            "varying highp vec4 g_position;\n" +
            "varying highp vec2 textureCoordinate;\n" +
            "varying highp vec2 textureCoordinate2;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = position;\n" +
            "    textureCoordinate = inputTextureCoordinate.xy;\n" +
            "    textureCoordinate2 = inputTextureCoordinate2.xy;\n" +
            "    g_position = gl_Position;\n" +
            "}";

    public static final String FRAGMENT_SHADER = "" +
            "varying highp vec4 g_position;\n" +
            "varying highp vec2 textureCoordinate;\n" +
            "varying highp vec2 textureCoordinate2;\n" +
            "\n" +
            "uniform sampler2D inputImageTexture;\n" +
            "uniform sampler2D inputImageTexture2;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    lowp vec4 rgba = texture2D(inputImageTexture, textureCoordinate);\n" +
            "    lowp vec4 rgba2 = texture2D(inputImageTexture2, textureCoordinate2);\n" +
            "    gl_FragColor = mix(rgba, rgba2, 0.5);\n" +
            "}";

    public static final String EXTERNALOES_FRAGMENT_SHADER = "" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "varying highp vec4 g_position;\n" +
            "varying highp vec2 textureCoordinate;\n" +
            "varying highp vec2 textureCoordinate2;\n" +
            "\n" +
            "uniform samplerExternalOES inputImageTexture;\n" +
            "uniform samplerExternalOES inputImageTexture2;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    lowp vec4 rgba = texture2D(inputImageTexture, textureCoordinate);\n" +
            "    lowp vec4 rgba2 = texture2D(inputImageTexture2, textureCoordinate2);\n" +
            "    gl_FragColor = mix(rgba, rgba2, 0.5);\n" +
            "}";

    protected int mGLUniformTexture2;
    protected int mGLAttribTextureCoordinate2;
    protected int mInputTexture2 = OpenGlUtils.NO_TEXTURE;
    protected boolean mReleaseTexture = false;
    protected FloatBuffer mTexture2CoordinatesBuffer;
    protected boolean isOESTexture = false;

    public GPUImageTwoInputFilter() {
        this(VERTEX_SHADER, FRAGMENT_SHADER);
    }

    public GPUImageTwoInputFilter(String fragmentShader) {
        this(VERTEX_SHADER, fragmentShader);
    }

    public GPUImageTwoInputFilter(String vertexShader, String fragmentShader) {
        super(vertexShader, fragmentShader);

        mTexture2CoordinatesBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mTexture2CoordinatesBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);
    }

    @Override
    public void onInit() {
        super.onInit();

        mGLUniformTexture2 = GLES20.glGetUniformLocation(mGLProgId, "inputImageTexture2");
        mGLAttribTextureCoordinate2 = GLES20.glGetAttribLocation(mGLProgId,
                "inputTextureCoordinate2");
    }

    @Override
    protected void onDrawArraysPre() {
        if (mInputTexture2 != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
            if (isOESTexture) {
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mInputTexture2);
            } else {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mInputTexture2);
            }
            GLES20.glUniform1i(mGLUniformTexture2, 3);
        }

        mTexture2CoordinatesBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate2, 2, GLES20.GL_FLOAT, false, 0,
                mTexture2CoordinatesBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate2);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mReleaseTexture) {
            if (mInputTexture2 != OpenGlUtils.NO_TEXTURE) {
                GLES20.glDeleteTextures(1, new int[]{mInputTexture2}, 0);
            }
        }
        mInputTexture2 = OpenGlUtils.NO_TEXTURE;
    }

    public void setSecondTexture(int texId, boolean isOES) {
        mInputTexture2 = texId;
        isOESTexture = isOES;
        mReleaseTexture = false;
    }
}
