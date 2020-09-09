package com.xmly.media.gles.filter;

import android.annotation.TargetApi;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.os.Build;
import android.util.Log;

import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.gles.utils.OpenGlUtils;

import java.nio.FloatBuffer;

/**
 * Created by sunyc on 19-3-21.
 */

public class GPUImagePixelCopierFilter extends GPUImageFilter {
    private static final String TAG = "PixelCopierFilter";
    private static final int PIXEL_STRIDE = 4;//RGBA 4字节
    private static final int PBO_BUFFER_NUM = 2;//pbo buffers的个数
    protected static final int FORMAT_RGBA8888 = 1;
    protected static final int FORMAT_YUY2 = 2;
    private final int mAlign = 8;//mAlign字节对齐
    private int[] mPboBuffers;
    private int mPboSize;
    private int mRowStride;
    private int mPboIndex = -1;
    private int mPboNextIndex = -1;
    private boolean mFirstBindPbo = true;

    private int mPboWidth;
    private int mPboHeight;
    protected XMMediaRecorder mNativeRecorder = null;

    public GPUImagePixelCopierFilter(XMMediaRecorder recorder) {
        this(recorder, NO_FILTER_VERTEX_SHADER, NO_FILTER_FRAGMENT_SHADER);
    }

    public GPUImagePixelCopierFilter(XMMediaRecorder recorder, String vertexShader, String fragmentShader) {
        super(vertexShader, fragmentShader);
        mNativeRecorder = recorder;
    }

    private void destroyPboBuffers() {
        if (mPboBuffers != null) {
            GLES30.glDeleteBuffers(mPboBuffers.length, mPboBuffers, 0);
            mPboBuffers = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyPboBuffers();
    }

    @Override
    public void onOutputSizeChanged(int width, int height) {
        super.onOutputSizeChanged(width, height);

        if (mPboBuffers != null && (mPboWidth != width || mPboHeight != height)) {
            destroyPboBuffers();
        }

        if (mPboBuffers == null) {
            mRowStride = (width * PIXEL_STRIDE + (mAlign - 1)) & ~(mAlign - 1);
            mPboSize = mRowStride * height;
            mPboBuffers = new int[PBO_BUFFER_NUM];

            for(int i = 0; i < PBO_BUFFER_NUM; i++) {
                GLES30.glGenBuffers(1, mPboBuffers, i);
                GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, mPboBuffers[i]);
                GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, mPboSize, null, GLES30.GL_STATIC_READ);
                GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
            }
        }
        mPboWidth = width;
        mPboHeight = height;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    protected void downloadImageToRecorderFromTexture(int format) {
        if (mPboBuffers == null) {
            Log.e(TAG, "mPboBuffers is null, downloadImageToRecorderFromTexture exit");
            return;
        }

        mPboIndex = (mPboIndex + 1) % 2;
        mPboNextIndex = (mPboIndex + 1) % 2;
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, mAlign);
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, mPboBuffers[mPboIndex]);
        mNativeRecorder.glReadPixels(0, 0, mRowStride / PIXEL_STRIDE, mPboHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE);

        if (mFirstBindPbo) {//第一帧没有数据,退出
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
            mFirstBindPbo = false;
            return;
        }

        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, mPboBuffers[mPboNextIndex]);
        int rowPadding = mRowStride - PIXEL_STRIDE * mPboWidth;//最好保证该值为0，方便像素数据拷贝
        mNativeRecorder.glMapBufferRange_put(GLES30.GL_PIXEL_PACK_BUFFER, 0, mPboSize, GLES30.GL_MAP_READ_BIT,
                mPboWidth, mPboHeight, PIXEL_STRIDE, rowPadding, format);
        GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER);
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
    }

    @Override
    public int onDrawToTexture(final int textureId, final FloatBuffer cubeBuffer,
                               final FloatBuffer textureBuffer) {
        if (mFrameBuffers == null)
            return OpenGlUtils.NO_TEXTURE;

        GLES20.glViewport(0, 0, mFrameWidth, mFrameHeight);
        GLES20.glUseProgram(0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
        // Attach texture to frame buffer
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureId, 0);
        if (mNativeRecorder != null) {
            downloadImageToRecorderFromTexture(FORMAT_RGBA8888);
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
        return mFrameBufferTextures[0];
    }
}
