package com.xmly.media.video.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Build;
import android.view.Surface;

import com.xmly.media.gles.filter.GPUImageFilter;
import com.xmly.media.gles.filter.GPUImageFilterFactory;
import com.xmly.media.gles.filter.GPUImageOESInputFilter;
import com.xmly.media.gles.filter.GPUImagePIPFilter;
import com.xmly.media.gles.filter.GPUImageYUY2PixelCopierFilter;
import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.gles.utils.OpenGlUtils;
import com.xmly.media.gles.utils.Rotation;
import com.xmly.media.gles.utils.TextureRotationUtil;
import com.xmly.media.gles.utils.XMFilterType;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by sunyc on 19-4-3.
 */

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class XMDecoderRenderer extends XMBaseRenderer {
    private static final String TAG = "XMDecoderRenderer";
    private XMFilterType mFilterType = XMFilterType.NONE;
    private ISurfacePreparedListener onSurfacePreparedListener;
    private int mVideoWidth; //Faster encoding when it is even
    private int mVideoHeight;
    private volatile int mDrawFrameNums = 0;

    public XMDecoderRenderer(final Context context, XMMediaRecorder recorder) {
        super(context, recorder);
        releaseFilters();
        initFilterArrays();
        mFilterArrays.put(RenderIndex.DecoderIndex, new GPUImageOESInputFilter());
        mFilterArrays.put(RenderIndex.PipIndex, new GPUImagePIPFilter());
        mFilterArrays.put(RenderIndex.FilterIndex, GPUImageFilterFactory.CreateFilter(mFilterType));
        mFilterArrays.put(RenderIndex.DownloadIndex, new GPUImageYUY2PixelCopierFilter(recorder));
    }

    @Override
    public void onSurfaceCreated(final GL10 unused, final EGLConfig config) {
        super.onSurfaceCreated(unused, config);
        initFilters();
    }

    @Override
    public void onSurfaceChanged(final GL10 gl, final int width, final int height) {
        super.onSurfaceChanged(gl, width, height);
        filtersSizeChanged();

        adjustImageScaling(mVideoWidth, mVideoHeight, mOutputWidth, mOutputHeight,
                Rotation.NORMAL, false, false, mGLTextureBuffer);
    }

    public int pipImageDrawToTexture() {
        return OpenGlUtils.NO_TEXTURE;
    }

    @Override
    public void onDrawFrame(final GL10 gl) {
        super.onDrawFrame(gl);

        int texture = OpenGlUtils.NO_TEXTURE, templateTex = OpenGlUtils.NO_TEXTURE, pipTex = OpenGlUtils.NO_TEXTURE;
        mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);
        if(mFilterArrays.get(RenderIndex.DecoderIndex) != null) {
            templateTex = mFilterArrays.get(RenderIndex.DecoderIndex).onDrawToTexture(mGLTextureId, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
        }
        pipTex = pipImageDrawToTexture();

        if(pipTex != OpenGlUtils.NO_TEXTURE && templateTex != OpenGlUtils.NO_TEXTURE) {
            if(mFilterArrays.get(RenderIndex.PipIndex) != null) {
                mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, false)).position(0);
                ((GPUImagePIPFilter) mFilterArrays.get(RenderIndex.PipIndex)).setSecondTexture(templateTex, false);
                texture = mFilterArrays.get(RenderIndex.PipIndex).onDrawToTexture(pipTex, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
            }
        } else {
            texture = templateTex;
            mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);
        }

        if(mFilterArrays.get(RenderIndex.FilterIndex) != null) {
            texture = mFilterArrays.get(RenderIndex.FilterIndex).onDrawToTexture(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
        }

        if (mGPUCopierEnable) {
            mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);
            if (mFilterArrays.get(RenderIndex.DownloadIndex) != null) {
                mFilterArrays.get(RenderIndex.DownloadIndex).onDrawToTexture(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
            }
        }

        synchronized (this) {
            if (mSurfaceTexture != null && updateTexImage) {
                mSurfaceTexture.updateTexImage();
                updateTexImage = false;
            }
        }

        addDrawFrameNums();
    }

    @Override
    synchronized public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        requestRender();
        updateTexImage = true;
    }

    @Override
    public void setFilter(final XMFilterType filtertype) {
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                if (mFilterArrays.get(RenderIndex.FilterIndex) != null) {
                    mFilterArrays.get(RenderIndex.FilterIndex).destroy();
                    mFilterArrays.put(RenderIndex.FilterIndex, null);
                }
                GPUImageFilter filter = GPUImageFilterFactory.CreateFilter(filtertype);
                filter.init();
                GLES20.glUseProgram(filter.getProgram());
                filter.onInputSizeChanged(mVideoWidth, mVideoHeight);
                filter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
                mFilterArrays.put(RenderIndex.FilterIndex, filter);
            }
        });
        mFilterType = filtertype;
        requestRender();
    }

    public void onVideoSizeChanged(int width, int height) {
        mVideoWidth = width;
        mVideoHeight = height;
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                filtersSizeChanged();
            }
        });

        adjustImageScaling(mVideoWidth, mVideoHeight, mOutputWidth, mOutputHeight,
                Rotation.NORMAL, false, false, mGLTextureBuffer);
    }

    public void setPipRectCoordinate(float[] buffer) {
        if(mFilterArrays.get(RenderIndex.PipIndex) != null) {
            mFilterArrays.get(RenderIndex.PipIndex).setRectangleCoordinate(buffer);
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void prepareVideoSurface() {
        setDrawFrameNums(0);
        if (mGLTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, new int[]{mGLTextureId}, 0);
            mGLTextureId = OpenGlUtils.NO_TEXTURE;
        }
        mGLTextureId = OpenGlUtils.getTexturesID();
        mSurfaceTexture = new SurfaceTexture(mGLTextureId);
        mSurfaceTexture.setOnFrameAvailableListener(this);

        Surface surface = new Surface(mSurfaceTexture);
        onSurfacePreparedListener.surfacePrepared(surface);
    }

    public void setSurfacePreparedListener(ISurfacePreparedListener onSurfacePreparedListener) {
        this.onSurfacePreparedListener = onSurfacePreparedListener;
    }

    public synchronized int getDrawFrameNums() {
        return mDrawFrameNums;
    }

    private synchronized void setDrawFrameNums(int num) {
        mDrawFrameNums = num;
    }

    private synchronized void addDrawFrameNums() {
        mDrawFrameNums ++;
    }

    private void filtersSizeChanged() {
        if (mFilterArrays.get(RenderIndex.DecoderIndex) != null) {
            mFilterArrays.get(RenderIndex.DecoderIndex).onOutputSizeChanged(mVideoWidth, mVideoHeight);
        }
        if (mFilterArrays.get(RenderIndex.PipIndex) != null) {
            mFilterArrays.get(RenderIndex.PipIndex).onOutputSizeChanged(mVideoWidth, mVideoHeight);
        }
        if (mFilterArrays.get(RenderIndex.FilterIndex) != null) {
            mFilterArrays.get(RenderIndex.FilterIndex).onInputSizeChanged(mVideoWidth, mVideoHeight);
            mFilterArrays.get(RenderIndex.FilterIndex).onOutputSizeChanged(mVideoWidth, mVideoHeight);
        }
        if (mFilterArrays.get(RenderIndex.DownloadIndex) != null) {
            mFilterArrays.get(RenderIndex.DownloadIndex).onOutputSizeChanged(mVideoWidth, mVideoHeight);
        }
    }

    @Override
    public void release() {
        super.release();
    }
}
