/*
 * Copyright (C) 2012 CyberAgent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xmly.media.camera.view.gpuimage;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.Log;

import com.xmly.media.camera.view.encoder.ImageEncoderCore;
import com.xmly.media.camera.view.encoder.TextureMovieEncoder;
import com.xmly.media.gles.filter.GPUImageCameraInputFilter;
import com.xmly.media.gles.filter.GPUImageFilter;
import com.xmly.media.gles.filter.GPUImageFilterFactory;
import com.xmly.media.camera.view.recorder.IXMCameraRecorderListener;
import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.gles.utils.GPUImageParams;
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

public class GPUImageRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "GPUImageRenderer";
    private Context mContext = null;
    private GLSurfaceView mGlSurfaceView = null;
    private GPUImageFilter mFilter = null;
    private SurfaceTexture mSurfaceTexture = null;
    private int mGLTextureId = OpenGlUtils.NO_TEXTURE;
    private final FloatBuffer mGLCubeBuffer;
    private final FloatBuffer mGLTextureBuffer;
    private final FloatBuffer mDefaultGLCubeBuffer;
    private final FloatBuffer mDefaultGLTextureBuffer;
    private int mOutputWidth;
    private int mOutputHeight;
    private int mImageWidth;
    private int mImageHeight;
    private final Queue<Runnable> mRunOnDraw;
    private final Queue<Runnable> mRunSetFilter;
    private Rotation mRotation;
    private boolean mFlipHorizontal;
    private boolean mFlipVertical;
    private ScaleType mScaleType = ScaleType.CENTER_CROP;
    private boolean updateTexImage = false;
    private IXMCameraRecorderListener mListener = null;
    private XMFilterType mFilterType = XMFilterType.NONE;
    private static final int RECORDING_OFF = 0;
    private static final int RECORDING_ON = 1;
    private static final int RECORDING_RESUMED = 2;
    private TextureMovieEncoder mVideoEncoder = null;
    private GPUImageCameraInputFilter mCameraInputFilter = null;
    //private GPUImageFilter mRenderFilter = null;
    private volatile boolean mEncoderEnabled = false;
    private int mEncoderStatus = RECORDING_OFF;

    public static final float CUBE[] = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f,
    };

    private boolean supportsOpenGLES2(final Context context) {
        final ActivityManager activityManager = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        final ConfigurationInfo configurationInfo =
                activityManager.getDeviceConfigurationInfo();
        return configurationInfo.reqGlEsVersion >= 0x20000;
    }

    public GPUImageRenderer(final Context context, XMMediaRecorder recorder) {
        if (!supportsOpenGLES2(context)) {
            throw new IllegalStateException("OpenGL ES 2.0 is not supported on this phone.");
        }

        mContext = context;
        GPUImageParams.context = context;

        filterCreate();
        mRunOnDraw = new LinkedList<Runnable>();
        mRunSetFilter = new LinkedList<Runnable>();

        mGLCubeBuffer = ByteBuffer.allocateDirect(CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLCubeBuffer.put(CUBE).position(0);

        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);
        setRotation(Rotation.NORMAL, false, false);

        mDefaultGLCubeBuffer = ByteBuffer.allocateDirect(CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mDefaultGLCubeBuffer.put(CUBE).position(0);
        float[] flipTexture = TextureRotationUtil.getRotation(Rotation.NORMAL, true, false);
        mDefaultGLTextureBuffer = ByteBuffer.allocateDirect(flipTexture.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mDefaultGLTextureBuffer.put(flipTexture).position(0);

        mVideoEncoder = new TextureMovieEncoder(recorder);
    }

    public void setGLSurfaceView(final GLSurfaceView view) {
        mGlSurfaceView = view;
        mGlSurfaceView.setEGLContextClientVersion(2);
        mGlSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        mGlSurfaceView.getHolder().setFormat(PixelFormat.RGBA_8888);
        mGlSurfaceView.setRenderer(this);
        mGlSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        mGlSurfaceView.requestRender();
    }

    public void setListener(IXMCameraRecorderListener l) {
        mListener = l;
        mVideoEncoder.setListener(l);
    }

    public void setOnImageEncoderListener(ImageEncoderCore.OnImageEncoderListener l) {
        mVideoEncoder.setOnImageEncoderListener(l);
    }

    @Override
    public void onSurfaceCreated(final GL10 unused, final EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        filterInit();

        mEncoderEnabled = mVideoEncoder.isRecording();
        if (mEncoderEnabled)
            mEncoderStatus = RECORDING_RESUMED;
        else
            mEncoderStatus = RECORDING_OFF;
    }

    @Override
    public void onSurfaceChanged(final GL10 gl, final int width, final int height) {
        mOutputWidth = align(width, 2);
        mOutputHeight = align(height, 2);
        Log.i(TAG, "onSurfaceChanged mOutputWidth "+ mOutputWidth + " mOutputHeight " +mOutputHeight);
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
        GLES20.glUseProgram(mFilter.getProgram());
        filterOutputSizeChanged();
        adjustImageScaling();
    }

    @Override
    public void onDrawFrame(final GL10 gl) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        runAll(mRunOnDraw);

        synchronized (mVideoEncoder) {
            runAll(mRunSetFilter);
            setupVideoEncoder(mVideoEncoder, mEncoderEnabled, mEncoderStatus);

            float[] mtx = new float[16];
            if (mSurfaceTexture != null) {
                mSurfaceTexture.getTransformMatrix(mtx);
            }
            int textureId = glDraw(mtx);
            encoderDraw(mVideoEncoder, mSurfaceTexture, textureId);
        }

        synchronized(this) {
            if (mSurfaceTexture != null && updateTexImage) {
                mSurfaceTexture.updateTexImage();
                updateTexImage = false;
            }
        }
    }

    public void setUpCamera(final Camera camera) {
        setUpCamera(camera, 0, false, false);
    }

    public void setUpCamera(final Camera camera, final int degrees, final boolean flipHorizontal,
            final boolean flipVertical) {
        cleanAll(mRunOnDraw);
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                if (camera != null) {
                    synchronized (camera) {
                        final Camera.Size previewSize = camera.getParameters().getPreviewSize();
                        mImageWidth = previewSize.width;
                        mImageHeight = previewSize.height;
                        if(mCameraInputFilter != null) {
                            mCameraInputFilter.onOutputSizeChanged(mImageWidth, mImageHeight);
                        } else {
                            Log.e(TAG, "mCameraInputFilter is null");
                        }
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
                        setRotationParam(rotation, flipHorizontal, flipVertical);

                        if (mGLTextureId != OpenGlUtils.NO_TEXTURE) {
                            GLES20.glDeleteTextures(1, new int[]{mGLTextureId}, 0);
                            mGLTextureId = OpenGlUtils.NO_TEXTURE;
                        }

                        mGLTextureId = OpenGlUtils.getTexturesID();
                        mSurfaceTexture = new SurfaceTexture(mGLTextureId);
                        mSurfaceTexture.setOnFrameAvailableListener(GPUImageRenderer.this);
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

    public void setFilter(final XMFilterType filtertype) {
        runOnDraw(mRunSetFilter, new Runnable() {
            @Override
            public void run() {
                if (mFilter != null) {
                    mFilter.destroy();
                    Log.i(TAG, "setFilter");
                }
                mFilter = GPUImageFilterFactory.CreateFilter(filtertype);
                mFilter.init();
                GLES20.glUseProgram(mFilter.getProgram());
                mFilter.onOutputSizeChanged(mOutputWidth, mOutputHeight);

                /*if(mCameraInputFilter!=null)
                    mCameraInputFilter.destroy();
                mCameraInputFilter = new GPUImageCameraInputFilter();
                mCameraInputFilter.init();
                mCameraInputFilter.onOutputSizeChanged(mOutputWidth, mOutputHeight);
                mCameraInputFilter.initCameraFrameBuffer(mImageWidth, mImageHeight);
                if(mRenderFilter!=null)
                    mRenderFilter.destroy();
                mRenderFilter = GPUImageFilterFactory.CreateFilter(XMFilterType.NONE);
                mRenderFilter.init();
                mRenderFilter.onOutputSizeChanged(mOutputWidth, mOutputHeight);*/
            }
        });
        mFilterType = filtertype;
        mVideoEncoder.setFilter(filtertype, mOutputWidth, mOutputHeight);
        requestRender();
    }

    public void onStop()
    {
        mEncoderEnabled = false;
        if(mGLTextureId != OpenGlUtils.NO_TEXTURE)
        {
            GLES20.glDeleteTextures(1, new int[]{ mGLTextureId }, 0);
            mGLTextureId = OpenGlUtils.NO_TEXTURE;
        }
        if(mVideoEncoder != null)
            mVideoEncoder.stopRecording();
    }

    public void release() {
        onStop();
        if(mCameraInputFilter != null)
            mCameraInputFilter.destroy();
        if(mFilter != null)
            mFilter.destroy();
    }

    public void changeVideoEncoderStatus(boolean isEncoding)
    {
        mEncoderEnabled = isEncoding;
    }

    public void startPutData(boolean isPutting)
    {
        if(mVideoEncoder != null)
            mVideoEncoder.startPutData(isPutting);
    }

    @Override
    synchronized public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        requestRender();
        updateTexImage = true;
    }

    private void filterCreate()
    {
        if(mCameraInputFilter != null)
            mCameraInputFilter.destroy();
        mCameraInputFilter = new GPUImageCameraInputFilter();
        if(mFilter != null)
            mFilter.destroy();
        mFilter = GPUImageFilterFactory.CreateFilter(XMFilterType.NONE);
        /*if(mRenderFilter != null)
            mRenderFilter.destroy();
        mRenderFilter = GPUImageFilterFactory.CreateFilter(XMFilterType.NONE);*/
    }

    private void filterInit()
    {
        mCameraInputFilter.init();
        mFilter.init();
        //mRenderFilter.init();
    }

    private void filterOutputSizeChanged() {
        mCameraInputFilter.onOutputSizeChanged(mImageWidth, mImageHeight);
        mFilter.onOutputSizeChanged(mOutputWidth, mOutputHeight);
        //mRenderFilter.onOutputSizeChanged(mOutputWidth, mOutputHeight);
    }

    private void setupVideoEncoder(TextureMovieEncoder encoder, boolean enable, int status) {
        if(encoder == null)
            return;

        if (enable) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                Log.e(TAG, "unsupport, because SDK version less than Build.VERSION_CODES.KITKAT");
                mListener.onRecorderError();
                return;
            }
            switch (status) {
                case RECORDING_OFF:
                    encoder.setPreviewSize(mImageWidth, mImageHeight);
                    encoder.setCubeBuffer(mDefaultGLCubeBuffer);
                    encoder.setTextureBuffer(mDefaultGLTextureBuffer);
                    encoder.setFilter(mFilterType, mOutputWidth, mOutputHeight);
                    encoder.startRecording(new TextureMovieEncoder.EncoderConfig(
                            mOutputWidth, mOutputHeight,
                            EGL14.eglGetCurrentContext()));
                    mEncoderStatus = RECORDING_ON;
                    break;
                case RECORDING_RESUMED:
                    encoder.updateSharedContext(EGL14.eglGetCurrentContext());
                    mEncoderStatus = RECORDING_ON;
                    break;
                case RECORDING_ON:
                    break;
                default:
                    throw new RuntimeException("unknown status " + status);
            }
        } else {
            switch (status) {
                case RECORDING_ON:
                case RECORDING_RESUMED:
                    encoder.stopRecording();
                    mEncoderStatus = RECORDING_OFF;
                    break;
                case RECORDING_OFF:
                    break;
                default:
                    throw new RuntimeException("unknown status " + status);
            }
        }
    }

    private int glDraw(float[] mtx)
    {
        int filterInputTexture = -1, renderFilterInputTexture = -1;
        mCameraInputFilter.setTextureTransformMatrix(mtx);
        mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, true, false)).position(0);
        filterInputTexture = mCameraInputFilter.onDrawToTexture(mGLTextureId, mGLCubeBuffer, mGLTextureBuffer);

        mFilter.onDraw(filterInputTexture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);

        //renderFilterInputTexture = mFilter.onDrawToTexture(filterInputTexture, mGLCubeBuffer, mGLTextureBuffer);

        //mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.ROTATION_180, false, false)).position(0);
        //mRenderFilter.onDraw(renderFilterInputTexture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);

        return filterInputTexture;
    }

    private void encoderDraw(TextureMovieEncoder encoder, SurfaceTexture st, int textureId) {
        if(encoder == null || st == null)
            return;
        encoder.setTextureId(textureId);
        encoder.frameAvailable(st);
    }

    private void setRotationParam(final Rotation rotation, final boolean flipHorizontal,
                                  final boolean flipVertical) {
        if(rotation == Rotation.ROTATION_90 || rotation == Rotation.ROTATION_270) {
            setRotation(rotation, flipVertical, flipHorizontal);
        } else {
            setRotation(rotation, flipHorizontal, flipVertical);
        }
    }

    private void setRotation(final Rotation rotation,
                             final boolean flipHorizontal, final boolean flipVertical) {
        mFlipHorizontal = flipHorizontal;
        mFlipVertical = flipVertical;
        mRotation = rotation;
        adjustImageScaling();
    }

    private void adjustImageScaling() {
        float outputWidth = mOutputWidth;
        float outputHeight = mOutputHeight;
        if (mRotation == Rotation.ROTATION_270 || mRotation == Rotation.ROTATION_90) {
            outputWidth = mOutputHeight;
            outputHeight = mOutputWidth;
        }

        float ratio1 = outputWidth / mImageWidth;
        float ratio2 = outputHeight / mImageHeight;
        float ratioMax = Math.max(ratio1, ratio2);
        int imageWidthNew = Math.round(mImageWidth * ratioMax);
        int imageHeightNew = Math.round(mImageHeight * ratioMax);

        float ratioWidth = imageWidthNew / outputWidth;
        float ratioHeight = imageHeightNew / outputHeight;

        float[] cube = CUBE;
        float[] textureCords = TextureRotationUtil.getRotation(mRotation, mFlipHorizontal, mFlipVertical);
        if (mScaleType == ScaleType.CENTER_CROP) {
            float distHorizontal = (1 - 1 / ratioWidth) / 2;
            float distVertical = (1 - 1 / ratioHeight) / 2;
            textureCords = new float[]{
                    addDistance(textureCords[0], distHorizontal), addDistance(textureCords[1], distVertical),
                    addDistance(textureCords[2], distHorizontal), addDistance(textureCords[3], distVertical),
                    addDistance(textureCords[4], distHorizontal), addDistance(textureCords[5], distVertical),
                    addDistance(textureCords[6], distHorizontal), addDistance(textureCords[7], distVertical),
            };
        } else {
            cube = new float[]{
                    cube[0] / ratioHeight, cube[1] / ratioWidth,
                    cube[2] / ratioHeight, cube[3] / ratioWidth,
                    cube[4] / ratioHeight, cube[5] / ratioWidth,
                    cube[6] / ratioHeight, cube[7] / ratioWidth,
            };
        }

        mGLCubeBuffer.clear();
        mGLCubeBuffer.put(cube).position(0);
        mGLTextureBuffer.clear();
        mGLTextureBuffer.put(textureCords).position(0);
    }

    private float addDistance(float coordinate, float distance) {
        return coordinate == 0.0f ? distance : 1 - distance;
    }

    private int align(int x, int align)
    {
        return ((( x ) + (align) - 1) / (align) * (align));
    }

    public int getOutputWidth() {
        return mOutputWidth;
    }

    public int getOutputHeight() {
        return mOutputHeight;
    }

    public int getInputWidth() {
        return mImageWidth;
    }

    public int getInputHeight() {
        return mImageHeight;
    }

    public void cleanRunOnDraw() {
        if(mRunOnDraw != null)
            cleanAll(mRunOnDraw);
    }

    public Rotation getRotation() {
        return mRotation;
    }

    public boolean isFlippedHorizontally() {
        return mFlipHorizontal;
    }

    public boolean isFlippedVertically() {
        return mFlipVertical;
    }

    private void cleanAll(Queue<Runnable> queue) {
        synchronized (queue) {
            while (!queue.isEmpty()) {
                queue.poll();
            }
        }
    }

    private void runAll(Queue<Runnable> queue) {
        synchronized (queue) {
            while (!queue.isEmpty()) {
                queue.poll().run();
            }
        }
    }

    private void runOnDraw(Queue<Runnable> queue, final Runnable runnable) {
        synchronized (queue) {
            queue.add(runnable);
        }
    }

    private void requestRender() {
        if (mGlSurfaceView != null) {
            mGlSurfaceView.requestRender();
        }
    }

    private enum ScaleType { CENTER_INSIDE, CENTER_CROP }
}
