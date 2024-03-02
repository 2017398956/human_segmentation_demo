package com.baidu.paddle.lite.demo.segmentation.util.MediaCodec;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class H264AndH265DeCodePlay {
    private static final String TAG = "H264AndH265DeCodePlay";
    //视频路径
    private final String videoPath;
    //使用android MediaCodec解码
    private MediaCodec mediaCodec;
    private final Surface surface;

    private final String mime;
    private final int width;
    private final int height;
    private final int fps;
    private boolean canDecode = true;

    public H264AndH265DeCodePlay(String videoPath, Surface surface, String mime, int width, int height, int fps) {
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
            //创建解码器 H264的Type为  AAC
            mediaCodec = MediaCodec.createDecoderByType(mime);
            //创建配置
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(mime, width, height);
            //设置解码预期的帧速率【以帧/秒为单位的视频格式的帧速率的键】
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            // 配置绑定mediaFormat和 surface
            mediaCodec.configure(mediaFormat, surface, null, 0);
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
                // 1、IO流方式读取 h264文件【太大的视频分批加载】
                RandomAccessFile randomAccessVideoFile = new RandomAccessFile(new File(videoPath), "r");
                Log.d(TAG, "bytes size " + randomAccessVideoFile.length());
                // 2.
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
                        // 获取下一帧
                        length = findNextFrame(randomAccessVideoFile);
                        Log.d(TAG, "frame size:" + length);
                        if (length == -1) {
                            canDecode = false;
                            break;
                        }
                        // 根据返回的 index 拿到可以用的 buffer
                        currentInputBuffer = mediaCodec.getInputBuffer(inIndex);
                        // 清空缓存
                        currentInputBuffer.clear();
                        //开始为 buffer填充数据
                        currentInputBuffer.put(src, 0, length);
                        //填充数据后通知 mediacodec 查询 inIndex 索引的这个 buffer,
                        mediaCodec.queueInputBuffer(inIndex, 0, length, 0, 0);
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
            } catch (IOException | IllegalStateException e) {
                e.printStackTrace();
            } finally {
                src = null;
                try {
                    mediaCodec.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 当一帧数据超出 20M 时，认为该数据读取出错了
     */
    private int FRAME_MAX_SIZE = 1024 * 1024 * 20;
    /**
     * 给个稍微大点的初始值避免递归中出现 StackOverflowError
     */
    private int frameSize = 1024 * 1024 * 5;

    private byte[] src;

    private int findNextFrame(RandomAccessFile randomAccessFile) {
        if (src == null || src.length < frameSize) {
            src = new byte[frameSize];
        }

        int length;
        try {
            long start = randomAccessFile.getFilePointer();
            if (start >= randomAccessFile.length() - 1) {
                return -1;
            }
            length = randomAccessFile.read(src);
            if (length < 4) {
                return -1;
            }
            for (int i = 2; i < length - 2; i++) {
                if (src[i] == 0x00 && src[i + 1] == 0x00) {
                    if ((src[i + 2] == 0x00 && src[i + 3] == 0x01) // 对 output.h264文件分析 可通过分隔符 0x00000001 读取真正的数据
                            || src[i + 2] == 0x01 //对 output.h265文件分析 可通过分隔符 0x000001 读取真正的数据
                    ) {
                        randomAccessFile.seek(start + i);
                        return i;
                    }
                }
            }
            // 以上都没有返回
            Log.d(TAG, "filePointer:" + randomAccessFile.getFilePointer() + " length:" + randomAccessFile.length());
            if (randomAccessFile.getFilePointer() == randomAccessFile.length()) {
                // 说明是最后一帧了
                return length;
            } else {
                // 说明这一帧的大小超出 maxSize
                frameSize = frameSize + 1024 * 1024;
                if (frameSize > FRAME_MAX_SIZE) {
                    throw new RuntimeException("帧过大");
                }
                randomAccessFile.seek(start);
                return findNextFrame(randomAccessFile);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public void setFrameMaxSize(int frameMaxSize) {
        this.FRAME_MAX_SIZE = frameMaxSize;
    }

    public void release() {
        canDecode = false;
    }
}
