package com.baidu.paddle.lite.demo.segmentation.util.MediaCodec;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 播放某个 track 的视频，不能用于裸流播放，如 h264，h265
 */
public class VideoPlayer {
    private static final String TAG = "VideoPlayer";
    //视频路径
    private final String videoPath;
    private MediaExtractor mediaExtractor;
    //使用 android MediaCodec解码
    private MediaCodec mediaCodec;
    private final Surface surface;

    private final String mime;
    private final int width;
    private final int height;
    private final int fps;
    private boolean canDecode = true;

    public VideoPlayer(String videoPath, Surface surface, String mime, int width, int height, int fps) {
        this.videoPath = videoPath;
        this.surface = surface;
        this.mime = mime;
        this.width = width;
        this.height = height;
        this.fps = fps;
        initMediaCodec();
    }

    private void initMediaCodec() {
        try {
            Log.d(TAG, "videoPath " + videoPath);
            mediaExtractor = new MediaExtractor();
            mediaExtractor.setDataSource(videoPath);
            MediaFormat trackFormat = null;
            String trackMime = null;
            int trackWidth = width;
            int trackHeight = height;
            int trackFPS = fps;
            for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                trackFormat = mediaExtractor.getTrackFormat(i);
                Log.d(TAG, "trackFormat:" + trackFormat);
                trackMime = trackFormat.getString(MediaFormat.KEY_MIME);
                if (!TextUtils.isEmpty(trackMime) && trackMime.startsWith("video")) {
                    trackMime = mime;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        trackWidth = trackFormat.getInteger(MediaFormat.KEY_WIDTH, width);
                        trackHeight = trackFormat.getInteger(MediaFormat.KEY_HEIGHT);
                    }
                    trackFPS = trackFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
                    mediaExtractor.selectTrack(i);
                    break;
                }
                trackFormat = null;
            }
            //创建解码器 H264的Type为  AAC
            mediaCodec = MediaCodec.createDecoderByType(trackMime);
            if (trackFormat == null) {
                trackFormat = MediaFormat.createVideoFormat(trackMime, trackWidth, trackHeight);
                //设置解码预期的帧速率【以帧/秒为单位的视频格式的帧速率的键】
                trackFormat.setInteger(MediaFormat.KEY_FRAME_RATE, trackFPS);
                // 配置绑定mediaFormat和 surface
            }
            mediaCodec.configure(trackFormat, surface, null, 0);
        } catch (IOException e) {
            e.printStackTrace();
            //创建解码失败
            Log.e(TAG, "创建解码失败");
        }
    }

    /**
     * 解码播放
     */
    public void decodePlay() {
        mediaCodec.start();
        new Thread(new DecodeRunnable()).start();
    }

    private class DecodeRunnable implements Runnable {

        @Override
        public void run() {
            try {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                ByteBuffer currentInputBuffer;
                int inIndex;
                int outIndex;
                int length;
                while (canDecode) {
                    // 查询 10000 毫秒后，如果 dSP 芯片的 buffer 全部被占用，返回 -1；存在则不小于 0
                    // FIXME:这里在 release() 时会抛出异常，先 catch 一下
                    // java.lang.IllegalStateException
                    // at android.media.MediaCodec.native_dequeueInputBuffer(Native Method)
                    // at android.media.MediaCodec.dequeueInputBuffer(MediaCodec.java:2332)
                    // FIXME:解决方案？
                    // -1 表示一直等，0 表示不等。按常理传 -1 就行，但实际上在很多机子上会挂掉, 所以传 0，丢帧总比挂掉好
                    // 但改成 0 后 mediaCodec.getInputBuffer(inIndex) 又可能会 crash
                    Log.d(TAG, "before dequeueInput");
                    // inIndex = mediaCodec.dequeueInputBuffer(0);
                    inIndex = mediaCodec.dequeueInputBuffer(10000);
                    Log.d(TAG, "after dequeueInput:" + inIndex);
                    if (inIndex >= 0) {
                        // 根据返回的 index 拿到可以用的 buffer
                        currentInputBuffer = mediaCodec.getInputBuffer(inIndex);
                        // 清空缓存
                        currentInputBuffer.clear();
                        // 获取下一帧
                        length = mediaExtractor.readSampleData(currentInputBuffer, 0);
                        Log.d(TAG, "frame size:" + length);
                        if (length == -1) {
                            canDecode = false;
                            break;
                        }
                        //填充数据后通知 mediacodec 查询 inIndex 索引的这个 buffer,
                        mediaCodec.queueInputBuffer(inIndex, 0, length, 0, 0);
                        mediaExtractor.advance();
                    } else {
                        //等待查询空的 buffer
                        continue;
                    }
                    // mediaCodec 查询 "mediaCodec的输出方队列"得到索引
                    Log.d(TAG, "before dequeueOutputBuffer");
                    // outIndex = mediaCodec.dequeueOutputBuffer(info, 0);
                    outIndex = mediaCodec.dequeueOutputBuffer(info, 10000);
                    Log.d(TAG, "after dequeueOutputBuffer outIndex:" + outIndex);
                    if (outIndex >= 0) {
                        try {
                            // 暂时以休眠线程方式放慢播放速度
                            // 另外还可以减缓数据帧的输入操作，延长解码器的解码时间，否则会造成画面撕裂，native crash 等情况
                            Thread.sleep(1000 / fps);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        // 如果 surface 绑定了，则直接输入到 surface 渲染并释放
                        mediaCodec.releaseOutputBuffer(outIndex, true);
                    } else {
                        Log.d(TAG, "没有解码成功");
                    }
                }
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } finally {
                try {
                    mediaCodec.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void release() {
        canDecode = false;
    }
}
