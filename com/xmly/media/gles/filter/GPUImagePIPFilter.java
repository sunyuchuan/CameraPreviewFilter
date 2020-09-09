package com.xmly.media.gles.filter;

import android.opengl.GLES20;

import java.nio.FloatBuffer;

/**
 * Created by sunyc on 19-4-10.
 */
public class GPUImagePIPFilter extends GPUImageTwoInputFilter {
    private static final String TAG = "GPUImagePIPFilter";
    private static final float PIXEL_DIFF = 4.0f;

    public static final String PIP_FRAGMENT_SHADER = "" +
            "varying highp vec4 g_position;\n" +
            "varying highp vec2 textureCoordinate;\n" +
            "varying highp vec2 textureCoordinate2;\n" +
            "\n" +
            "uniform sampler2D inputImageTexture;\n" +
            "uniform sampler2D inputImageTexture2;\n" +
            "\n" +
            "uniform lowp vec2 leftBottom;\n" +
            "uniform lowp vec2 rightTop;\n" +
            "uniform highp float whiteFrameWidth;\n" +
            "uniform highp float whiteFrameHeight;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "lowp vec2 pipTextureCoordinateUse;\n" +
            "lowp vec2 leftBottom_flip = vec2(-rightTop.x, -rightTop.y);\n" +
            "lowp vec2 rightTop_flip = vec2(-leftBottom.x, -leftBottom.y);\n" +
            "if (g_position.x >= leftBottom.x - whiteFrameWidth && g_position.y >= leftBottom.y - whiteFrameHeight\n" +
            "    && g_position.x <= rightTop.x + whiteFrameWidth && g_position.y <= rightTop.y + whiteFrameHeight) {\n" +
            "\n" +
            "    if (g_position.x >= leftBottom.x && g_position.y >= leftBottom.y\n" +
            "        && g_position.x <= rightTop.x && g_position.y <= rightTop.y) {\n" +
            "\n" +
            "        pipTextureCoordinateUse = vec2((g_position.x - leftBottom.x) / (rightTop.x - leftBottom.x),\n" +
            "            1.0 - (g_position.y - leftBottom.y) / (rightTop.y - leftBottom.y));\n" +
            "        gl_FragColor = texture2D(inputImageTexture, pipTextureCoordinateUse);\n" +
            "    } else {\n" +
            "        gl_FragColor = vec4(255, 255, 255, 1);\n" +
            "    }\n" +
            "} else if (g_position.x >= leftBottom_flip.x - whiteFrameWidth && g_position.y >= leftBottom_flip.y - whiteFrameHeight\n" +
            "    && g_position.x <= rightTop_flip.x + whiteFrameWidth && g_position.y <= rightTop_flip.y + whiteFrameHeight) {\n" +
            "\n" +
            "    if (g_position.x >= leftBottom_flip.x && g_position.y >= leftBottom_flip.y\n" +
            "        && g_position.x <= rightTop_flip.x && g_position.y <= rightTop_flip.y) {\n" +
            "\n" +
            "        pipTextureCoordinateUse = vec2((g_position.x - leftBottom_flip.x) / (rightTop_flip.x - leftBottom_flip.x),\n" +
            "            1.0 - (g_position.y - leftBottom_flip.y) / (rightTop_flip.y - leftBottom_flip.y));\n" +
            "        gl_FragColor = texture2D(inputImageTexture, pipTextureCoordinateUse);\n" +
            "    } else {\n" +
            "        gl_FragColor = vec4(255, 255, 255, 1);\n" +
            "    }\n" +
            "} else {\n" +
            "    gl_FragColor = texture2D(inputImageTexture2, textureCoordinate);\n" +
            "}\n" +
            "\n" +
            "}\n";

