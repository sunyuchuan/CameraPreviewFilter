package com.xmly.media.camera.view.encoder;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import com.xmly.media.camera.view.recorder.IXMCameraRecorderListener;
import com.xmly.media.camera.view.recorder.XMMediaRecorder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ImageReader录制，我在这里将图片传回了主界面显示，你需要自己改装
 *
 * @author Created by jz on 2017/4/8 14:53
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
public class ImageEncoderCore {
    private static final String TAG = "ImageEncoderCore";
    private static final boolean VERBOSE = false;

    private static final int MAX_IMAGE_NUMBER = 25;//这个值代表ImageReader最大的存储图像
    private static final int ENCODER_BITMAP = 0;

    private int mWidth;
    private int mHeight;

    private int[] mPixelData;
    private List<byte[]> mReusableBuffers;

    private ImageReader mImageReader;
    private Surface mInputSurface;

    //private EncoderThread mEncoderThread;

    private List<ImageInfo> mList;

    private OnImageEncoderListener mOnImageEncoderListener;
    private IXMCameraRecorderListener mIXMCameraRecorderListener = null;
    private XMMediaRecorder mNativeRecorder = null;
    private volatile boolean isPutting = false;
    private boolean isFirst = true;

    //这里的width=240，height=320。为了测试实时浏览，尽可能的小，防止转换消耗时间
    public ImageEncoderCore(int width, int height, OnImageEncoderListener l) {
        this.mWidth = width;
        this.mHeight = height;
        this.mPixelData = new int[width * height];
        this.mReusableBuffers = Collections.synchronizedList(new ArrayList<byte[]>());

        this.mOnImageEncoderListener = l;
        this.mImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, MAX_IMAGE_NUMBER);

        mList = Collections.synchronizedList(new ArrayList<ImageInfo>());

        mInputSurface = mImageReader.getSurface();

        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @TargetApi(Build.VERSION_CODES.KITKAT)
            @Override
            public void onImageAvailable(ImageReader reader) {
                if (VERBOSE) Log.i(TAG, "in onImageAvailable");
                try {
                    Image image = reader.acquireNextImage();//获取下一个
                    Image.Plane[] planes = image.getPlanes();
                    int width = image.getWidth();//设置的宽
                    int height = image.getHeight();//设置的高
                    int pixelStride = planes[0].getPixelStride();//内存对齐参数
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * width;
                    byte[] data = getBuffer(rowStride * height);//获得byte
                    ByteBuffer buffer = planes[0].getBuffer();//获得buffer
                    buffer.get(data);//将buffer数据写入byte中

                    if (isFirst && mIXMCameraRecorderListener != null) {
                        isFirst = false;
                        mIXMCameraRecorderListener.onImageReaderPrepared();
                    }
                    if (mNativeRecorder != null && isPutting)
                        mNativeRecorder.put(data, mWidth, mHeight, pixelStride, rowPadding, 0, false, false);
                    //mList.add(new ImageInfo(data, pixelStride, rowPadding));
                    image.close();//用完需要关闭
                    mReusableBuffers.add(data);
                } catch (Exception e) {
                    if(mIXMCameraRecorderListener != null) {
                        mIXMCameraRecorderListener.onRecorderError();
                    }
                    e.printStackTrace();
                }
            }
        }, null);

        //mEncoderThread = new EncoderThread();
        //mEncoderThread.start();
    }

    private class EncoderThread extends Thread {//这里把byte转换为bitmap，实际效率比较低下，这里只是展示用

        @Override
        public void run() {
            while (mImageReader != null) {
                if (mList.isEmpty()) {
                    SystemClock.sleep(1);
                    continue;
                }

                final ImageInfo info = mList.remove(0);
                final byte[] data = info.data;
                final int pixelStride = info.pixelStride;
                final int rowPadding = info.rowPadding;

                int offset = 0;
                int index = 0;
                for (int i = 0; i < mHeight; ++i) {
                    for (int j = 0; j < mWidth; ++j) {
                        int pixel = 0;
                        pixel |= (data[offset] & 0xff) << 16;     // R
                        pixel |= (data[offset + 1] & 0xff) << 8;  // G
                        pixel |= (data[offset + 2] & 0xff);       // B
                        pixel |= (data[offset + 3] & 0xff) << 24; // A
                        mPixelData[index++] = pixel;
                        offset += pixelStride;
                    }
                    offset += rowPadding;
                }

                Bitmap bitmap = Bitmap.createBitmap(mPixelData,
                        mWidth, mHeight,
                        Bitmap.Config.ARGB_8888);
                Message message = Message.obtain();
                message.what = ENCODER_BITMAP;
                message.obj = bitmap;
                mHandler.sendMessage(message);

                mReusableBuffers.add(data);
            }
        }
    }

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case ENCODER_BITMAP:
                    if (mOnImageEncoderListener != null) {
                        mOnImageEncoderListener.onImageEncoder((Bitmap) msg.obj);
                    }
                    break;
            }
        }
    };

    public void setListener(IXMCameraRecorderListener l) {
        mIXMCameraRecorderListener = l;
    }

    public void setNativeRecorder(XMMediaRecorder recorder)
    {
        mNativeRecorder = recorder;
    }

    public void startPutData(boolean isPutting)
    {
        this.isPutting = isPutting;
    }

    /**
     * Returns the encoder's input surface.
     */
    public Surface getInputSurface() {
        return mInputSurface;
    }

    /**
     * Releases encoder resources.
     */
    public void release() {
        if (VERBOSE) Log.d(TAG, "releasing encoder objects");
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        /*try {
            mEncoderThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/
        if (mList != null) {
            mList.clear();
            mList = null;
        }
        if (mReusableBuffers != null) {
            mReusableBuffers.clear();
            mReusableBuffers = null;
        }
        if (mHandler != null) {
            mHandler.removeMessages(ENCODER_BITMAP);
            mHandler = null;
        }
        mOnImageEncoderListener = null;
    }

    private byte[] getBuffer(int length) {
        if (mReusableBuffers.isEmpty()) {
            return new byte[length];
        } else {
            return mReusableBuffers.remove(0);
        }
    }

    public interface OnImageEncoderListener {
        void onImageEncoder(Bitmap bitmap);
    }

    private static class ImageInfo {
        final byte[] data;
        final int pixelStride;
        final int rowPadding;

        ImageInfo(byte[] data, int pixelStride, int rowPadding) {
            this.data = data;
            this.pixelStride = pixelStride;
            this.rowPadding = rowPadding;
        }
    }

}
