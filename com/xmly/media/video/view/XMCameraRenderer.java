package com.xmly.media.video.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.Camera;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Build;

import com.xmly.media.gles.filter.GPUImageCameraInputFilter;
import com.xmly.media.gles.filter.GPUImageFilter;
import com.xmly.media.gles.filter.GPUImageFilterFactory;
import com.xmly.media.gles.filter.GPUImageYUY2PixelCopierFilter;
import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.gles.utils.OpenGlUtils;
import com.xmly.media.gles.utils.Rotation;
import com.xmly.media.gles.utils.TextureRotationUtil;
import com.xmly.media.gles.utils.XMFilterType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.LinkedList;
import java.util.Queue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by sunyc on 19-3-1.
 */

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class XMCameraRenderer extends XMBaseRenderer {
    private static final String TAG = "XMCameraRenderer";
    private FloatBuffer mGLCameraTextureBuffer;
    private XMFilterType mFilterType = XMFilterType.NONE;
    private final Queue<Runnable> mCameraRunOnDraw;
    private int mCameraPreviewWidth = 960;
    private int mCameraPreviewHeight = 540;
    private int mCameraOutputWidth = 960;
    private int mCameraOutputHeight = 540;
    private int mCameraOuptutFps = 15;
    private Rotation mRotation = Rotation.NORMAL;
    private boolean mFlipHorizontal = false;
    private boolean mFlipVertical = false;

    private void initBuffer() {
        mGLCameraTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLCameraTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);
    }

    public XMCameraRenderer(final Context context, XMMediaRecorder recorder) {
        super(context, recorder);
        releaseFilters();
        initFilterArrays();
        mFilterArrays.put(RenderIndex.CameraIndex, new GPUImageCameraInputFilter());
        mFilterArrays.put(RenderIndex.RotateIndex, GPUImageFilterFactory.CreateFilter(XMFilterType.NONE));
        mFilterArrays.put(RenderIndex.FilterIndex, GPUImageFilterFactory.CreateFilter(mFilterType));
        mFilterArrays.put(RenderIndex.DisplayIndex, GPUImageFilterFactory.CreateFilter(XMFilterType.NONE));
        mFilterArrays.put(RenderIndex.DownloadIndex, new GPUImageYUY2PixelCopierFilter(mRecorder));

        mCameraRunOnDraw = new LinkedList<Runnable>();
        initBuffer();
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

        adjustImageScaling(mCameraPreviewWidth, mCameraPreviewHeight, mCameraOutputWidth, mCameraOutputHeight,
                mRotation, mFlipHorizontal, mFlipVertical, mGLCameraTextureBuffer);

        adjustImageScaling(mCameraOutputWidth, mCameraOutputHeight, mOutputWidth, mOutputHeight,
                Rotation.NORMAL, false, false, mGLTextureBuffer);
    }

    @Override
    public void onDrawFrame(final GL10 gl) {
        super.onDrawFrame(gl);
        runAll(mCameraRunOnDraw);

        if (mSurfaceTexture != null) {
            float[] mtx = new float[16];
            int cameraTex = OpenGlUtils.NO_TEXTURE;
            mSurfaceTexture.getTransformMatrix(mtx);

            if (mFilterArrays.get(RenderIndex.CameraIndex) != null) {
                ((GPUImageCameraInputFilter) mFilterArrays.get(RenderIndex.CameraIndex)).setTextureTransformMatrix(mtx);
                cameraTex = mFilterArrays.get(RenderIndex.CameraIndex).onDrawToTexture(mGLTextureId, mDefaultGLCubeBuffer, mGLCameraTextureBuffer);
            }

            mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, true, false)).position(0);
            if (mFilterArrays.get(RenderIndex.FilterIndex) != null) {
                cameraTex = mFilterArrays.get(RenderIndex.FilterIndex).onDrawToTexture(cameraTex, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
            }

            mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, false)).position(0);
            if (mFilterArrays.get(RenderIndex.RotateIndex) != null) {
                cameraTex = mFilterArrays.get(RenderIndex.RotateIndex).onDrawToTexture(cameraTex, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
            }

            if (mFilterArrays.get(RenderIndex.DisplayIndex) != null) {
                mFilterArrays.get(RenderIndex.DisplayIndex).onDraw(cameraTex, mDefaultGLCubeBuffer, mGLTextureBuffer);
            }

            if (mGPUCopierEnable) {
                mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);
                if (mFilterArrays.get(RenderIndex.DownloadIndex) != null) {
                    mFilterArrays.get(RenderIndex.DownloadIndex).onDrawToTexture(cameraTex, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
                }
            }
        }

        synchronized (this) {
            if (mSurfaceTexture != null && updateTexImage) {
                mSurfaceTexture.updateTexImage();
                updateTexImage = false;
            }
        }
    }

    @Override
    synchronized public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        requestRender();
        updateTexImage = true;
    }

    public void setUpCamera(final Camera camera, final int degrees, final boolean flipHorizontal,
                            final boolean flipVertical) {
        cleanAll(mCameraRunOnDraw);
        runOnDraw(mCameraRunOnDraw, new Runnable() {
            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public void run() {
                if (camera != null) {
                    synchronized (camera) {
                        final Camera.Size previewSize = camera.getParameters().getPreviewSize();
                        mCameraPreviewWidth = previewSize.width;
                        mCameraPreviewHeight = previewSize.height;
                        if (degrees == 270 || degrees == 90) {
                            mCameraOutputWidth = mCameraPreviewHeight;
                            mCameraOutputHeight = mCameraPreviewWidth;
                        } else {
                            mCameraOutputWidth = mCameraPreviewWidth;
                            mCameraOutputHeight = mCameraPreviewHeight;
                        }
                        filtersSizeChanged();

                        Rotation rotation = Rotation.NORMAL;
                        switch (degrees) {
                            case 90:
                                rotation = Rotation.ROTATION_90;
                                break;
                            case 180:
                                rotation = Rotation.ROTATION_180;
                                break;
                            case 270:
                                rotation = Rotation.ROTATION_270;
                                break;
                        }
                        setRotation(rotation, flipHorizontal, flipVertical);

                        if (mGLTextureId != OpenGlUtils.NO_TEXTURE) {
                            GLES20.glDeleteTextures(1, new int[]{mGLTextureId}, 0);
                            mGLTextureId = OpenGlUtils.NO_TEXTURE;
                        }
                        mGLTextureId = OpenGlUtils.getTexturesID();
                        mSurfaceTexture = new SurfaceTexture(mGLTextureId);
                        mSurfaceTexture.setOnFrameAvailableListener(XMCameraRenderer.this);
                        try {
                            camera.setPreviewTexture(mSurfaceTexture);
                            camera.startPreview();
                        } catch (IOException e) {
                            mListener.onPreviewError();
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        mListener.onPreviewStarted();
        requestRender();
    }

    public int getCameraOutputWidth() {
        return mCameraOutputWidth;
    }

    public int getCameraOutputHeight() {
        return mCameraOutputHeight;
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
                filter.onOutputSizeChanged(mCameraOutputWidth, mCameraOutputHeight);
                filter.onInputSizeChanged(mCameraOutputWidth, mCameraOutputHeight);
                mFilterArrays.put(RenderIndex.FilterIndex, filter);
            }
        });
        mFilterType = filtertype;
        requestRender();
    }

    private void setRotation(final Rotation rotation,
                             final boolean flipHorizontal, final boolean flipVertical) {
        mRotation = rotation;
        if(rotation == Rotation.ROTATION_90 || rotation == Rotation.ROTATION_270) {
            mFlipHorizontal = flipVertical;
            mFlipVertical = flipHorizontal;
        } else {
            mFlipHorizontal = flipHorizontal;
            mFlipVertical = flipVertical;
        }

        adjustImageScaling(mCameraPreviewWidth, mCameraPreviewHeight, mCameraOutputWidth, mCameraOutputHeight,
                mRotation, mFlipHorizontal, mFlipVertical, mGLCameraTextureBuffer);

        adjustImageScaling(mCameraOutputWidth, mCameraOutputHeight, mOutputWidth, mOutputHeight,
                Rotation.NORMAL, false, false, mGLTextureBuffer);
    }

    private void filtersSizeChanged() {
        if (mFilterArrays.get(RenderIndex.CameraIndex) != null) {
            mFilterArrays.get(RenderIndex.CameraIndex).onOutputSizeChanged(mCameraOutputWidth, mCameraOutputHeight);
        }
        if (mFilterArrays.get(RenderIndex.RotateIndex) != null) {
            mFilterArrays.get(RenderIndex.RotateIndex).onOutputSizeChanged(mCameraOutputWidth, mCameraOutputHeight);
        }
        if (mFilterArrays.get(RenderIndex.FilterIndex) != null) {
            mFilterArrays.get(RenderIndex.FilterIndex).onInputSizeChanged(mCameraOutputWidth, mCameraOutputHeight);
            mFilterArrays.get(RenderIndex.FilterIndex).onOutputSizeChanged(mCameraOutputWidth, mCameraOutputHeight);
        }
        if (mFilterArrays.get(RenderIndex.DisplayIndex) != null) {
            mFilterArrays.get(RenderIndex.DisplayIndex).onOutputSizeChanged(mOutputWidth, mOutputHeight);
        }
        if (mFilterArrays.get(RenderIndex.DownloadIndex) != null) {
            mFilterArrays.get(RenderIndex.DownloadIndex).onOutputSizeChanged(mCameraOutputWidth, mCameraOutputHeight);
        }
    }

    public void cleanRunOnSetupCamera() {
        if(mCameraRunOnDraw != null)
            cleanAll(mCameraRunOnDraw);
    }

    @Override
    public void release() {
        super.release();
    }
}
