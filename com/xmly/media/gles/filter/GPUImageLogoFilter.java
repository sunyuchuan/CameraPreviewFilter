package com.xmly.media.gles.filter;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.util.Log;

import com.xmly.media.gles.utils.OpenGlUtils;

import java.nio.FloatBuffer;

/**
 * Created by sunyc on 19-5-16.
 */
public class GPUImageLogoFilter extends GPUImageFilter {
    private static final String TAG = "GPUImageLogoFilter";
    private static final String VERTEX_SHADER = "" +
            "precision highp float;\n" +
            "attribute vec4 position;\n" +
            "attribute vec4 inputTextureCoordinate;\n" +
            " \n" +
            "varying vec2 textureCoordinate;\n" +
            "varying highp vec4 g_position;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = position;\n" +
            "    textureCoordinate = inputTextureCoordinate.xy;\n" +
            "    g_position = gl_Position;\n" +
            "}";

    public static final String FRAGMENT_SHADER = "" +
            "precision highp float;\n" +
            "varying highp vec2 textureCoordinate;\n" +
            "varying highp vec4 g_position;\n" +
            "\n" +
            "uniform lowp sampler2D inputImageTexture;\n" +
            "uniform lowp sampler2D inputLogoTexture;\n" +
            "uniform highp vec2 leftBottom;\n" +
            "uniform highp vec2 rightTop;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    lowp vec4 rgba2;\n" +
            "    lowp vec4 rgba1;\n" +
            "    highp vec2 textureCoordinate2use;\n" +
            "    if (g_position.x >= leftBottom.x && g_position.y >= leftBottom.y\n" +
            "        && g_position.x <= rightTop.x && g_position.y <= rightTop.y) {\n" +
            "        textureCoordinate2use = vec2((g_position.x - leftBottom.x) / (rightTop.x - leftBottom.x),\n" +
            "            1.0 - (g_position.y - leftBottom.y) / (rightTop.y - leftBottom.y));\n" +
            "        rgba2 = texture2D(inputLogoTexture, textureCoordinate2use);\n" +
            "        rgba1 = texture2D(inputImageTexture, textureCoordinate);\n" +
            "        gl_FragColor = rgba2 * rgba2.a + rgba1 * (1.0 - rgba2.a);\n" +
            "    } else {\n" +
            "        gl_FragColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            "    }\n" +
            "}\n";

    private int mGLUniformLogoTexture;
    private int mLeftBottom;
    private int mRightTop;

    public int mSubTexture = OpenGlUtils.NO_TEXTURE;
    private int mLogoWidth = 0;
    private int mLogoHeight = 0;
    private float[] mInputBuffer = new float[4];
    private FloatBuffer mLeftBottomBuffer = null;
    private FloatBuffer mRightTopBuffer = null;

    public GPUImageLogoFilter() {
        this(VERTEX_SHADER, FRAGMENT_SHADER);
    }

    public GPUImageLogoFilter(String vertexShader, String fragmentShader) {
        super(vertexShader, fragmentShader);
        float rectangle[] = {0.55f, 0.55f, 0.95f, 0.95f};
        setRectangleCoordinate(rectangle);
    }

    @Override
    public void onInit() {
        super.onInit();

        mGLUniformLogoTexture = GLES20.glGetUniformLocation(getProgram(), "inputLogoTexture");
        mLeftBottom = GLES20.glGetUniformLocation(getProgram(), "leftBottom");
        mRightTop = GLES20.glGetUniformLocation(getProgram(), "rightTop");
    }

    private synchronized void rectCoordinateTransform(float[] inBuffer) {
        float buffer[] = {inBuffer[0], inBuffer[1], inBuffer[2], inBuffer[3]};

        buffer[0] *= mOutputWidth;
        buffer[1] *= mOutputHeight;
        buffer[2] *= mOutputWidth;
        buffer[3] *= mOutputHeight;

        if(mLogoWidth != 0 && mLogoHeight != 0) {
            float logo_rect_aspect_ratio = (buffer[2] - buffer[0])/(buffer[3] - buffer[1]);
            float logo_image_aspect_ratio = (float)mLogoWidth / (float)mLogoHeight;
            if (logo_image_aspect_ratio > logo_rect_aspect_ratio) {
                buffer[1] = buffer[3] - (buffer[2] - buffer[0]) / logo_image_aspect_ratio;
            } else {
                buffer[0] = buffer[2] - (buffer[3] - buffer[1]) * logo_image_aspect_ratio;
            }
        }

        buffer[0] = (buffer[0]/mOutputWidth)*2 -1.0f;
        buffer[1] = (buffer[1]/mOutputHeight)*2 -1.0f;
        buffer[2] = (buffer[2]/mOutputWidth)*2 -1.0f;
        buffer[3] = (buffer[3]/mOutputHeight)*2 -1.0f;

        if(mLeftBottomBuffer == null)
            mLeftBottomBuffer = FloatBuffer.allocate(2);
        float[] left = {buffer[0], buffer[1]};
        mLeftBottomBuffer.put(left);
        mLeftBottomBuffer.flip();
        mLeftBottomBuffer.position(0);

        if(mRightTopBuffer == null)
            mRightTopBuffer = FloatBuffer.allocate(2);
        float[] right = {buffer[2], buffer[3]};
        mRightTopBuffer.put(right);
        mRightTopBuffer.flip();
        mRightTopBuffer.position(0);
    }

    @Override
    public void onOutputSizeChanged(final int width, final int height) {
        super.onOutputSizeChanged(width, height);
        rectCoordinateTransform(mInputBuffer);
    }

    public void setBitmap(final Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }

        runOnDraw(new Runnable() {
            public void run() {
                mLogoWidth = bitmap.getWidth();
                mLogoHeight = bitmap.getHeight();
                rectCoordinateTransform(mInputBuffer);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
                mSubTexture = OpenGlUtils.loadTexture(bitmap, mSubTexture, true);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSubTexture != OpenGlUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, new int[]{mSubTexture}, 0);
            mSubTexture = OpenGlUtils.NO_TEXTURE;
        }
    }

    @Override
    protected void onDrawArraysPre() {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mSubTexture);
        GLES20.glUniform1i(mGLUniformLogoTexture, 3);
        GLES20.glUniform2fv(mLeftBottom, 1, mLeftBottomBuffer);
        GLES20.glUniform2fv(mRightTop, 1, mRightTopBuffer);
    }

    @Override
    public void setRectangleCoordinate(float buffer[]) {
        mInputBuffer[0] = buffer[0];
        mInputBuffer[1] = buffer[1];
        mInputBuffer[2] = buffer[2];
        mInputBuffer[3] = buffer[3];
        rectCoordinateTransform(mInputBuffer);
    }
}
