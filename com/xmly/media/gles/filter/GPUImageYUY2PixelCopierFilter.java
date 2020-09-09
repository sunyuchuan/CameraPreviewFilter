package com.xmly.media.gles.filter;

import android.opengl.GLES20;

import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.gles.utils.OpenGlUtils;

import java.nio.FloatBuffer;

/**
 * Created by sunyc on 19-7-1.
 */

public class GPUImageYUY2PixelCopierFilter extends GPUImagePixelCopierFilter {
    private static final String TAG = "YUY2PixelCopierFilter";

    private static final float[] coefYVec = new float[]{0.299f, 0.587f, 0.114f, 16f / 255f};
    private static final float[] coefUVec = new float[]{-0.1687f, -0.3313f, 0.5f, 128f / 255f};
    private static final float[] coefVVec = new float[]{0.5f, -0.4187f, -0.0813f, 128f / 255f};

    private static final float[] coefYVec709 = new float[]{0.18259f, 0.61423f, 0.06201f, 16f / 255f};
    private static final float[] coefUVec709 = new float[]{-0.10065f, -0.33857f, 0.43922f, 128f / 255f};
    private static final float[] coefVVec709 = new float[]{0.43922f, -0.39894f, -0.04027f, 128f / 255f};

    private static final float[] coefYVec601 = new float[]{0.25679f, 0.50413f, 0.09791f, 16f / 255f};
    private static final float[] coefUVec601 = new float[]{-0.14822f, -0.29099f, 0.43922f, 128f / 255f};
    private static final float[] coefVVec601 = new float[]{0.43922f, -0.36779f, -0.07142f, 128f / 255f};

    /**
     * From RGB to YUV
     *              Y = 0.299R + 0.587G + 0.114B
     *              U = 0.492 (B-Y)
     *              V = 0.877 (R-Y)
     *
     * It can also be represented as:
     *              Y =  0.299R + 0.587G + 0.114B
     *              U = -0.147R - 0.289G + 0.436B
     *              V =  0.615R - 0.515G - 0.100B
     *
     * From YUV to RGB
     *              R = Y + 1.140V
     *              G = Y - 0.395U - 0.581V
     *              B = Y + 2.032U
     *
     */
    /**
     * 下面这个FragmentShader 主要作用
     * 以下代表着纹理的坐标
     *      (0, 1) --------------- (1, 1)
     *            |               |
     *            |               |
     *            |               |
     *            |               |
     *            |               |
     *  (0, 0) --------------- (1, 0)
     *  首先取出一个纹理坐标中的点 比如说(0.5, 0.0)这样子的一个二维向量，然后调用texture2D这个内嵌函数
     *  会把sampler上的这个像素点以一个Vec4的结构拿出来，实际上就是(r, g, b, a)的形式, 一般处理起来就是
     *  直接给到gl_FragColor，说明这个像素点处理完了
     *  但是我们这里是这样子的，因为我们是把RGBA的数据转换为YUV422的数据，所以像素数据少了一般，而在Main函数
     *  的处理过程中，我们就需要每次执行要把两个像素点合并成一个像素点
     *  也就是  pixel1:(r,g,b,a)  pixel2:(r,g,b,a)
     *                                Y1,U1,V1             Y2
     *  然后在按照YUY2的pixelFormat组合起来(Y1,U1,Y2,V1) 这样子就把8个字节表示的两个像素搞成了4个字节的YUY2表示的2个像素点
     *
     *  具体是如何做的转换呢？
     *              float perHalfTexel = 0.5 / width; 把纹理坐标的一半除以纹理的宽度
     *              当前像素点的x坐标上减去perHalfTexel 所在的像素点 转换为Y1,U1,V1
     *              当前像素点的x坐标上加上perHalfTexel 所在的像素点 转换为Y2
     *
     *      但是应该注意的是 我们将像素减半了，所以应该我们调用OpenGL指令glViewPort的时候应该width降为一般，最终调用指令glReadPixels
     *      的时候读取的width也应为一半
     "uniform highp float sPerHalfTexel = 0.00069445;\n"
     */
    public static final String FRAGMENT_SHADER = "" +
            "varying highp vec2 textureCoordinate;\n" +
            " \n" +
            "uniform mediump vec4 coefY;\n" +
            "uniform mediump vec4 coefU;\n" +
            "uniform mediump vec4 coefV;\n" +
            "uniform highp float sPerHalfTexel;\n" +
            " \n" +
            "uniform sampler2D inputImageTexture;\n" +
            "void main()\n" +
            "{\n" +
            "    highp vec2 texelOffset = vec2(sPerHalfTexel, 0);\n" +
            "    lowp vec4 leftRGBA = texture2D(inputImageTexture, textureCoordinate - texelOffset);\n" +
            "    lowp vec4 rightRGBA = texture2D(inputImageTexture, textureCoordinate + texelOffset);\n" +
            "    lowp vec4 left = vec4(leftRGBA.rgb, 1);\n" +
            "    lowp float y0 = dot(left, coefY);\n" +
            "    lowp float y1 = dot(vec4(rightRGBA.rgb, 1), coefY);\n" +
            "    lowp float u0 = dot(left, coefU);\n" +
            "    lowp float v0 = dot(left, coefV);\n" +
            "    gl_FragColor = vec4(y0, u0, y1, v0);\n" +
            "}\n";
    private int mGLcoefY;
    private int mGLcoefU;
    private int mGLcoefV;
    private int mGLPerHalfTexel;
    private float sPerHalfTexel = -1;
    private int yuy2Pairs = -1;//yuy2数据量比rgba减半 (width - 1)/2,保证是偶数

