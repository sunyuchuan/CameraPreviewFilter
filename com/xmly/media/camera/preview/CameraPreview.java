package com.xmly.media.camera.preview;

import android.content.Context;
import android.util.Log;
import android.view.SurfaceView;

import com.xmly.media.camera.preview.listener.onCameraEngineListener;
import com.xmly.media.camera.preview.listener.onCameraPreviewListener;
import com.xmly.media.camera.preview.listener.onCameraRecorderListener;
import com.xmly.media.camera.preview.listener.onCameraRendererListener;
import com.xmly.media.camera.preview.listener.onCameraSetupCallback;
import com.xmly.media.camera.preview.render.CameraRender;
import com.xmly.media.gles.utils.XMFilterType;

/**
 * 负责相机的预览\录制等相机操作
 * Created by sunyc on 19-7-24.
 */
public class CameraPreview {
    private static final String TAG = "CameraPreview";
    private Context mContext = null;
    private CameraParam mCameraParam = null;
    private CameraEngine mCameraEngine = null;
    private CameraRender mCameraRender = null;
    private onCameraPreviewListener mPreviewListener = null;
    private final Object mSynOperation = new Object();
    private boolean isPreviewing = false;
    //CameraEngine回调
    private onCameraSetupCallback mCameraSetupCallback = new onCameraSetupCallback() {
        @Override
        public void setUpCamera(CameraParam param) {
            if (mCameraRender != null) {
                mCameraRender.setupCamera(param);
            }
        }
    };

    private CameraPreview(Context context, CameraParam param, SurfaceView view) {
        mCameraParam = param;
        mCameraEngine = new CameraEngine(param, mCameraSetupCallback);
        mCameraEngine.setListener(mCameraEngineListener);
        mCameraRender = new CameraRender(context);
        mCameraRender.setSurfaceView(view);
        mCameraRender.setRendererListener(mRendererListener);
    }

    /**
     * 销毁CameraPreview
     */
    public void release() {
        synchronized (mSynOperation) {
            if (mCameraParam != null) {
                mCameraParam.release();
                mCameraParam = null;
            }
            if (mCameraEngine != null) {
                mCameraEngine.release();
                mCameraEngine = null;
            }
            if (mCameraRender != null) {
                mCameraRender.release();
                mCameraRender = null;
            }
            mPreviewListener = null;
        }
    }

    /**
     * 打开相机
     * @return
     */
    public void openCamera() {
        synchronized (mSynOperation) {
            if (isPreviewing) {
                Log.w(TAG, "The camera has started previewing, return");
                return;
            }

            isPreviewing = true;
            if (mCameraRender != null) {
                mCameraRender.cleanCameraRunOnDraw();
            }
            if (mCameraEngine != null) {
                mCameraEngine.openCamera();
            }
        }
    }

    /**
     * 关闭相机
     * @return
     */
    public void closeCamera() {
        synchronized (mSynOperation) {
            isPreviewing = false;
            if (mCameraRender != null) {
                mCameraRender.cleanCameraRunOnDraw();
            }
            if (mCameraEngine != null) {
                mCameraEngine.closeCamera();
            }
        }
    }

    /**
     * 相机切换
     */
    public void switchCamera() {
        synchronized (mSynOperation) {
            if (mCameraRender != null) {
                mCameraRender.cleanCameraRunOnDraw();
            }
            if (mCameraEngine != null) {
                mCameraEngine.switchCamera();
            }
        }
    }

    /**
     * 开始录制预览视频
     * @return
     */
    public void startRecord(String outputPath) {
        synchronized (mSynOperation) {
            if (mCameraRender != null) {
                mCameraRender.startRecord(outputPath, mCameraRecorderListener);
            }
        }
    }

    /**
     * 停止录制预览视频
     * @return
     */
    public void stopRecord() {
        synchronized (mSynOperation) {
            if (mCameraRender != null) {
                mCameraRender.stopRecord();
            }
        }
    }

    /**
     * CameraPreview构造器
     */
    public static class Builder {
        private CameraParam mCameraParam = null;
        private SurfaceView mSurfaceView = null;
        private onCameraPreviewListener mPreviewListener = null;
        private Context mContext = null;

        public Builder() {
            mCameraParam = new CameraParam();
        }

        /**
         * 设置应用的上下文
         * @param context
         * @return
         */
        public Builder setContext(Context context) {
            mContext = context;
            return this;
        }

        public Builder setSurfaceView(SurfaceView view) {
            mSurfaceView = view;
            return this;
        }

        /**
         * 设置相机预览回调
         *
         * @param l
         * @return
         */
        public Builder setPreviewListener(onCameraPreviewListener l) {
            mPreviewListener = l;
            return this;
        }

        /**
         * 设置相机ID
         *
         * @param id 1：前置；0：后置
         * @return CameraBuilder
         */
        public Builder setCameraId(int id) {
            mCameraParam.mExpectedCameraId = id;
            return this;
        }

        /**
         * 设置窗口的旋转方向
         *
         * @param rotation 1：竖屏；0：横屏
         * @return CameraBuilder
         */
        public Builder setWindowRotation(int rotation) {
            mCameraParam.mWindowRotation = rotation;
            return this;
        }

