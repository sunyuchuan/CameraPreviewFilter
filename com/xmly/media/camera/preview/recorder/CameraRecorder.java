package com.xmly.media.camera.preview.recorder;

import android.opengl.EGLContext;

import com.xmly.media.camera.preview.listener.onCameraRecorderListener;
import com.xmly.media.camera.view.recorder.XMMediaRecorderParams;

/**
 * 相机预览录制编码器
 * Created by sunyc on 19-7-24.
 */

public class CameraRecorder {
    private static final String TAG = "CameraRecorder";
    //录制线程
    private CameraRecorderThread mRecordThread = null;

    private CameraRecorder(Object drawLock) {
        mRecordThread = new CameraRecorderThread(drawLock);
    }

    /**
     * 设置recorder的绘制锁 与renderer的绘制同步
     * @param lock
     * @return
     */
    public void setDrawLock(Object lock) {
        if (mRecordThread != null) {
            mRecordThread.setDrawLock(lock);
        }
    }

    /**
     * 配置EGL共享上下文
     * @param sharedContext
     */
    public void setEGLContext(EGLContext sharedContext) {
        if (mRecordThread != null) {
            mRecordThread.setEGLContext(sharedContext);
        }
    }

    /**
     * 配置编码器参数
     * @param params
     */
    public void setEncoderParams(XMMediaRecorderParams params) {
        if (mRecordThread != null) {
            mRecordThread.setEncoderParams(params);
        }
    }

    /**
     * 开始录制
     */
    public void startRecord() {
        if (mRecordThread != null) {
            mRecordThread.startRecord();
        }
    }

    /**
     * 停止录制
     */
    public void stopRecord() {
        if (mRecordThread != null) {
            mRecordThread.stopRecord();
        }
    }

    /**
     * 帧有效
     */
    public void frameAvailable(int texId) {
        if (mRecordThread != null) {
            mRecordThread.frameAvailable(texId);
        }
    }

    /**
     * 设置录制器监听
     * @param l
     */
    public void setRecorderListener(onCameraRecorderListener l) {
        if (mRecordThread != null) {
            mRecordThread.setListener(l);
        }
    }

    /**
     * 释放
     */
    public void release() {
        if (mRecordThread != null) {
            mRecordThread.release();
            mRecordThread = null;
        }
    }

    /**
     * CameraRecorder构造器
     */
    public static class Builder {
        private static final String TAG = "RecorderBuilder";
        //录制器参数
        private XMMediaRecorderParams mEncoderParams = null;
        //与渲染线程共享EGLContext
        private EGLContext mSharedEGLContext = null;
        //渲染操作锁
        private Object mDrawLock = null;

        public Builder() {
            mEncoderParams = new XMMediaRecorderParams();
        }

        //设置录制视频的宽和高
        public Builder setRecordSize(int w, int h) {
            mEncoderParams.width = w;
            mEncoderParams.height = h;
            return this;
        }

        //设置录制视频的码率
        public Builder setRecordBitrate(int bitrate) {
            mEncoderParams.bitrate = bitrate;
            return this;
        }

        //设置录制视频的帧率
        public Builder setRecordFps(int fps) {
            mEncoderParams.fps = fps;
            return this;
        }

        //是否固定帧率录制，值XMMediaRecorderParams.FALSE or TRUE
        public Builder isConstantFps(int constFps) {
            mEncoderParams.CFR = constFps;
            return this;
        }

        //设置录制视频的输出路径
        public Builder setOutputPath(String path) {
            mEncoderParams.output_path = path;
            return this;
        }

        //设置录制视频的GOP大小，单位是秒
        public Builder setGopSize(float gopsize) {
            mEncoderParams.gop_size = gopsize;
            return this;
        }

        //设置录制视频的最大B帧个数
        public Builder setBFramesNum(int number) {
            mEncoderParams.max_b_frames = number;
            return this;
        }

        //设置录制视频的编码器编码速度
        public Builder setEncoderPreset(String preset) {
            mEncoderParams.preset = preset;
            return this;
        }

        //设置EGL共享上下文
        public Builder setSharedEGLContext(EGLContext sharedEGLContext) {
            mSharedEGLContext = sharedEGLContext;
            return this;
        }

        //设置录制的绘制锁 与预览绘制同步
        public Builder setDrawLock(Object lock) {
            mDrawLock = lock;
            return this;
        }

        //创建CameraRecorder
        public CameraRecorder build() {
            CameraRecorder recorder =  new CameraRecorder(mDrawLock);
            recorder.setEGLContext(mSharedEGLContext);
            recorder.setEncoderParams(mEncoderParams);
            mDrawLock = null;
            mSharedEGLContext = null;
            mEncoderParams = null;
            return recorder;
        }
    }
}
