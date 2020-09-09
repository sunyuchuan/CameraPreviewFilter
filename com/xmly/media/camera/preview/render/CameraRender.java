package com.xmly.media.camera.preview.render;

import android.annotation.TargetApi;
import android.content.Context;
import android.opengl.EGLContext;
import android.os.Build;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.xmly.media.camera.preview.CameraParam;
import com.xmly.media.camera.preview.listener.onCameraRecorderListener;
import com.xmly.media.camera.preview.listener.onCameraRendererListener;
import com.xmly.media.gles.utils.XMFilterType;

import java.lang.ref.WeakReference;

/**
 * 相机渲染器
 * Created by sunyc on 19-7-24.
 */

public class CameraRender {
    private static final String TAG = "CameraRender";
    private Context mContext = null;
    private WeakReference<SurfaceView> mWeakSurfaceView;
    private CameraRenderThread mRenderThread = null;
    private CameraRenderHandler mRenderHandler = null;
    private final Object mSynOperation = new Object();

    public CameraRender(Context context) {
        mContext = context;
        mRenderThread = new CameraRenderThread(context, "camera render thread");
        mRenderThread.start();
        mRenderHandler = new CameraRenderHandler(mRenderThread);
        mRenderThread.setThreadHandler(mRenderHandler);
    }

    /**
     * 设置SurfaceView 提供渲染画布
     * @param view
     */
    public void setSurfaceView(SurfaceView view) {
        synchronized (mSynOperation) {
            mWeakSurfaceView = new WeakReference<>(view);
            view.getHolder().addCallback(mSurfaceCallback);
        }
    }

    /**
     * 启动相机渲染线程
     */
    public void start() {
        synchronized (mSynOperation) {
            if (mRenderThread != null) {
                mRenderThread.startThread();
            }
        }
    }

    /**
     * 退出渲染线程
     */
    public void stop() {
        synchronized (mSynOperation) {
            if (mRenderThread != null) {
                mRenderThread.stopThread();
                try {
                    mRenderThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 开始录制预览视频
     */
    public void startRecord(String outputPath, onCameraRecorderListener listener) {
        synchronized (mSynOperation) {
            if (mRenderThread != null) {
                mRenderThread.startRecord(outputPath, listener);
            }
        }
    }

    /**
     * 停止录制预览视频
     */
    public void stopRecord() {
        synchronized (mSynOperation) {
            if (mRenderThread != null) {
                mRenderThread.stopRecord();
            }
        }
    }

    /**
     * 释放
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void release() {
        stopRecord();
        stop();

        synchronized (mSynOperation) {
            if (mWeakSurfaceView != null) {
                mWeakSurfaceView.clear();
                mWeakSurfaceView = null;
            }
            if (mRenderHandler != null) {
                mRenderHandler.release();
                mRenderHandler = null;
            }
            if (mRenderThread != null) {
                mRenderThread.release();
                mRenderThread = null;
            }
        }
    }

    /**
     * 设置相机预览状态
     * @param preview
     */
    public void setPreviewStatus(boolean preview) {
        if (mRenderThread != null) {
            mRenderThread.setPreviewStatus(preview);
        }
    }

    /**
     * 设置渲染器状态监听
     * @param l
     */
    public void setRendererListener(onCameraRendererListener l) {
        if (mRenderThread != null) {
            mRenderThread.setListener(l);
        }
    }

    /**
     * 设置渲染滤镜
     */
    public void setFilter(final XMFilterType filtertype) {
        if (mRenderThread != null) {
            mRenderThread.setFilter(filtertype);
        }
    }

    /**
     * 根据相机参数计算渲染参数
     */
    public void setupCamera(CameraParam param) {
        if (mRenderThread != null) {
            mRenderThread.setupCamera(param);
        }
    }

    /**
     * 请求渲染
     */
    public void requestRender() {
        synchronized (mSynOperation) {
            if (mRenderThread != null) {
                mRenderThread.requestRender();
            }
        }
    }

    /**
     * 清除mCameraRunOnDraw
     */
    public void cleanCameraRunOnDraw() {
        if (mRenderThread != null) {
            mRenderThread.cleanCameraRunOnDraw();
        }
    }

    /**
     * 得到共享EGLContext
     * @return
     */
    public EGLContext getEGLContext() {
        if (mRenderThread != null) {
            return mRenderThread.getEGLContext();
        }
        return null;
    }

    /**
     * 得到onDrawFrame synchronized lock
     * @return
     */
    public Object getOnDrawLock() {
        if (mRenderThread != null) {
            return mRenderThread.getOnDrawLock();
        }
        return null;
    }

    private SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            synchronized (mSynOperation) {
                if (mRenderHandler != null) {
                    mRenderHandler.sendMessage(mRenderHandler
                            .obtainMessage(CameraRenderHandler.MSG_SURFACE_CREATED, holder));
                }
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            synchronized (mSynOperation) {
                if (mRenderHandler != null) {
                    mRenderHandler.sendMessage(mRenderHandler
                            .obtainMessage(CameraRenderHandler.MSG_SURFACE_CHANGED, width, height));
                }
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            synchronized (mSynOperation) {
                if (mRenderHandler != null) {
                    mRenderHandler.sendMessage(mRenderHandler
                            .obtainMessage(CameraRenderHandler.MSG_SURFACE_DESTROYED));
                }
            }
        }
    };
}
