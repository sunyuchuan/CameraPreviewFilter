package com.xmly.media.video.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Build;
import android.view.Surface;

import com.xmly.media.gles.filter.GPUImageCameraInputFilter;
import com.xmly.media.gles.filter.GPUImageFilter;
import com.xmly.media.gles.filter.GPUImageFilterFactory;
import com.xmly.media.gles.filter.GPUImageMixFilter;
import com.xmly.media.gles.filter.GPUImageOESInputFilter;
import com.xmly.media.gles.filter.GPUImagePIPFilter;
import com.xmly.media.gles.filter.GPUImageTwoInputFilter;
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
public class XMPlayerRenderer extends XMBaseRenderer {
    private static final String TAG = "XMPlayerRenderer";
    private FloatBuffer mCameraGLTextureBuffer;
    private SurfaceTexture mCameraSurfaceTexture = null;
    private int mCameraGLTextureId = OpenGlUtils.NO_TEXTURE;
    private XMFilterType mFilterType = XMFilterType.NONE;
    private ISurfacePreparedListener onSurfacePreparedListener;
    private final Queue<Runnable> mCameraRunOnDraw;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mCameraPreviewWidth = 960;
    private int mCameraPreviewHeight = 540;
    private int mCameraOuputWidth = 960;
    private int mCameraOuputHeight = 540;
    private float mCameraOutputAspectRatio = 960f/540f;
    private int mCameraOuptutFps = 15;
    private Rotation mRotation = Rotation.NORMAL;
    private boolean mFlipHorizontal = false;
    private boolean mFlipVertical = false;
    private volatile boolean mEnableSubtitle = false;

