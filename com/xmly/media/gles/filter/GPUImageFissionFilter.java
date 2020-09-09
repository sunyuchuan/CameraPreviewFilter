package com.xmly.media.gles.filter;

import android.opengl.GLES20;

import com.xmly.media.gles.utils.OpenGlUtils;
import com.xmly.media.gles.utils.TextureRotationUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by sunyc on 19-6-17.
 */

public class GPUImageFissionFilter extends GPUImageFilter {
    private static final String TAG = "GPUImageFissionFilter";
    private static final int FPS = 25;
    private static final float DURATION = 3.0f;
    private FloatBuffer mGLCubeBuffer;
    private FloatBuffer mGLTextureBuffer;
    private int step = 0;
    private int num = 2;

    private static final float CUBE[] = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f,
    };

    public GPUImageFissionFilter() {
        super(NO_FILTER_VERTEX_SHADER, NO_FILTER_FRAGMENT_SHADER);
        mGLCubeBuffer = ByteBuffer.allocateDirect(CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLCubeBuffer.put(CUBE).position(0);

        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);
    }

    @Override
    public void onInit() {
        super.onInit();
        step = 0;
        num = 2;
    }

    private void fission(FloatBuffer buffer) {
        float[] textureBuffer = new float[TextureRotationUtil.TEXTURE_NO_ROTATION.length];
        if(step < DURATION * FPS)
        {
            step ++;
            num = 3;
        } else if(step < DURATION * FPS * 2) {
            step ++;
            num = 2;
        } else {
            step = 0;
        }

        for (int i = 0; i < textureBuffer.length; i++) {
            textureBuffer[i] = buffer.get(i) * num;
        }
        mGLTextureBuffer.put(textureBuffer).position(0);
    }

    @Override
    public int onDrawToTexture(final int textureId, final FloatBuffer cubeBuffer,
                               final FloatBuffer textureBuffer) {
        if (mFrameBuffers == null)
            return OpenGlUtils.NO_TEXTURE;
        GLES20.glViewport(0, 0, mFrameWidth, mFrameHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
        GLES20.glUseProgram(mGLProgId);
        runPendingOnDrawTasks();
        if (!isInitialized()) {
            return OpenGlUtils.NOT_INIT;
        }

        cubeBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, cubeBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        fission(textureBuffer);
        mGLTextureBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0,
                mGLTextureBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);
        if (textureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
            GLES20.glUniform1i(mGLUniformTexture, 0);
        }
        onDrawArraysPre();
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
        return mFrameBufferTextures[0];
    }
}
