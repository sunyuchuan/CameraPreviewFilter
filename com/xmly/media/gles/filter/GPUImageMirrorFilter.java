package com.xmly.media.gles.filter;

import android.opengl.GLES20;

import com.xmly.media.gles.utils.OpenGlUtils;
import com.xmly.media.gles.utils.TextureRotationUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by sunyc on 19-3-13.
 */

public class GPUImageMirrorFilter extends GPUImageFilter {
    public static final String MIRROR_VERTEX_SHADER = "" +
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
    public static final String MIRROR_FRAGMENT_SHADER = "" +
            "varying highp vec2 textureCoordinate;\n" +
            "varying highp vec4 g_position;\n" +
            " \n" +
            "uniform sampler2D inputImageTexture;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "    if (g_position.x >= -1.0 && g_position.x <= 0.0\n" +
            "         && g_position.y >= 0.0 && g_position.y <= 1.0) {\n" +
            "        gl_FragColor = texture2D(inputImageTexture, vec2(2.0*textureCoordinate.x, 2.0*textureCoordinate.y));\n" +
            "    }\n" +
            "    if (g_position.x >= 0.0 && g_position.x <= 1.0\n" +
            "         && g_position.y >= 0.0 && g_position.y <= 1.0) {\n" +
            "        gl_FragColor = texture2D(inputImageTexture, vec2(2.0 - 2.0*textureCoordinate.x, 2.0*textureCoordinate.y));\n" +
            "    }\n" +
            "    if (g_position.x >= -1.0 && g_position.x <= 0.0\n" +
            "         && g_position.y >= -1.0 && g_position.y <= 0.0) {\n" +
            "        gl_FragColor = texture2D(inputImageTexture, vec2(2.0*textureCoordinate.x, 2.0 - 2.0*textureCoordinate.y));\n" +
            "    }\n" +
            "    if (g_position.x >= 0.0 && g_position.x <= 1.0\n" +
            "         && g_position.y >= -1.0 && g_position.y <= 0.0) {\n" +
            "        gl_FragColor = texture2D(inputImageTexture, vec2(2.0 - 2.0*textureCoordinate.x, 2.0 - 2.0*textureCoordinate.y));\n" +
            "    }\n" +
            "}";
    private final FloatBuffer mGLCubeBuffer;
    private final FloatBuffer mGLTextureBuffer;

    public GPUImageMirrorFilter() {
        super(MIRROR_VERTEX_SHADER, MIRROR_FRAGMENT_SHADER);
        mGLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLCubeBuffer.put(TextureRotationUtil.CUBE).position(0);

        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);
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

        mGLCubeBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, mGLCubeBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        mGLTextureBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0,
                mGLTextureBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);
        if (textureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
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