    private void initBuffer() {
        mCameraGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mCameraGLTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);
    }

    public XMPlayerRenderer(final Context context, XMMediaRecorder recorder) {
        super(context, recorder);

        releaseFilters();
        initFilterArrays();
        mFilterArrays.put(RenderIndex.CameraIndex, new GPUImageCameraInputFilter());
        mFilterArrays.put(RenderIndex.FilterIndex, GPUImageFilterFactory.CreateFilter(mFilterType));
        mFilterArrays.put(RenderIndex.DecoderIndex, new GPUImageOESInputFilter());
        mFilterArrays.put(RenderIndex.PipIndex, new GPUImagePIPFilter());
        mFilterArrays.put(RenderIndex.MixIndex, new GPUImageMixFilter());
        mFilterArrays.put(RenderIndex.RotateIndex, GPUImageFilterFactory.CreateFilter(XMFilterType.NONE));
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
        mCameraOuputWidth = (int) (mCameraOuputHeight * mCameraOutputAspectRatio);
        filtersSizeChanged();

        adjustImageScaling(mCameraPreviewWidth, mCameraPreviewHeight, mCameraOuputWidth, mCameraOuputHeight,
                mRotation, mFlipHorizontal, mFlipVertical, mCameraGLTextureBuffer);
        adjustImageScaling(mVideoWidth, mVideoHeight, mOutputWidth, mOutputHeight,
                Rotation.NORMAL, false, false, mGLTextureBuffer);
    }

    @Override
    public void onDrawFrame(final GL10 gl) {
        super.onDrawFrame(gl);
        runAll(mCameraRunOnDraw);

        float[] mtx = new float[16];
        int cameraTex = OpenGlUtils.NO_TEXTURE, texture = OpenGlUtils.NO_TEXTURE;
        if (mCameraSurfaceTexture != null) {
            if (mFilterArrays.get(RenderIndex.CameraIndex) != null) {
                mCameraSurfaceTexture.getTransformMatrix(mtx);
                ((GPUImageCameraInputFilter) mFilterArrays.get(RenderIndex.CameraIndex)).setTextureTransformMatrix(mtx);
                cameraTex = mFilterArrays.get(RenderIndex.CameraIndex).onDrawToTexture(mCameraGLTextureId, mDefaultGLCubeBuffer, mCameraGLTextureBuffer);
            }

            mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.ROTATION_180, false, false)).position(0);
            if (mFilterArrays.get(RenderIndex.FilterIndex) != null) {
                cameraTex = mFilterArrays.get(RenderIndex.FilterIndex).onDrawToTexture(cameraTex, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
            }
        }

        if (mSurfaceTexture != null) {
            mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.ROTATION_180, true, false)).position(0);
            if (mFilterArrays.get(RenderIndex.DecoderIndex) != null) {
                texture = mFilterArrays.get(RenderIndex.DecoderIndex).onDrawToTexture(mGLTextureId, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
            }

            synchronized (this) {
                if (mEnableSubtitle) {
                    mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, false)).position(0);
                    if (mFilterArrays.get(RenderIndex.MixIndex) != null) {
                        texture = mFilterArrays.get(RenderIndex.MixIndex).onDrawToTexture(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
                    }
                    if (mFilterArrays.get(RenderIndex.RotateIndex) != null) {
                        texture = mFilterArrays.get(RenderIndex.RotateIndex).onDrawToTexture(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
                    }
                }
            }
        }

        if (mCameraSurfaceTexture != null && texture != OpenGlUtils.NO_TEXTURE) {
            mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, false)).position(0);
            if (mFilterArrays.get(RenderIndex.PipIndex) != null) {
                ((GPUImageTwoInputFilter) mFilterArrays.get(RenderIndex.PipIndex)).setSecondTexture(texture, false);
                texture = mFilterArrays.get(RenderIndex.PipIndex).onDrawToTexture(cameraTex, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
            }
            if (mFilterArrays.get(RenderIndex.RotateIndex) != null) {
                texture = mFilterArrays.get(RenderIndex.RotateIndex).onDrawToTexture(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
            }
        }

        if (texture == OpenGlUtils.NO_TEXTURE) {
            mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, false)).position(0);
            if (mFilterArrays.get(RenderIndex.DisplayIndex) != null) {
                mFilterArrays.get(RenderIndex.DisplayIndex).onDraw(cameraTex, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
            }
        } else {
            if (mFilterArrays.get(RenderIndex.DisplayIndex) != null) {
                mFilterArrays.get(RenderIndex.DisplayIndex).onDraw(texture, mGLCubeBuffer, mGLTextureBuffer);
            }
        }

        if (mGPUCopierEnable) {
            mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);
            if (mFilterArrays.get(RenderIndex.DownloadIndex) != null) {
                mFilterArrays.get(RenderIndex.DownloadIndex).onDrawToTexture(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
            }
        }

        synchronized (this) {
            if (mCameraSurfaceTexture == null) {
                if (mSurfaceTexture != null && updateTexImage) {
                    mSurfaceTexture.updateTexImage();
                    updateTexImage = false;
                }
            } else {
                if (updateTexImage) {
                    if (mSurfaceTexture != null) {
                        mSurfaceTexture.updateTexImage();
                    }
                    mCameraSurfaceTexture.updateTexImage();
                    updateTexImage = false;
                }
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
                        mCameraOuputHeight = mCameraPreviewHeight;
                        mCameraOuputWidth = (int) (mCameraOuputHeight * mCameraOutputAspectRatio);
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

                        if (mCameraGLTextureId != OpenGlUtils.NO_TEXTURE) {
                            GLES20.glDeleteTextures(1, new int[]{mCameraGLTextureId}, 0);
                            mCameraGLTextureId = OpenGlUtils.NO_TEXTURE;
                        }
                        mCameraGLTextureId = OpenGlUtils.getTexturesID();
                        mCameraSurfaceTexture = new SurfaceTexture(mCameraGLTextureId);
                        if (mSurfaceTexture != null) {
                            mSurfaceTexture.setOnFrameAvailableListener(null);
                        }
                        mCameraSurfaceTexture.setOnFrameAvailableListener(XMPlayerRenderer.this);
                        try {
                            camera.setPreviewTexture(mCameraSurfaceTexture);
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

    public void setCameraOutputAspectRatio(float aspectRatio) {
        mCameraOutputAspectRatio = aspectRatio;
    }

    public void setPipRectCoordinate(float[] buffer) {
        if(mFilterArrays.get(RenderIndex.PipIndex) != null) {
            mFilterArrays.get(RenderIndex.PipIndex).setRectangleCoordinate(buffer);
        }
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
                filter.onInputSizeChanged(mCameraOuputWidth, mCameraOuputHeight);
                filter.onOutputSizeChanged(mCameraOuputWidth, mCameraOuputHeight);
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

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void prepareVideoSurface() {
        if (mGLTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, new int[]{mGLTextureId}, 0);
            mGLTextureId = OpenGlUtils.NO_TEXTURE;
        }
        mGLTextureId = OpenGlUtils.getTexturesID();
        mSurfaceTexture = new SurfaceTexture(mGLTextureId);
        if (mCameraSurfaceTexture == null) {
            mSurfaceTexture.setOnFrameAvailableListener(this);
        }

        Surface surface = new Surface(mSurfaceTexture);
        onSurfacePreparedListener.surfacePrepared(surface);
    }

    public void setSurfacePreparedListener(ISurfacePreparedListener onSurfacePreparedListener) {
        this.onSurfacePreparedListener = onSurfacePreparedListener;
    }

    public void loadSubBitmap(final Bitmap bitmap) {
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                synchronized (this) {
                    if (mFilterArrays.get(RenderIndex.MixIndex) != null) {
                        ((GPUImageMixFilter) mFilterArrays.get(RenderIndex.MixIndex)).setBitmap(bitmap);
                    }
                    mEnableSubtitle = true;
                }
            }
        });
    }

    public void stopSubtitle() {
        synchronized (this) {
            mEnableSubtitle = false;
        }
    }

    public void releaseCamera() {
        if (mCameraSurfaceTexture != null) {
            mCameraSurfaceTexture.setOnFrameAvailableListener(null);
        }
        mCameraSurfaceTexture = null;

        if (mSurfaceTexture != null) {
            mSurfaceTexture.setOnFrameAvailableListener(this);
            requestRender();
        }

        if (mCameraGLTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, new int[]{mCameraGLTextureId}, 0);
            mCameraGLTextureId = OpenGlUtils.NO_TEXTURE;
        }
    }

    private void filtersSizeChanged() {
        if (mFilterArrays.get(RenderIndex.CameraIndex) != null) {
            mFilterArrays.get(RenderIndex.CameraIndex).onOutputSizeChanged(mCameraOuputWidth, mCameraOuputHeight);
        }
        if (mFilterArrays.get(RenderIndex.FilterIndex) != null) {
            mFilterArrays.get(RenderIndex.FilterIndex).onInputSizeChanged(mCameraOuputWidth, mCameraOuputHeight);
            mFilterArrays.get(RenderIndex.FilterIndex).onOutputSizeChanged(mCameraOuputWidth, mCameraOuputHeight);
        }
        if (mFilterArrays.get(RenderIndex.DecoderIndex) != null) {
            mFilterArrays.get(RenderIndex.DecoderIndex).onOutputSizeChanged(mVideoWidth, mVideoHeight);
        }
        if (mFilterArrays.get(RenderIndex.PipIndex) != null) {
            mFilterArrays.get(RenderIndex.PipIndex).onOutputSizeChanged(mVideoWidth, mVideoHeight);
        }
        if (mFilterArrays.get(RenderIndex.MixIndex) != null) {
            mFilterArrays.get(RenderIndex.MixIndex).onOutputSizeChanged(mVideoWidth, mVideoHeight);
        }
        if (mFilterArrays.get(RenderIndex.RotateIndex) != null) {
            mFilterArrays.get(RenderIndex.RotateIndex).onOutputSizeChanged(mVideoWidth, mVideoHeight);
        }
        if (mFilterArrays.get(RenderIndex.DisplayIndex) != null) {
            mFilterArrays.get(RenderIndex.DisplayIndex).onOutputSizeChanged(mOutputWidth, mOutputHeight);
        }
        if (mFilterArrays.get(RenderIndex.DownloadIndex) != null) {
            mFilterArrays.get(RenderIndex.DownloadIndex).onOutputSizeChanged(mVideoWidth, mVideoHeight);
        }
    }

    public void releaseVideoSurface() {
        super.release();
    }

    @Override
    public void release() {
        releaseVideoSurface();
        releaseCamera();
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

        adjustImageScaling(mCameraPreviewWidth, mCameraPreviewHeight, mCameraOuputWidth, mCameraOuputHeight,
                mRotation, mFlipHorizontal, mFlipVertical, mCameraGLTextureBuffer);
    }

    public void cleanRunOnSetupCamera() {
        if(mCameraRunOnDraw != null)
            cleanAll(mCameraRunOnDraw);
    }
}
