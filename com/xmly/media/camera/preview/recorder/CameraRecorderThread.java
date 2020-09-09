package com.xmly.media.camera.preview.recorder;

import android.annotation.TargetApi;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import com.xmly.media.camera.preview.listener.onCameraRecorderListener;
import com.xmly.media.camera.view.recorder.IXMCameraRecorderListener;
import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.camera.view.recorder.XMMediaRecorderParams;
import com.xmly.media.gles.EglCore;
import com.xmly.media.gles.OffscreenSurface;
import com.xmly.media.gles.filter.GPUImagePixelCopierFilter;
import com.xmly.media.gles.filter.GPUImageYUY2PixelCopierFilter;
import com.xmly.media.gles.utils.Rotation;
import com.xmly.media.gles.utils.TextureRotationUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;

/**
 * Created by sunyc on 19-7-24.
 */

public class CameraRecorderThread extends Thread {
    private static final String TAG = "CameraRecorderThread";
    //与渲染线程共享EGLContext
    private EGLContext mSharedEGLContext = null;
    //EGL环境
    private EglCore mEglCore;
    //录制视频用的EGLSurface
    private OffscreenSurface mWindowSurface;
    //gles顶点坐标
    private FloatBuffer mGLCubeBuffer;
    //gles纹理坐标
    private FloatBuffer mGLTextureBuffer;
    //类操作锁
    private final Object mSynOperation = new Object();
    //mReady锁
    private final Object mReadyFence = new Object();
    //录制渲染同步锁,与预览渲染同步
    private Object mDrawLock = null;
    //线程运行状态
    private volatile boolean mReady = false;
    //录制状态
    private volatile boolean isRecording = false;
    //编码状态
    private volatile boolean isEncoding = false;
    //录制线程handler
    private CameraRecorderHandler mHandler = null;
    //监听回调
    private onCameraRecorderListener mCameraRecorderListener = null;
    //编码器参数
    private XMMediaRecorderParams mEncoderParams = null;
    //GLES帧拷贝滤镜
    private GPUImagePixelCopierFilter mFilter = null;
    //Native编码器
    private XMMediaRecorder mEncoder = null;