    public GPUImageYUY2PixelCopierFilter(XMMediaRecorder recorder) {
        this(recorder, NO_FILTER_VERTEX_SHADER, FRAGMENT_SHADER);
    }

    public GPUImageYUY2PixelCopierFilter(XMMediaRecorder recorder, String vertexShader, String fragmentShader) {
        super(recorder, vertexShader, fragmentShader);
    }

    private void initCoefYUVVec() {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform4fv(mGLcoefY, 1, coefYVec709, 0);
                GLES20.glUniform4fv(mGLcoefU, 1, coefUVec709, 0);
                GLES20.glUniform4fv(mGLcoefV, 1, coefVVec709, 0);
            }
        });
    }

    @Override
    public void onInit() {
        super.onInit();
        mGLcoefY = GLES20.glGetUniformLocation(mGLProgId, "coefY");
        mGLcoefU = GLES20.glGetUniformLocation(mGLProgId, "coefU");
        mGLcoefV = GLES20.glGetUniformLocation(mGLProgId, "coefV");
        mGLPerHalfTexel = GLES20.glGetUniformLocation(mGLProgId, "sPerHalfTexel");
        initCoefYUVVec();
    }

    @Override
    public void onOutputSizeChanged(final int width, final int height) {
        super.onOutputSizeChanged((width + 1) / 2, height);
        yuy2Pairs = (width + 1) / 2;

        sPerHalfTexel = 0.5f / (float) width;
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform1f(mGLPerHalfTexel, sPerHalfTexel);
            }
        });
    }

    @Override
    public int onDrawToTexture(final int textureId, final FloatBuffer cubeBuffer,
                               final FloatBuffer textureBuffer) {
        if (mFrameBuffers == null)
            return OpenGlUtils.NO_TEXTURE;

        GLES20.glViewport(0, 0, mFrameWidth, mFrameHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFrameBufferTextures[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glUseProgram(mGLProgId);
        runPendingOnDrawTasks();
        if (!isInitialized()) {
            return OpenGlUtils.NOT_INIT;
        }

        cubeBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, cubeBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        textureBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0,
                textureBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);
        if (textureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            GLES20.glUniform1i(mGLUniformTexture, 0);
        }
        onDrawArraysPre();
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        if (mNativeRecorder != null) {
            downloadImageToRecorderFromTexture(FORMAT_YUY2);
        }
        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glUseProgram(0);
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
        return mFrameBufferTextures[0];
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