    public static final String PIP_EXTERNALOES_FRAGMENT_SHADER = "" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "varying highp vec4 g_position;\n" +
            "varying highp vec2 textureCoordinate;\n" +
            "varying highp vec2 textureCoordinate2;\n" +
            "\n" +
            "uniform sampler2D inputImageTexture;\n" +
            "uniform samplerExternalOES inputImageTexture2;\n" +
            "\n" +
            "uniform lowp vec2 leftBottom;\n" +
            "uniform lowp vec2 rightTop;\n" +
            "uniform highp float whiteFrameWidth;\n" +
            "uniform highp float whiteFrameHeight;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "lowp vec2 pipTextureCoordinateUse;\n" +
            "if (g_position.x >= leftBottom.x - whiteFrameWidth && g_position.y >= leftBottom.y - whiteFrameHeight\n" +
            "    && g_position.x <= rightTop.x + whiteFrameWidth && g_position.y <= rightTop.y + whiteFrameHeight) {\n" +
            "\n" +
            "    if (g_position.x >= leftBottom.x && g_position.y >= leftBottom.y\n" +
            "        && g_position.x <= rightTop.x && g_position.y <= rightTop.y) {\n" +
            "\n" +
            "        pipTextureCoordinateUse = vec2((g_position.x - leftBottom.x) / (rightTop.x - leftBottom.x),\n" +
            "            1.0 - (g_position.y - leftBottom.y) / (rightTop.y - leftBottom.y));\n" +
            "        gl_FragColor = texture2D(inputImageTexture, pipTextureCoordinateUse);\n" +
            "    } else {\n" +
            "        gl_FragColor = vec4(255, 255, 255, 1);\n" +
            "    }\n" +
            "} else {\n" +
            "    gl_FragColor = texture2D(inputImageTexture2, textureCoordinate);\n" +
            "}\n" +
            "\n" +
            "}\n";

    private FloatBuffer mLeftBottomBuffer = null;
    private FloatBuffer mRightTopBuffer = null;
    protected int mLeftBottom;
    protected int mRightTop;
    protected int mWhiteFrameWidth;
    protected int mWhiteFrameHeight;

    public GPUImagePIPFilter() {
        this(VERTEX_SHADER, PIP_FRAGMENT_SHADER);
    }

    public GPUImagePIPFilter(String vertexShader, String fragmentShader) {
        super(vertexShader, fragmentShader);
        float rectangle[] = {0.0f, 0.0f, 1.0f, 1.0f};
        setRectangleCoordinate(rectangle);
    }

    @Override
    public void onInit() {
        super.onInit();

        mLeftBottom = GLES20.glGetUniformLocation(getProgram(), "leftBottom");
        mRightTop = GLES20.glGetUniformLocation(getProgram(), "rightTop");
        mWhiteFrameWidth = GLES20.glGetUniformLocation(getProgram(), "whiteFrameWidth");
        mWhiteFrameHeight = GLES20.glGetUniformLocation(getProgram(), "whiteFrameHeight");
    }

    @Override
    public void onOutputSizeChanged(final int width, final int height) {
        super.onOutputSizeChanged(width, height);
        GLES20.glUseProgram(getProgram());
        GLES20.glUniform1f(mWhiteFrameWidth, PIXEL_DIFF / (float)mOutputWidth);
        GLES20.glUniform1f(mWhiteFrameHeight, PIXEL_DIFF / (float)mOutputHeight);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onDrawArraysPre() {
        super.onDrawArraysPre();
        GLES20.glUniform2fv(mLeftBottom, 1, mLeftBottomBuffer);
        GLES20.glUniform2fv(mRightTop, 1, mRightTopBuffer);
    }

    @Override
    public void setRectangleCoordinate(float buffer[]) {
        if(mLeftBottomBuffer == null)
            mLeftBottomBuffer = FloatBuffer.allocate(2);
        if(mRightTopBuffer == null)
            mRightTopBuffer = FloatBuffer.allocate(2);

        float[] left = {buffer[0] * 2 - 1.0f, buffer[1] * 2 - 1.0f};
        mLeftBottomBuffer.put(left);
        mLeftBottomBuffer.flip();
        mLeftBottomBuffer.position(0);

        float[] right = {buffer[2] * 2 - 1.0f, buffer[3] * 2 - 1.0f};
        mRightTopBuffer.put(right);
        mRightTopBuffer.flip();
        mRightTopBuffer.position(0);
    }
}