    private void initBuffer() {
        mGLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLCubeBuffer.put(TextureRotationUtil.CUBE).position(0);

        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);
    }

    public CameraRecorderThread(Object drawLock) {
        mDrawLock = drawLock;
        mEncoder = new XMMediaRecorder(true, false, true);
        mEncoder.setListener(onEncoderListener);
        initBuffer();
    }

    /**
     * 设置recorder的绘制锁 与renderer的绘制同步
     * @param drawLock
     */
    public void setDrawLock(Object drawLock) {
        synchronized (mSynOperation) {
            mDrawLock = drawLock;
        }
    }

    /**
     * 配置EGL参数
     * @param sharedContext
     */
    public void setEGLContext(EGLContext sharedContext) {
        synchronized (mSynOperation) {
            mSharedEGLContext = sharedContext;
        }
    }

    /**
     * 配置编码器参数
     * @param params
     */
    public void setEncoderParams(XMMediaRecorderParams params) {
        synchronized (mSynOperation) {
            mEncoderParams = params;
            if (mFilter != null) {
                mFilter.onOutputSizeChanged(mEncoderParams.width, mEncoderParams.height);
            }
        }
    }

    /**
     * 设置录制器监听
     * @param l
     */
    public void setListener(onCameraRecorderListener l) {
        synchronized (mSynOperation) {
            mCameraRecorderListener = l;
        }
    }

    /**
     * 开始录制
     */
    public void startRecord() {
        if (mSharedEGLContext == null) {
            if (mCameraRecorderListener != null) {
                mCameraRecorderListener.onRecorderError();
            }
            Log.e(TAG, "mSharedEGLContext is invalid");
            return;
        }

        synchronized (mSynOperation) {
            if (getStatus()) {
                Log.w(TAG, "the Recorder is running, return");
                return;
            }

            setStatus(true);
            start();
        }
        waitReady();

        synchronized (mSynOperation) {
            if (mHandler != null) {
                mHandler.sendEmptyMessage(CameraRecorderHandler.MSG_START_RECORDING);
            }
        }
    }

    /**
     * 停止录制
     */
    public void stopRecord() {
        synchronized (mSynOperation) {
            isEncoding = false;
            if (!getStatus()) {
                Log.w(TAG, "the Recorder has stopped, return");
                return;
            }

            setStatus(false);
            if (mHandler != null) {
                mHandler.sendEmptyMessage(CameraRecorderHandler.MSG_STOP_RECORDING);
            }
            if (mHandler != null) {
                mHandler.sendEmptyMessage(CameraRecorderHandler.MSG_THREAD_QUIT);
            }

        }
        waitStop();
    }

    /**
     * 帧有效
     * @param texId 纹理Id
     */
    public void frameAvailable(int texId) {
        synchronized (mSynOperation) {
            if (mHandler != null) {
                mHandler.removeMessages(CameraRecorderHandler.MSG_FRAME_AVAILABLE);
                mHandler.sendMessage(mHandler.obtainMessage(CameraRecorderHandler.MSG_FRAME_AVAILABLE, texId, 0));
            }
        }
    }

    /**
     * 等待线程启动
     */
    private void waitReady() {
        synchronized (mReadyFence) {
            while (!mReady) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException ie) {

                }
            }
        }
    }

    /**
     * 等待线程停止
     */
    private void waitStop() {
        synchronized (mReadyFence) {
            while (mReady) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException ie) {

                }
            }
        }
    }

    /**
     * 设置录制状态
     */
    private void setStatus(boolean recording) {
        isRecording = recording;
    }

    /**
     * 得到录制状态
     */
    private boolean getStatus() {
        return isRecording;
    }

    /**
     * 释放GLES资源
     */
    private void releaseGLES() {
        if (mFilter != null) {
            mFilter.destroy();
            mFilter= null;
        }
        if (mWindowSurface != null) {
            mWindowSurface.release();
            mWindowSurface = null;
        }
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }
    }

    /**
     * 释放
     */
    public void release() {
        stopRecord();
        synchronized (mSynOperation) {
            if (mEncoder != null) {
                mEncoder.release();
                mEncoder= null;
            }
            releaseGLES();
            mHandler = null;
            mCameraRecorderListener = null;
            mEncoderParams = null;
            mSharedEGLContext = null;
        }
    }

    @Override
    public void run() {
        Looper.prepare();
        synchronized (mReadyFence) {
            mHandler = new CameraRecorderHandler(this);
            mReady = true;
            mReadyFence.notify();
        }

        Looper.loop();

        synchronized (mReadyFence) {
            mReady = false;
            mHandler = null;
            mReadyFence.notify();
        }
        Log.i(TAG, "Record thread exiting");
    }

    /**
     * 由Handler回调,线程内启动录制
     */
    public void handleStartRecord() {
        //启动Native编码线程
        startEncoder_l(mEncoderParams);
        //释放之前的Egl
        releaseGLES();
        //重新创建一个EglContext 和 Offscreen Surface
        mEglCore = new EglCore(mSharedEGLContext, EglCore.FLAG_RECORDABLE);
        mWindowSurface = new OffscreenSurface(mEglCore, mEncoderParams.width, mEncoderParams.height);

        //启动Egl上下文到此线程
        mWindowSurface.makeCurrent();
        initFilter();
    }

    /**
     * 由Handler回调,线程内停止录制
     */
    public void handleStopRecord() {
        //停止Native编码线程
        stopEncoder_l();
        releaseGLES();
    }

    /**
     * 由Handler回调,线程内读取一帧有效数据
     */
    public void handleFrameAvailable(int texId) {
        if (mDrawLock != null) {
            synchronized (mDrawLock) {
                if (mWindowSurface != null) {
                    mWindowSurface.makeCurrent();
                    //清除opengles显示缓冲区的颜色
                    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

                    if (mFilter != null && isEncoding) {
                        mFilter.onDrawToTexture(texId, mGLCubeBuffer, mGLTextureBuffer);
                    }
                    mWindowSurface.swapBuffers();
                }
            }
        }
    }

    /**
     * 由Handler回调, 停止线程
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void handleStopThread() {
        synchronized (mSynOperation) {
            if (mHandler != null) {
                mHandler.removeCallbacksAndMessages(null);
            }
        }
        releaseGLES();
        Looper.myLooper().quitSafely();
    }

    /**
     * 启动native编码器
     * @param params
     */
    private void startEncoder_l(XMMediaRecorderParams params) {
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
        } else {
            if (mCameraRecorderListener != null) {
                mCameraRecorderListener.onRecorderError();
            }
            Log.e(TAG, "encoder params or encoder is null");
        }
    }

    /**
     * 初始化帧拷贝滤镜
     */
    private void initFilter() {
        if(mFilter != null)
            mFilter.destroy();
        mFilter = new GPUImageYUY2PixelCopierFilter(mEncoder);
        mFilter.init();
        GLES20.glUseProgram(mFilter.getProgram());
        if (mEncoderParams != null) {
            mFilter.onOutputSizeChanged(mEncoderParams.width, mEncoderParams.height);
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
            mEncoder.start();
            if (mCameraRecorderListener != null) {mCameraRecorderListener.onRecorderPrepared();}
        }

        @Override
        public void onRecorderStarted() {
            Log.i(TAG, "onRecorderStarted");
            synchronized (mSynOperation) {
                isEncoding = true;
            }
            if (mCameraRecorderListener != null) {mCameraRecorderListener.onRecorderStarted();}
        }

        @Override
        public void onRecorderStopped() {
            Log.i(TAG, "onRecorderStopped");
            if (mCameraRecorderListener != null) {mCameraRecorderListener.onRecorderStopped();}
        }

        @Override
        public void onRecorderError() {
            Log.e(TAG, "onRecorderError");
            stopRecord();
            if (mCameraRecorderListener != null) {mCameraRecorderListener.onRecorderError();}
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