        /**
         * 设置期望的相机分辨率
         *
         * @param w 宽
         * @param h 高
         * @return CameraBuilder
         */
        public Builder setExpectedResolution(int w, int h) {
            mCameraParam.mExpectedWidth = w;
            mCameraParam.mExpectedHeight = h;
            return this;
        }

        /**
         * 设置相机目标输出帧率
         *
         * @param fps 帧率
         * @return CameraBuilder
         */
        public Builder setExpectedFps(int fps) {
            mCameraParam.mExpectedFps = fps;
            return this;
        }

        /**
         * 创建CameraPreview
         * @return
         */
        public CameraPreview build() {
            if (mCameraParam == null) {
                Log.e(TAG, "mCameraParam is null in builder, build failed");
                return null;
            }

            CameraPreview prv = new CameraPreview(mContext, mCameraParam, mSurfaceView);
            prv.setPreviewListener(mPreviewListener);
            mContext = null;
            mPreviewListener = null;
            mSurfaceView = null;
            mCameraParam = null;
            return prv;
        }
    }

    /**
     * 设置渲染滤镜
     */
    public void setFilter(final XMFilterType filtertype) {
        if (mCameraRender != null) {
            mCameraRender.setFilter(filtertype);
            mCameraRender.requestRender();
        }
    }

    /**
     * 设置应用的上下文环境
     * @param context
     * @return
     */
    private void setContext(Context context) {
        mContext = context;
    }

    /**
     * 设置相机预览回调
     * @param l
     * @return
     */
    private void setPreviewListener(onCameraPreviewListener l) {
        mPreviewListener = l;
    }

    /**
     * 得到当前相机ID
     * @return 相机id
     */
    public int getCurrentCameraID() {
        if (mCameraParam == null) {
            return -1;
        }
        return mCameraParam.mPreviewCameraId;
    }

    /**
     * 得到当前相机使用的窗口方向
     * @return 方向值 1：竖屏 0：横屏
     */
    public int getCurrentWindowRotation() {
        if (mCameraParam == null) {
            return -1;
        }
        return mCameraParam.mWindowRotation;
    }

    /**
     * 得到预览宽
     * @return 宽
     */
    public int getPreviewWidth() {
        if (mCameraParam == null) {
            return -1;
        }
        return mCameraParam.mPreviewWidth;
    }

    /**
     * 得到预览高
     * @return
     */
    public int getPreviewHeight() {
        if (mCameraParam == null) {
            return -1;
        }
        return mCameraParam.mPreviewHeight;
    }

    /**
     * 得到录制视频的宽
     * @return
     */
    public int getRecordWidth() {
        if (mCameraParam == null) {
            return -1;
        }
        return mCameraParam.mRecordWidth;
    }

    /**
     * 得到录制视频的高
     * @return
     */
    public int getRecordHeight() {
        if (mCameraParam == null) {
            return -1;
        }
        return mCameraParam.mRecordHeight;
    }

    /**
     * 得到相机实际输出帧率
     * @return 帧率
     */
    public int getPreviewFps() {
        if (mCameraParam == null) {
            return -1;
        }
        return mCameraParam.mPreviewFps;
    }

    //渲染器监听
    private onCameraRendererListener mRendererListener = new onCameraRendererListener() {
        @Override
        public void onSurfaceCreated() {
            Log.i(TAG, "onSurfaceCreated");
        }

        @Override
        public void onSurfaceChanged() {
            Log.i(TAG, "onSurfaceChanged");
            if (mCameraRender != null) {
                mCameraRender.requestRender();
            }
        }

        @Override
        public void onSurfaceDestroyed() {
            Log.i(TAG, "onSurfaceDestroyed");
        }

        @Override
        public void onRendererStarted() {
            Log.i(TAG, "onRendererStarted");
            if (mPreviewListener != null) {
                mPreviewListener.onPreviewStarted();
            }
        }

        @Override
        public void onFrameAvailable(int texId) {
        }
    };

    //CameraEngine回调监听
    private onCameraEngineListener mCameraEngineListener = new onCameraEngineListener() {
        @Override
        public void onCameraOpened() {
            Log.i(TAG, "onCameraOpened");
            if (mCameraRender != null) {
                mCameraRender.setPreviewStatus(true);
                mCameraRender.requestRender();
            }
        }

        @Override
        public void onCameraClosed() {
            Log.i(TAG, "onCameraClosed");
            if (mCameraRender != null) {
                mCameraRender.setPreviewStatus(false);
            }
            if (mPreviewListener != null) {
                mPreviewListener.onPreviewStopped();
            }
        }

        @Override
        public void onCameraError() {
            Log.i(TAG, "onCameraError");
            if (mCameraRender != null) {
                mCameraRender.setPreviewStatus(false);
            }
            if (mPreviewListener != null) {
                mPreviewListener.onPreviewError();
            }
            synchronized (mSynOperation) {
                isPreviewing = false;
            }
        }
    };

    private onCameraRecorderListener mCameraRecorderListener = new onCameraRecorderListener() {
        @Override
        public void onRecorderPrepared() {
            Log.i(TAG, "onRecorderPrepared");
        }

        @Override
        public void onRecorderStarted() {
            Log.i(TAG, "onRecorderStarted");
        }

        @Override
        public void onRecorderStopped() {
            Log.i(TAG, "onRecorderStopped");
        }

        @Override
        public void onRecorderError() {
            Log.e(TAG, "onRecorderError");
        }
    };
}
