package com.xmly.media.video.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Log;

import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.gles.filter.GPUImageFilter;
import com.xmly.media.gles.filter.GPUImageFilterFactory;
import com.xmly.media.gles.filter.GPUImageImageSwitchFilter;
import com.xmly.media.gles.filter.GPUImageLogoFilter;
import com.xmly.media.gles.filter.GPUImageYUY2PixelCopierFilter;
import com.xmly.media.gles.utils.OpenGlUtils;
import com.xmly.media.gles.utils.Rotation;
import com.xmly.media.gles.utils.TextureRotationUtil;
import com.xmly.media.gles.utils.XMFilterType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by sunyc on 19-5-16.
 */

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class XMImageRenderer extends XMBaseRenderer {
    private static final String TAG = "XMImageRenderer";
    private FloatBuffer mGLVideoTextureBuffer;
    private XMFilterType mFilterType = XMFilterType.NONE;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mImageWidth;
    private int mImageHeight;
    private String mImagePath = null;

    private void initBuffer() {
        mGLVideoTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLVideoTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);
    }

    public XMImageRenderer(final Context context, XMMediaRecorder recorder) {
        super(context, recorder);
        releaseFilters();
        initFilterArrays();
        mFilterArrays.put(RenderIndex.ImageIndex, GPUImageFilterFactory.CreateFilter(XMFilterType.NONE));
        mFilterArrays.put(RenderIndex.FilterIndex, GPUImageFilterFactory.CreateFilter(mFilterType));
        mFilterArrays.put(RenderIndex.LogoIndex, new GPUImageLogoFilter());
        mFilterArrays.put(RenderIndex.RotateIndex, GPUImageFilterFactory.CreateFilter(XMFilterType.NONE));
        mFilterArrays.put(RenderIndex.DisplayIndex, GPUImageFilterFactory.CreateFilter(XMFilterType.NONE));
        mFilterArrays.put(RenderIndex.DownloadIndex, new GPUImageYUY2PixelCopierFilter(mRecorder));
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

        adjustImageScaling(mVideoWidth, mVideoHeight, mOutputWidth, mOutputHeight,
                Rotation.NORMAL, false, false, mGLTextureBuffer);
    }

    @Override
    public void onDrawFrame(final GL10 gl) {
        super.onDrawFrame(gl);

        int texture = OpenGlUtils.NO_TEXTURE;
        mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, false)).position(0);
        if (mFilterArrays.get(RenderIndex.ImageIndex) != null) {
            texture = mFilterArrays.get(RenderIndex.ImageIndex).onDrawToTexture(mGLTextureId, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
        }
        if (mFilterArrays.get(RenderIndex.FilterIndex) != null) {
            texture = mFilterArrays.get(RenderIndex.FilterIndex).onDrawToTexture(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
        }
        if (mFilterArrays.get(RenderIndex.LogoIndex) != null) {
            texture = mFilterArrays.get(RenderIndex.LogoIndex).onDrawToTexture(texture, mGLCubeBuffer, mGLVideoTextureBuffer);
        }
        if (mFilterArrays.get(RenderIndex.RotateIndex) != null) {
            texture = mFilterArrays.get(RenderIndex.RotateIndex).onDrawToTexture(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
        }
        if (mFilterArrays.get(RenderIndex.DisplayIndex) != null) {
            mFilterArrays.get(RenderIndex.DisplayIndex).onDraw(texture, mGLCubeBuffer, mGLTextureBuffer);
        }
        if (mGPUCopierEnable) {
            mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);
            if (mFilterArrays.get(RenderIndex.DownloadIndex) != null) {
                mFilterArrays.get(RenderIndex.DownloadIndex).onDrawToTexture(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
            }
        }
    }

    public void setVideoSize(int w, int h) {
        mVideoWidth = w;
        mVideoHeight = h;

        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                filtersSizeChanged();
            }
        });
        adjustImageScaling(mImageWidth, mImageHeight, mVideoWidth, mVideoHeight,
                Rotation.NORMAL, false, false, mGLVideoTextureBuffer);

        adjustImageScaling(mVideoWidth, mVideoHeight, mOutputWidth, mOutputHeight,
                Rotation.NORMAL, false, false, mGLTextureBuffer);
    }

    private void setImageSize(int w, int h) {
        mImageWidth = w;
        mImageHeight = h;
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                filtersSizeChanged();
            }
        });

        adjustImageScaling(mImageWidth, mImageHeight, mVideoWidth, mVideoHeight,
                Rotation.NORMAL, false, false, mGLVideoTextureBuffer);
    }

    private void setSwitchFilterImage(String imagePath) {
        Bitmap bmp = GPUImageFilterFactory.decodeBitmap(imagePath);
        setImageSize(bmp.getWidth(), bmp.getHeight());

        if (mFilterArrays.get(RenderIndex.FilterIndex) instanceof GPUImageImageSwitchFilter) {
            ((GPUImageImageSwitchFilter) mFilterArrays.get(RenderIndex.FilterIndex)).setBitmap(bmp);
        }
    }

    public void setNextImage(String imagePath) {
        final Bitmap bmp = GPUImageFilterFactory.decodeBitmap(imagePath);
        setImageSize(bmp.getWidth(), bmp.getHeight());
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                mGLTextureId = OpenGlUtils.loadTexture(bmp, mGLTextureId, true);
            }
        });
    }

    public void setImage(String imagePath) {
        mImagePath = imagePath;
        if (mFilterArrays.get(RenderIndex.FilterIndex) instanceof GPUImageImageSwitchFilter) {
            setSwitchFilterImage(imagePath);
        } else {
            setNextImage(imagePath);
        }
    }

    public void setLogo(final Bitmap bmp, final float[] rect) {
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                if (mFilterArrays.get(RenderIndex.LogoIndex) != null) {
                    mFilterArrays.get(RenderIndex.LogoIndex).destroy();
                    mFilterArrays.put(RenderIndex.LogoIndex, null);
                }
                GPUImageLogoFilter filter = new GPUImageLogoFilter();
                filter.init();
                GLES20.glUseProgram(filter.getProgram());
                filter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
                filter.onInputSizeChanged(mVideoWidth, mVideoHeight);
                filter.setBitmap(bmp);
                filter.setRectangleCoordinate(rect);
                mFilterArrays.put(RenderIndex.LogoIndex, filter);
            }
        });
    }

    public void setFilter(final XMFilterType filtertype) {
        if (mFilterArrays.get(RenderIndex.FilterIndex) instanceof GPUImageImageSwitchFilter) {
            setNextImage(mImagePath);
        }
        mFilterType = filtertype;

        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                if (mFilterArrays.get(RenderIndex.FilterIndex) != null) {
                    mFilterArrays.get(RenderIndex.FilterIndex).destroy();
                    mFilterArrays.put(RenderIndex.FilterIndex, null);
                }
                GPUImageFilter filter = GPUImageFilterFactory.CreateFilter(filtertype);
                if (filter instanceof GPUImageImageSwitchFilter) {
                    ((GPUImageImageSwitchFilter) filter).setListener(onImageSwitchListener);
                }
                filter.init();
                GLES20.glUseProgram(filter.getProgram());
                filter.onOutputSizeChanged(mImageWidth, mImageHeight);
                filter.onInputSizeChanged(mImageWidth, mImageHeight);
                mFilterArrays.put(RenderIndex.FilterIndex, filter);
            }
        });
    }

    private void filtersSizeChanged() {
        if (mFilterArrays.get(RenderIndex.ImageIndex) != null) {
            mFilterArrays.get(RenderIndex.ImageIndex).onOutputSizeChanged(mImageWidth, mImageHeight);
        }
        if (mFilterArrays.get(RenderIndex.FilterIndex) != null) {
            mFilterArrays.get(RenderIndex.FilterIndex).onInputSizeChanged(mImageWidth, mImageHeight);
            mFilterArrays.get(RenderIndex.FilterIndex).onOutputSizeChanged(mImageWidth, mImageHeight);
        }
        if (mFilterArrays.get(RenderIndex.LogoIndex) != null) {
            mFilterArrays.get(RenderIndex.LogoIndex).onOutputSizeChanged(mVideoWidth, mVideoHeight);
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

    public void cleanRunOnDraw() {
        if(mRunOnDraw != null)
            cleanAll(mRunOnDraw);
    }

    private GPUImageImageSwitchFilter.IImageSwitchListener onImageSwitchListener = new GPUImageImageSwitchFilter.IImageSwitchListener() {
        @Override
        public void onImageSwitchCompleted() {
            setNextImage(mImagePath);
        }
    };
}
