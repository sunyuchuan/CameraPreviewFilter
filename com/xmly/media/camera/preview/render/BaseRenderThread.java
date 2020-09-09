package com.xmly.media.camera.preview.render;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.os.Build;
import android.os.HandlerThread;
import android.util.SparseArray;
import android.view.SurfaceHolder;

import com.xmly.media.gles.EglCore;
import com.xmly.media.gles.WindowSurface;
import com.xmly.media.gles.filter.GPUImageFilter;
import com.xmly.media.gles.utils.OpenGlUtils;
import com.xmly.media.gles.utils.Rotation;
import com.xmly.media.gles.utils.TextureRotationUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.LinkedList;
import java.util.Queue;

/**
 * 渲染线程的基类
 * Created by sunyc on 19-7-26.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class BaseRenderThread extends HandlerThread {
    private static final String TAG = "BaseRenderThread";
    //操作锁
    protected final Object mSynOperation = new Object();
    //GLES操作锁
    protected final Object mDrawLock = new Object();
    //EGL封装类,创建EGL上下文环境
    protected EglCore mEglCore = null;
    //EGLSurface,入参是SurfaceView
    protected WindowSurface mWindowSurface = null;
    //相机SurfaceTexture
    protected SurfaceTexture mSurfaceTexture = null;
    //相机输出纹理
    protected int mTextureId = OpenGlUtils.NO_TEXTURE;
    //openGL滤镜组
    protected SparseArray<GPUImageFilter> mFilterArrays = new SparseArray<GPUImageFilter>();
    //顶点坐标buffer
    protected FloatBuffer mGLCubeBuffer;
    protected FloatBuffer mDefaultGLCubeBuffer;
    //纹理坐标buffer
    protected FloatBuffer mGLTextureBuffer;
    protected FloatBuffer mDefaultGLTextureBuffer;

    //需要在egl上下文环境中运行的事件队列
    protected final Queue<Runnable> mRunOnDraw;
    //SurfaceView显示区域宽高
    protected int mOutputWidth;
    protected int mOutputHeight;

    //顶点坐标常量
    protected static final float CUBE[] = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f,
    };

    /**
     * 初始化顶点和纹理坐标数组
     */
    private void initBuffer() {
        mGLCubeBuffer = ByteBuffer.allocateDirect(CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLCubeBuffer.put(CUBE).position(0);

        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);

        mDefaultGLCubeBuffer = ByteBuffer.allocateDirect(CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mDefaultGLCubeBuffer.put(CUBE).position(0);

        mDefaultGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.ROTATION_180, true, false)).position(0);
    }

    /**
     * 检测是否支持opengles 2.0
     * @param context
     * @return
     */
    private boolean supportsOpenGLES2(final Context context) {
        final ActivityManager activityManager = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        final ConfigurationInfo configurationInfo =
                activityManager.getDeviceConfigurationInfo();
        return configurationInfo.reqGlEsVersion >= 0x20000;
    }

    public BaseRenderThread(final Context context, String name) {
        super(name);
        if (!supportsOpenGLES2(context)) {
            throw new IllegalStateException("OpenGL ES 2.0 is not supported on this phone.");
        }

        mRunOnDraw = new LinkedList<Runnable>();
        initBuffer();
    }

    /**
     * SurfaceView创建完成,执行EGL创建及初始化
     * @param holder
     */
    public void onSurfaceCreated(SurfaceHolder holder) {
        synchronized (mDrawLock) {
            //释放之前的Egl
            if (mWindowSurface != null) {
                mWindowSurface.release();
                mWindowSurface = null;
            }
            if (mEglCore != null) {
                mEglCore.release();
                mEglCore = null;
            }
            //创建EGL环境
            mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
            mWindowSurface = new WindowSurface(mEglCore, holder.getSurface(), false);
            //切换到渲染上下文
            mWindowSurface.makeCurrent();
        }

        //gles配置
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    /**
     * SurfaceView 大小发生变化
     * @param width
     * @param height
     */
    public void onSurfaceChanged(int width, int height) {
        mOutputWidth = align(width, 2);
        mOutputHeight = align(height, 2);
    }

    /**
     * 绘制帧
     */
    public void onDrawFrame() {
        if (mWindowSurface != null) {
            mWindowSurface.makeCurrent();
        }
        //清零缓冲区颜色
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
    }

    /**
     * SurfaceView被销毁
     */
    public void onSurfaceDestroyed() {
        synchronized (mDrawLock) {
            if (mWindowSurface != null) {
                mWindowSurface.release();
                mWindowSurface = null;
            }
            if (mEglCore != null) {
                mEglCore.release();
                mEglCore = null;
            }
        }
    }

    /**
     * 得到共享EGL上下文
     * @return
     */
    public EGLContext getEGLContext() {
        if (mEglCore != null) {
            return mEglCore.getEGLContext();
        }
        return null;
    }

    /**
     * 得到onDrawFrame synchronized lock
     * @return
     */
    public Object getOnDrawLock() {
        return mDrawLock;
    }

    /**
     * 主动释放渲染线程资源
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void release() {
        if (mRunOnDraw != null) {
            cleanAll(mRunOnDraw);
        }
        releaseFilters();

        synchronized (mDrawLock) {
            if (mTextureId != OpenGlUtils.NO_TEXTURE) {
                GLES20.glDeleteTextures(1, new int[]{mTextureId}, 0);
                mTextureId = OpenGlUtils.NO_TEXTURE;
            }
            if (mSurfaceTexture != null) {
                mSurfaceTexture.release();
                mSurfaceTexture = null;
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
    }

    /**
     * 请求绘制帧
     */
    protected void requestRender() { }

    /**
     * 滤镜初始化
     */
    protected void initFilters() {
        synchronized (mFilterArrays) {
            for (int i = 0; i < mFilterArrays.size(); i++) {
                if (mFilterArrays.get(i) != null) {
                    mFilterArrays.get(i).init();
                }
            }
        }
    }

    /**
     * 滤镜数组初始化
     */
    protected void initFilterArrays() {
        synchronized (mFilterArrays) {
            for (int i = 0; i <= RenderIndex.DownloadIndex; i++) {
                mFilterArrays.put(i, null);
            }
        }
    }

    /**
     * 释放所有滤镜和滤镜数组本身
     */
    protected void releaseFilters() {
        synchronized (mFilterArrays) {
            for (int i = 0; i < mFilterArrays.size(); i++) {
                if (mFilterArrays.get(i) != null) {
                    mFilterArrays.get(i).destroy();
                }
            }
            mFilterArrays.clear();
        }
    }

    /**
     * align对齐
     * @param x
     * @param align
     * @return
     */
    protected int align(int x, int align) {
        return ((( x ) + (align) - 1) / (align) * (align));
    }

    /**
     * 计算旋转 翻转 缩放后的纹理坐标
     * @param input_w
     * @param input_h
     * @param output_w
     * @param output_h
     * @param rotation
     * @param flipHorizontal
     * @param flipVertical
     * @param textureBuffer
     */
    protected void adjustImageScaling(int input_w, int input_h, int output_w, int output_h,
                                      Rotation rotation, boolean flipHorizontal, boolean flipVertical,
                                      FloatBuffer textureBuffer) {
        if (input_w == 0 || input_h == 0 || output_w == 0 || output_h == 0) {
            return;
        }

        float outputWidth = output_w;
        float outputHeight = output_h;
        if (rotation == Rotation.ROTATION_270 || rotation == Rotation.ROTATION_90) {
            outputWidth = output_h;
            outputHeight = output_w;
        }

        float ratio1 = outputWidth / input_w;
        float ratio2 = outputHeight / input_h;
        float ratioMax = Math.max(ratio1, ratio2);
        int imageWidthNew = Math.round(input_w * ratioMax);
        int imageHeightNew = Math.round(input_h * ratioMax);

        float ratioWidth = imageWidthNew / outputWidth;
        float ratioHeight = imageHeightNew / outputHeight;

        float[] textureCords = TextureRotationUtil.getRotation(rotation, flipHorizontal, flipVertical);
        float distHorizontal = (1 - 1 / ratioWidth) / 2;
        float distVertical = (1 - 1 / ratioHeight) / 2;
        textureCords = new float[]{
                addDistance(textureCords[0], distHorizontal), addDistance(textureCords[1], distVertical),
                addDistance(textureCords[2], distHorizontal), addDistance(textureCords[3], distVertical),
                addDistance(textureCords[4], distHorizontal), addDistance(textureCords[5], distVertical),
                addDistance(textureCords[6], distHorizontal), addDistance(textureCords[7], distVertical),
        };

        textureBuffer.clear();
        textureBuffer.put(textureCords).position(0);
    }

    private float addDistance(float coordinate, float distance) {
        return coordinate == 0.0f ? distance : 1 - distance;
    }

    /**
     * 清除Runnable队列
     * @param queue
     */
    protected void cleanAll(Queue<Runnable> queue) {
        synchronized (queue) {
            while (!queue.isEmpty()) {
                queue.poll();
            }
        }
    }

    /**
     * 运行Runnable队列中的事件
     * @param queue
     */
    protected void runAll(Queue<Runnable> queue) {
        synchronized (queue) {
            while (!queue.isEmpty()) {
                queue.poll().run();
            }
        }
    }

    /**
     * 添加事件到Runnable队列
     * @param queue
     * @param runnable
     */
    protected void runOnDraw(Queue<Runnable> queue, final Runnable runnable) {
        synchronized (queue) {
            queue.add(runnable);
        }
    }

    /**
     * 滤镜类型的Index
     */
    public final class RenderIndex {
        public static final int CameraIndex = 0;        // 相机输入索引
        public static final int RotateIndex = 1;        // 图片旋转索引
        public static final int DecoderIndex = 2;       // 视频解码器输入索引
        public static final int PipIndex = 3;           // 画中画索引
        public static final int FilterIndex = 4;        // 滤镜索引
        public static final int DecoderPipIndex = 5;    // 视频解码器画中画输入索引
        public static final int MixIndex = 6;           // 混合索引
        public static final int DisplayIndex = 7;       // 显示索引
        public static final int DownloadIndex = 8;      // 像素下载到编码器索引
    }
}
