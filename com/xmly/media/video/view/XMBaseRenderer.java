package com.xmly.media.video.view;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.Log;
import android.util.SparseArray;

import com.xmly.media.gles.filter.GPUImageFilter;
import com.xmly.media.camera.view.recorder.IXMCameraRecorderListener;
import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.gles.utils.GPUImageParams;
import com.xmly.media.gles.utils.OpenGlUtils;
import com.xmly.media.gles.utils.Rotation;
import com.xmly.media.gles.utils.TextureRotationUtil;
import com.xmly.media.gles.utils.XMFilterType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.LinkedList;
import java.util.Queue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by sunyc on 19-7-9.
 */

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class XMBaseRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "XMRenderer";
    protected SparseArray<GPUImageFilter> mFilterArrays = new SparseArray<GPUImageFilter>();
    protected FloatBuffer mGLCubeBuffer;
    protected FloatBuffer mGLTextureBuffer;
    protected FloatBuffer mDefaultGLCubeBuffer;
    protected FloatBuffer mDefaultGLTextureBuffer;
    protected XMMediaRecorder mRecorder = null;
    protected GLSurfaceView mGLSurfaceView = null;
    protected SurfaceTexture mSurfaceTexture = null;
    protected int mGLTextureId = OpenGlUtils.NO_TEXTURE;
    protected final Queue<Runnable> mRunOnDraw;
    protected boolean updateTexImage = false;
    protected int mOutputWidth;
    protected int mOutputHeight;
    protected IXMCameraRecorderListener mListener;
    protected volatile boolean mGPUCopierEnable = false;

    protected static final float CUBE[] = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f,
    };

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

    private boolean supportsOpenGLES2(final Context context) {
        final ActivityManager activityManager = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        final ConfigurationInfo configurationInfo =
                activityManager.getDeviceConfigurationInfo();
        return configurationInfo.reqGlEsVersion >= 0x20000;
    }

    public XMBaseRenderer(final Context context, XMMediaRecorder recorder) {
        if (!supportsOpenGLES2(context)) {
            throw new IllegalStateException("OpenGL ES 2.0 is not supported on this phone.");
        }
        GPUImageParams.context = context;
        mRecorder = recorder;
        mRunOnDraw = new LinkedList<Runnable>();
        initBuffer();
    }

    public void setGLSurfaceView(final GLSurfaceView view) {
        if (mGLSurfaceView != view) {
            mGLSurfaceView = view;
            mGLSurfaceView.setEGLContextClientVersion(2);
            mGLSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
            mGLSurfaceView.getHolder().setFormat(PixelFormat.RGBA_8888);
            mGLSurfaceView.setRenderer(this);
            mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            mGLSurfaceView.requestRender();
        } else {
            Log.w(TAG, "GLSurfaceView already exists");
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mOutputWidth = align(width, 2);
        mOutputHeight = align(height, 2);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        runAll(mRunOnDraw);
    }

    @Override
    synchronized public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    }

    public void setListener(IXMCameraRecorderListener l) {
        mListener = l;
    }

    public void enableGPUCopier(boolean enable) {
        mGPUCopierEnable = enable;
    }

    protected void initFilters() {
        for (int i = 0; i < mFilterArrays.size(); i++) {
            if (mFilterArrays.get(i) != null) {
                mFilterArrays.get(i).init();
            }
        }
    }

    protected void initFilterArrays() {
        for (int i = 0; i <= RenderIndex.DownloadIndex; i++) {
            mFilterArrays.put(i, null);
        }
    }

    protected void releaseFilters() {
        for (int i = 0; i < mFilterArrays.size(); i++) {
            if (mFilterArrays.get(i) != null) {
                mFilterArrays.get(i).destroy();
            }
        }
        mFilterArrays.clear();
    }

    public void release() {
        if (mGLTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, new int[]{mGLTextureId}, 0);
            mGLTextureId = OpenGlUtils.NO_TEXTURE;
        }

        if (mSurfaceTexture != null) {
            mSurfaceTexture.setOnFrameAvailableListener(null);
        }
        mSurfaceTexture = null;

        releaseFilters();
    }

    protected void requestRender() {
        if (mGLSurfaceView != null) {
            mGLSurfaceView.requestRender();
        }
    }

    public void setFilter(final XMFilterType filtertype) {
    }

    public void checkRendererStatus() {
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                mListener.onImageReaderPrepared();
            }
        });
    }

    protected int align(int x, int align) {
        return ((( x ) + (align) - 1) / (align) * (align));
    }

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

    protected void cleanAll(Queue<Runnable> queue) {
        synchronized (queue) {
            while (!queue.isEmpty()) {
                queue.poll();
            }
        }
    }

    protected void runAll(Queue<Runnable> queue) {
        synchronized (queue) {
            while (!queue.isEmpty()) {
                queue.poll().run();
            }
        }
    }

    protected void runOnDraw(Queue<Runnable> queue, final Runnable runnable) {
        synchronized (queue) {
            queue.add(runnable);
        }
    }

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
        public static final int ImageIndex = 9;         // 图片输入索引
        public static final int LogoIndex = 10;         // logo索引
    }
}
