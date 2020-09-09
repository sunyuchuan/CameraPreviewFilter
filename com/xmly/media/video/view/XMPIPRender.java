package com.xmly.media.video.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Build;
import android.view.Surface;

import com.xmly.media.gles.filter.GPUImageOESInputFilter;
import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.gles.utils.OpenGlUtils;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class XMPIPRender extends XMDecoderRenderer {
    private static final String TAG = "XMPIPRender";
    private int mPipGLTextureId = OpenGlUtils.NO_TEXTURE;
    private int outputTexId = OpenGlUtils.NO_TEXTURE;
    private SurfaceTexture mPipSurfaceTexture = null;
    private ISurfacePreparedListener onPipSurfacePreparedListener;
    private int mPipVideoWidth;
    private int mPipVideoHeight;
    private volatile int updateTexImageCounter = 0;
    private volatile int updateTexImageCompare = 0;
    private volatile boolean mIsStopRequested = false;
    private volatile boolean drawToTexture = false;

    public void init() {
        updateTexImageCounter = 0;
        updateTexImageCompare = 0;
        mIsStopRequested = false;
        outputTexId = OpenGlUtils.NO_TEXTURE;
    }

    public XMPIPRender(Context context, XMMediaRecorder recorder) {
        super(context, recorder);
        mFilterArrays.put(RenderIndex.DecoderPipIndex, new GPUImageOESInputFilter());
        init();
    }

    @Override
    public void onSurfaceCreated(final GL10 unused, final EGLConfig config) {
        super.onSurfaceCreated(unused, config);
    }

    @Override
    public void onSurfaceChanged(final GL10 gl, final int width, final int height) {
        super.onSurfaceChanged(gl, width, height);
        filtersSizeChanged();
    }

    @Override
    public int pipImageDrawToTexture() {
        if (mIsStopRequested) {
            return OpenGlUtils.NO_TEXTURE;
        }

        synchronized (this) {
            while (mPipSurfaceTexture != null && updateTexImageCounter != updateTexImageCompare) {
                mPipSurfaceTexture.updateTexImage();
                updateTexImageCompare ++;
                drawToTexture = true;
            }
        }

        if (drawToTexture) {
            drawToTexture = false;
            if (mFilterArrays.get(RenderIndex.DecoderPipIndex) != null) {
                outputTexId = mFilterArrays.get(RenderIndex.DecoderPipIndex).onDrawToTexture(mPipGLTextureId, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
            }
        }

        return outputTexId;
    }

    public void stopPipImageDraw() {
        synchronized (this) {
            mIsStopRequested = true;
        }

        if (mPipSurfaceTexture != null) {
            mPipSurfaceTexture.setOnFrameAvailableListener(null);
        }
    }

    public void onPipVideoSizeChanged(int width, int height) {
        mPipVideoWidth = width;
        mPipVideoHeight = height;
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                filtersSizeChanged();
            }
        });
    }

    private void filtersSizeChanged() {
        if (mFilterArrays.get(RenderIndex.DecoderPipIndex) != null) {
            mFilterArrays.get(RenderIndex.DecoderPipIndex).onOutputSizeChanged(mPipVideoWidth, mPipVideoHeight);
        }
    }

    public void release() {
        super.release();
        if (mPipGLTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, new int[]{mPipGLTextureId}, 0);
            mPipGLTextureId = OpenGlUtils.NO_TEXTURE;
        }

        if (mPipSurfaceTexture != null) {
            mPipSurfaceTexture.setOnFrameAvailableListener(null);
        }
        mPipSurfaceTexture = null;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void prepareVideoSurface() {
        super.prepareVideoSurface();

        if (mPipGLTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, new int[]{mPipGLTextureId}, 0);
            mPipGLTextureId = OpenGlUtils.NO_TEXTURE;
        }
        mPipGLTextureId = OpenGlUtils.getTexturesID();
        mPipSurfaceTexture = new SurfaceTexture(mPipGLTextureId);
        mPipSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            synchronized public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                updateTexImageCounter ++;
            }
        });

        Surface surface = new Surface(mPipSurfaceTexture);
        onPipSurfacePreparedListener.surfacePrepared(surface);
    }

    public void setPipSurfacePreparedListener(ISurfacePreparedListener onSurfacePreparedListener) {
        this.onPipSurfacePreparedListener = onSurfacePreparedListener;
    }
}
