package com.xmly.media.camera.preview.render;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceHolder;

import com.xmly.media.camera.preview.CameraParam;
import com.xmly.media.camera.preview.listener.onCameraRecorderListener;
import com.xmly.media.camera.preview.listener.onCameraRendererListener;
import com.xmly.media.camera.view.recorder.IXMCameraRecorderListener;
import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.camera.view.recorder.XMMediaRecorderParams;
import com.xmly.media.gles.filter.GPUImageCameraInputFilter;
import com.xmly.media.gles.filter.GPUImageFilter;
import com.xmly.media.gles.filter.GPUImageFilterFactory;
import com.xmly.media.gles.filter.GPUImageYUY2PixelCopierFilter;
import com.xmly.media.gles.utils.OpenGlUtils;
import com.xmly.media.gles.utils.Rotation;
import com.xmly.media.gles.utils.TextureRotationUtil;
import com.xmly.media.gles.utils.XMFilterType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

/**
 * 渲染线程
 * Created by sunyc on 19-7-24.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class CameraRenderThread extends BaseRenderThread {
    private static final String TAG = "CameraRenderThread";
    //线程Handler
    private CameraRenderHandler mRenderHandler = null;
    //相机参数
    private CameraParam mCameraParam = null;
    //帧的刷新锁
    private final Object mSyncFrameNum = new Object();
    private final Object mSyncFence = new Object();
    //预览状态
    private boolean isPreviewing = false;
    //可用帧个数
    private int updateTexImageCounter = 0;
    //滤镜类型
    private XMFilterType mFilterType = XMFilterType.NONE;
    //相机纹理坐标
    private FloatBuffer mGLCameraTextureBuffer;
    //相机旋转角度和翻转
    private Rotation mRotation = Rotation.NORMAL;
    private boolean mFlipHorizontal = false;
    private boolean mFlipVertical = false;
    //渲染器状态监听
    private onCameraRendererListener mListener = null;
    //相机事件队列
    private final Queue<Runnable> mCameraRunOnDraw;
    //第一次渲染
    private boolean mFirstFrame = true;
    //编码器
    private XMMediaRecorder mEncoder = null;
    //编码器参数
    private XMMediaRecorderParams mEncoderParams = null;
    //编码器监听回调
    private onCameraRecorderListener mEncoderListener = null;
    //录制状态
    private boolean isRecording = false;
    //编码状态
    private boolean isEncoding = false;
    //录制视频输出路径
    private String mOutputPath = null;

    /**
     * 初始化相机纹理坐标
     */
    private void initBuffer() {
        mGLCameraTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLCameraTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);
    }

    private void createFilters() {
        releaseFilters();
        initFilterArrays();
        mFilterArrays.put(RenderIndex.CameraIndex, new GPUImageCameraInputFilter());
        mFilterArrays.put(RenderIndex.RotateIndex, GPUImageFilterFactory.CreateFilter(XMFilterType.NONE));
        mFilterArrays.put(RenderIndex.FilterIndex, GPUImageFilterFactory.CreateFilter(mFilterType));
        mFilterArrays.put(RenderIndex.DisplayIndex, GPUImageFilterFactory.CreateFilter(XMFilterType.NONE));
        mFilterArrays.put(RenderIndex.DownloadIndex, new GPUImageYUY2PixelCopierFilter(mEncoder));
    }

    public CameraRenderThread(Context context, String name) {
        super(context, name);
        initBuffer();
        mCameraRunOnDraw = new LinkedList<Runnable>();
        mEncoder = new XMMediaRecorder(true, false, true);
        mEncoder.setListener(onEncoderListener);
    }

    /**
     * 启动渲染线程
     */
    public void startThread() {
        synchronized (mSynOperation) {
            Thread.State state = getState();
            if (Thread.State.NEW == state
                    || Thread.State.TERMINATED == state) {
                start();
            }
        }
    }

    /**
     * 停止渲染线程
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void stopThread() {
        synchronized (mSynOperation) {
            if (mRenderHandler != null) {
                mRenderHandler.removeCallbacksAndMessages(null);
            }

            quitSafely();
        }
    }

    /**
     * 开始录制
     * @param outputPath
     */
    public void startRecord(String outputPath, onCameraRecorderListener listener) {
        synchronized (mSynOperation) {
            if (isRecording) {
                Log.w(TAG, "the Recorder is running, pls stop");
                return;
            }

            mEncoderListener = listener;
            isRecording = true;
            mOutputPath = outputPath;
            if (mEncoderParams != null) {
                mEncoderParams.setOutputPath(mOutputPath);
                startEncoder_l(mEncoderParams);
            }
        }
    }

    /**
     * 停止录制
     */
    public void stopRecord() {
        synchronized (mSynOperation) {
            if (!isRecording) {
                Log.w(TAG, "the Recorder has stopped, return");
                return;
            }
            stopEncoder_l();
            mOutputPath = null;
        }
    }

    /**
     * 设置渲染器状态监听
     * @param l
     */
    public void setListener(onCameraRendererListener l) {
        synchronized (mSynOperation) {
            mListener = l;
        }
    }

    /**
     * 设置相机预览状态
     * @param preview
     */
    public void setPreviewStatus(boolean preview) {
        synchronized (mSynOperation) {
            isPreviewing = preview;
        }
    }

    /**
     * 设置Handler回调
     * @param handler
     */
    public void setThreadHandler(CameraRenderHandler handler) {
        synchronized (mSynOperation) {
            mRenderHandler = handler;
        }
    }

    /**
     * Surface创建
     * @param holder
     */
    @Override
    public void onSurfaceCreated(SurfaceHolder holder) {
        super.onSurfaceCreated(holder);

        createFilters();
        initFilters();
        if (mListener != null) {
            mListener.onSurfaceCreated();
        }
    }

    /**
     * Surface改变
     * @param width
     * @param height
     */
    @Override
    public void onSurfaceChanged(int width, int height) {
        super.onSurfaceChanged(width, height);

        if (mCameraParam != null) {
            filtersSizeChanged(mCameraParam);

            adjustImageScaling(mCameraParam.mRecordWidth, mCameraParam.mRecordHeight, mOutputWidth, mOutputHeight,
                    Rotation.NORMAL, false, false, mGLTextureBuffer);
        }

        if (mListener != null) {
            mListener.onSurfaceChanged();
        }
    }

    /**
     * Surface销毁
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void onSurfaceDestroyed() {
        super.onSurfaceDestroyed();

        releaseFilters();
        synchronized (mDrawLock) {
            if (mTextureId != OpenGlUtils.NO_TEXTURE) {
                GLES20.glDeleteTextures(1, new int[]{mTextureId}, 0);
                mTextureId = OpenGlUtils.NO_TEXTURE;
            }
            if (mSurfaceTexture != null) {
                mSurfaceTexture.release();
            }
            mFirstFrame = true;
        }

        if (mListener != null) {
            mListener.onSurfaceDestroyed();
        }
    }

    /**
     * 释放
     */
    public void release() {
        super.release();
        if (mCameraRunOnDraw != null) {
            cleanAll(mCameraRunOnDraw);
        }

        mRenderHandler = null;
        mCameraParam = null;
        mListener = null;
    }

    /**
     * 绘制视频帧
     */
    @Override
    public void onDrawFrame() {
        if (!isPreviewing || mWindowSurface == null) {
            return;
        }

        int cameraTex = OpenGlUtils.NO_TEXTURE;
        synchronized (mDrawLock) {
            super.onDrawFrame();
            runAll(mCameraRunOnDraw);
            runAll(mRunOnDraw);
            //把SurfaceTexture中的帧全部刷新出
            synchronized (mSyncFrameNum) {
                if (mSurfaceTexture != null) {
                    while (updateTexImageCounter != 0) {
                        mSurfaceTexture.updateTexImage();
                        --updateTexImageCounter;
                    }
                } else {
                    return;
                }
            }

            float[] mtx = new float[16];
            mSurfaceTexture.getTransformMatrix(mtx);

            if (mFilterArrays.get(RenderIndex.CameraIndex) != null) {
                ((GPUImageCameraInputFilter) mFilterArrays.get(RenderIndex.CameraIndex)).setTextureTransformMatrix(mtx);
                cameraTex = mFilterArrays.get(RenderIndex.CameraIndex).onDrawToTexture(mTextureId, mDefaultGLCubeBuffer, mGLCameraTextureBuffer);
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
                //交换显示缓冲区，把绘制的帧显示到屏幕
                mWindowSurface.swapBuffers();
            }

            if (mFilterArrays.get(RenderIndex.DownloadIndex) != null && isEncoding) {
                mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);
                mFilterArrays.get(RenderIndex.DownloadIndex).onDrawToTexture(cameraTex, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
            }
        }

        if (mListener != null) {
            if (mFirstFrame) {
                mListener.onRendererStarted();
                mFirstFrame = false;
            }
            mListener.onFrameAvailable(cameraTex);
        }
    }

    public void onStartRecording() {}
    public void onStopRecording() {}
    public void onPreviewCallback(byte[] data) {}

    /**
     * 设置渲染滤镜
     * @param filtertype
     */
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
                if (mCameraParam != null) {
                    filter.onOutputSizeChanged(mCameraParam.mRecordWidth, mCameraParam.mRecordHeight);
                    filter.onInputSizeChanged(mCameraParam.mRecordWidth, mCameraParam.mRecordHeight);
                }
                mFilterArrays.put(RenderIndex.FilterIndex, filter);
            }
        });
        mFilterType = filtertype;
    }

    /**
     * 请求刷新
     */
    public void requestRender() {
        synchronized (mSynOperation) {
            if (mRenderHandler != null) {
                mRenderHandler.removeMessages(CameraRenderHandler.MSG_REQUEST_RENDER);
                mRenderHandler.sendMessage(mRenderHandler
                        .obtainMessage(CameraRenderHandler.MSG_REQUEST_RENDER));
            }
        }
    }

    /**
     * 清除mCameraRunOnDraw
     */
    public void cleanCameraRunOnDraw() {
            cleanAll(mCameraRunOnDraw);
    }

    /**
     * 设置相机旋转角度 水平垂直翻转 纹理坐标.
     * @param param
     * @param rotation
     */
    private void setRotation(CameraParam param, final Rotation rotation) {
        mRotation = rotation;
        if(rotation == Rotation.ROTATION_90 || rotation == Rotation.ROTATION_270) {
            mFlipHorizontal = param.mFlipVertical;
            mFlipVertical = param.mFlipHorizontal;
        } else {
            mFlipHorizontal = param.mFlipHorizontal;
            mFlipVertical = param.mFlipVertical;
        }

        adjustImageScaling(param.mPreviewWidth, param.mPreviewHeight, param.mRecordWidth, param.mRecordHeight,
                mRotation, mFlipHorizontal, mFlipVertical, mGLCameraTextureBuffer);

        adjustImageScaling(param.mRecordWidth, param.mRecordHeight, mOutputWidth, mOutputHeight,
                Rotation.NORMAL, false, false, mGLTextureBuffer);
    }

    /**
     * 设置滤镜输出大小
     * @param param
     */
    private void filtersSizeChanged(CameraParam param) {
        if (param == null) {
            return;
        }

        if (mFilterArrays.get(RenderIndex.CameraIndex) != null) {
            mFilterArrays.get(RenderIndex.CameraIndex).onOutputSizeChanged(param.mRecordWidth, param.mRecordHeight);
        }
        if (mFilterArrays.get(RenderIndex.RotateIndex) != null) {
            mFilterArrays.get(RenderIndex.RotateIndex).onOutputSizeChanged(param.mRecordWidth, param.mRecordHeight);
        }
        if (mFilterArrays.get(RenderIndex.FilterIndex) != null) {
            mFilterArrays.get(RenderIndex.FilterIndex).onInputSizeChanged(param.mRecordWidth, param.mRecordHeight);
            mFilterArrays.get(RenderIndex.FilterIndex).onOutputSizeChanged(param.mRecordWidth, param.mRecordHeight);
        }
        if (mFilterArrays.get(RenderIndex.DisplayIndex) != null) {
            mFilterArrays.get(RenderIndex.DisplayIndex).onOutputSizeChanged(mOutputWidth, mOutputHeight);
        }
        if (mFilterArrays.get(RenderIndex.DownloadIndex) != null) {
            mFilterArrays.get(RenderIndex.DownloadIndex).onOutputSizeChanged(param.mRecordWidth, param.mRecordHeight);
        }
    }

    /**
     * 启动相机 安装相机参数，比如相机旋转角度 翻转
     * 录制分辨率 相机纹理坐标 滤镜输出帧大小
     */
    public void setupCamera(final CameraParam param) {
        runOnDraw(mCameraRunOnDraw, new Runnable() {
            @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            @Override
            public void run() {
                mCameraParam = param;
                if (param == null) {
                    Log.w(TAG, "setupCameraParma param is null, return");
                    return;
                }

                if (param.mOrientation == 90 || param.mOrientation == 270) {
                    param.mRecordWidth = param.mPreviewHeight;
                    param.mRecordHeight = param.mPreviewWidth;
                } else {
                    param.mRecordWidth = param.mPreviewWidth;
                    param.mRecordHeight = param.mPreviewHeight;
                }
                filtersSizeChanged(param);
                mEncoderParams = new XMMediaRecorderParams()
                        .setSize(param.mRecordWidth, param.mRecordHeight)
                        .setFps(param.mPreviewFps)
                        .setOutputPath(mOutputPath)
                        .setCFR(XMMediaRecorderParams.FALSE)
                        .setBitrate(700000 * (param.mRecordWidth*param.mRecordHeight)/(960*540))
                        .setGopsize(0.5f)
                        .setMaxBFrames(0);
                if (isRecording) {
                    startEncoder_l(mEncoderParams);
                }

                Rotation rotation = Rotation.NORMAL;
                switch (param.mOrientation) {
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
                setRotation(param, rotation);

                if (mTextureId != OpenGlUtils.NO_TEXTURE) {
                    GLES20.glDeleteTextures(1, new int[]{mTextureId}, 0);
                    mTextureId = OpenGlUtils.NO_TEXTURE;
                }
                if (mSurfaceTexture != null) {
                    mSurfaceTexture.release();
                }

                mTextureId = OpenGlUtils.getTexturesID();
                mSurfaceTexture = new SurfaceTexture(mTextureId);
                mSurfaceTexture.setOnFrameAvailableListener(mOnFrameAvailableListener);
                try {
                    param.mCameraInstance.setPreviewTexture(mSurfaceTexture);
                    param.mCameraInstance.startPreview();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 帧有效回调
     */
    private SurfaceTexture.OnFrameAvailableListener mOnFrameAvailableListener = new SurfaceTexture.OnFrameAvailableListener() {
        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            synchronized (mSyncFrameNum) {
                ++updateTexImageCounter;
            }
            requestRender();
        }
    };

    /**
     * 启动native编码器
     * @param params
     */
    private void startEncoder_l(XMMediaRecorderParams params) {
        if (isEncoding) {
            Log.w(TAG, "the encoder is running, pls stop");
            return;
        }

        if (mEncoder != null && params != null) {
            HashMap<String, String> config = new HashMap<String, String>();
            config.put("width", String.valueOf(params.width));
            config.put("height", String.valueOf(params.height));
            config.put("bit_rate", String.valueOf(params.bitrate));
            config.put("fps", String.valueOf(params.fps));
            config.put("gop_size", String.valueOf((int) (params.gop_size * params.fps)));
            config.put("crf", String.valueOf(params.crf));
            config.put("multiple", String.valueOf(params.multiple));
            config.put("max_b_frames", String.valueOf(params.max_b_frames));
            config.put("CFR", String.valueOf(params.CFR));
            config.put("output_filename", params.output_path);
            config.put("preset", params.preset);
            config.put("tune", params.tune);
            if (!mEncoder.setConfigParams(config)) {
                Log.e(TAG, "setConfigParams failed, exit");
                config.clear();
                return;
            }

            config.clear();
            mEncoder.prepareAsync();
            isEncoding = true;
        } else {
            if (mEncoderListener != null) {
                mEncoderListener.onRecorderError();
            }
            Log.e(TAG, "encoder params or encoder is null");
        }
    }

    /**
     * 停止Native编码器
     */
    private void stopEncoder_l() {
        if (mEncoder != null) {
            mEncoder.stop();
        }
    }

    private IXMCameraRecorderListener onEncoderListener = new IXMCameraRecorderListener() {
        @Override
        public void onImageReaderPrepared() {
            Log.i(TAG, "onImageReaderPrepared");
        }

        @Override
        public void onRecorderPrepared() {
            Log.i(TAG, "onRecorderPrepared");
            synchronized (mSynOperation) {
                mEncoder.start();
            }
            if (mEncoderListener != null) {mEncoderListener.onRecorderPrepared();}
        }

        @Override
        public void onRecorderStarted() {
            Log.i(TAG, "onRecorderStarted");
            if (mEncoderListener != null) {mEncoderListener.onRecorderStarted();}
        }

        @Override
        public void onRecorderStopped() {
            Log.i(TAG, "onRecorderStopped");
            synchronized (mSynOperation) {
                isEncoding = false;
                isRecording = false;
            }
            if (mEncoderListener != null) {mEncoderListener.onRecorderStopped();}
        }

        @Override
        public void onRecorderError() {
            Log.e(TAG, "onRecorderError");
            synchronized (mSynOperation) {
                isEncoding = false;
                isRecording = false;
            }
            stopRecord();
            if (mEncoderListener != null) {mEncoderListener.onRecorderError();}
        }

        @Override
        public void onPreviewStarted() {

        }

        @Override
        public void onPreviewStopped() {

        }

        @Override
        public void onPreviewError() {

        }
    };
}
