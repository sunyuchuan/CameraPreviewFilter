package com.xmly.media.video.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Map;

/**
 * Created by sunyc on 19-3-1.
 */

public class XMPlayer implements IMediaPlayer, ISurfacePreparedListener {
    private static final String TAG = "XMPlayer";
    private static final int FPS = 25;
    private static final int BITRATE = 700000;
    private MediaPlayer mp;
    private boolean isSurfaceCreated;
    private boolean isDataSourceSet;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;
    private int mVideoFps = 0;
    private int mBitRate = 0;

    public XMPlayer() {
    }

    public void init() {
        stop();
        release();
        mp = new MediaPlayer();
        setScreenOnWhilePlaying(true);
        setOnCompletionListener(mOnCompletionListener);
        setOnPreparedListener(mOnPreparedListener);
        setOnSeekCompleteListener(mOnSeekCompleteListener);
        setOnInfoListener(mOnInfoListener);
        setOnBufferingUpdateListener(mOnBufferingUpdateListener);
        setOnErrorListener(mOnErrorListener);
        setOnVideoSizeChangedListener(mOnVideoSizeChangedListener);
    }

    @Override
    public void setDataSource(Context context, Uri uri)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {

    }

    @Override
    public void setDataSource(Context context, Uri uri, Map<String, String> headers)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {

    }

    @Override
    public void setDataSource(FileDescriptor fd)
            throws IOException, IllegalArgumentException, IllegalStateException {

    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
    @Override
    public void setDataSource(String path)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        reset();
        try {
            if(mp != null && path != null)
                mp.setDataSource(path);
            else
                throw new IllegalArgumentException();

            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(path);
            mVideoWidth = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            mVideoHeight = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            //mVideoFps = (int) Float.parseFloat(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE));
            //mBitRate = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));

            isDataSourceSet = true;
            if (isSurfaceCreated) {
                prepareAsync();
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            throw new IOException(e);
        }
    }

    @Override
    public void prepareAsync() {
        if (mp != null) {
            mp.prepareAsync();
        }
    }

    @Override
    public void start() {
        if (mp != null) {
            mp.start();
        }
    }

    @Override
    public void stop() {
        if (mp != null) {
            mp.stop();
        }
    }

    @Override
    public void pause() {
        if (mp != null) {
            mp.pause();
        }
    }

    @Override
    public void setScreenOnWhilePlaying(boolean screenOn) {
        if (mp != null) {
            mp.setScreenOnWhilePlaying(screenOn);
        }
    }

    @Override
    public int getVideoWidth() {
        return mVideoWidth;
    }

    @Override
    public int getVideoHeight() {
        return mVideoHeight;
    }

    @Override
    public boolean isPlaying() {
        if (mp != null) {
            return mp.isPlaying();
        }
        return false;
    }

    @Override
    public void seekTo(int msec) throws IllegalStateException {
        if (mp != null) {
            mp.seekTo(msec);
        }
    }

    @Override
    public long getCurrentPosition() {
        if (mp != null) {
            return mp.getCurrentPosition();
        }
        return 0;
    }

    @Override
    public long getDuration() {
        if (mp != null) {
            return mp.getDuration();
        }
        return 0;
    }

    @Override
    public void release() {
        if (mp != null) {
            mp.release();
            isSurfaceCreated = false;
            isDataSourceSet = false;
            mp = null;
        }
    }

    @Override
    public void reset() {
        if (mp != null) {
            mp.reset();
        }
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        if (mp != null) {
            mp.setVolume(leftVolume, rightVolume);
        }
    }

    @Override
    public void setOnPreparedListener(MediaPlayer.OnPreparedListener listener) {
        mOnPreparedListener = listener;
        if (mp != null) {
            mp.setOnPreparedListener(listener);
        }
    }

    @Override
    public void setOnCompletionListener(MediaPlayer.OnCompletionListener listener) {
        mOnCompletionListener = listener;
        if (mp != null) {
            mp.setOnCompletionListener(listener);
        }
    }

    @Override
    public void setOnBufferingUpdateListener(MediaPlayer.OnBufferingUpdateListener listener) {
        mOnBufferingUpdateListener = listener;
        if (mp != null) {
            mp.setOnBufferingUpdateListener(listener);
        }
    }

    @Override
    public void setOnSeekCompleteListener(MediaPlayer.OnSeekCompleteListener listener) {
        mOnSeekCompleteListener = listener;
        if (mp != null) {
            mp.setOnSeekCompleteListener(listener);
        }
    }

    @Override
    public void setOnVideoSizeChangedListener(MediaPlayer.OnVideoSizeChangedListener listener) {
        mOnVideoSizeChangedListener = listener;
        if (mp != null) {
            mp.setOnVideoSizeChangedListener(listener);
        }
    }

    @Override
    public void setOnErrorListener(MediaPlayer.OnErrorListener listener) {
        mOnErrorListener = listener;
        if (mp != null) {
            mp.setOnErrorListener(listener);
        }
    }

    @Override
    public void setOnInfoListener(MediaPlayer.OnInfoListener listener) {
        mOnInfoListener = listener;
        if (mp != null) {
            mp.setOnInfoListener(listener);
        }
    }

    @Override
    public void setAudioStreamType(int streamtype) {
        if (mp != null) {
           mp.setAudioStreamType(streamtype);
        }
    }

    @Override
    public void setKeepInBackground(boolean keepInBackground) {

    }

    @Override
    public void setWakeMode(Context context, int mode) {
        if (mp != null) {
            mp.setWakeMode(context, mode);
        }
    }

    @Override
    public void setLooping(boolean looping) {
        if (mp != null) {
            mp.setLooping(looping);
        }
    }

    @Override
    public boolean isLooping() {
        if (mp != null) {
            return mp.isLooping();
        }
        return false;
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void setSurface(Surface surface) {
        if (mp != null) {
            mp.setSurface(surface);
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void surfacePrepared(Surface surface) {
        setSurface(surface);
        surface.release();
        isSurfaceCreated = true;
        if (isDataSourceSet) {
            prepareAsync();
        }
    }

    public int getVideoFps() {
        Log.i(TAG,"mVideoFps " + mVideoFps);
        return mVideoFps < 2 ? FPS : mVideoFps;
    }

    public int getVideoBitRate() {
        Log.i(TAG,"mBitRate " + mBitRate);
        return mBitRate < 100 ? BITRATE : mBitRate;
    }

    private MediaPlayer.OnVideoSizeChangedListener mOnVideoSizeChangedListener =
            new MediaPlayer.OnVideoSizeChangedListener() {
                @Override
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height)
                {
                    Log.i(TAG, "onVideoSizeChanged");
                }
            };

    private MediaPlayer.OnBufferingUpdateListener mOnBufferingUpdateListener =
            new MediaPlayer.OnBufferingUpdateListener() {
                @Override
                public void onBufferingUpdate(MediaPlayer mp, int percent)
                {
                    Log.i(TAG, "onBufferingUpdate");
                }
            };

    private MediaPlayer.OnCompletionListener mOnCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.i(TAG, "onCompletion");
                    stop();
                    release();
                }
            };

    private MediaPlayer.OnErrorListener mOnErrorListener =
            new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "what " + what + ", extra " + extra);
                    stop();
                    release();
                    return true;
                }
            };

    private MediaPlayer.OnInfoListener mOnInfoListener =
            new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer mp, int what, int extra) {
                    Log.i(TAG, "what " + what + ", extra " + extra);
                    return true;
                }
            };

    private MediaPlayer.OnPreparedListener mOnPreparedListener =
            new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    Log.i(TAG, "onPrepared");
                    start();
                }
            };

    private MediaPlayer.OnSeekCompleteListener mOnSeekCompleteListener =
            new MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer mp) {
                    Log.i(TAG, "onSeekComplete");
                }
            };
}
