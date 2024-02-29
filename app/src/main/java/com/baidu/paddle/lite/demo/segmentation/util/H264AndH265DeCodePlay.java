package com.baidu.paddle.lite.demo.segmentation.util;

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
    private String videoPath;
    //使用android MediaCodec解码
    private MediaCodec mediaCodec;
    private Surface surface;

    private String mime;
    private int width, height, fps;

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
            Log.e(TAG, "videoPath " + videoPath);
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
        new Thread(new MyRun()).start();
    }

    private class MyRun implements Runnable {

        @Override
        public void run() {
            try {
                // 1、IO流方式读取 h264文件【太大的视频分批加载】
                RandomAccessFile randomAccessVideoFile = new RandomAccessFile(new File(videoPath), "r");
                Log.e(TAG, "bytes size " + randomAccessVideoFile.length());
                // 2、拿到 mediaCodec 所有队列buffer[]
                ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
                // 3、解析
                byte[] bytes;
                while (true) {
                    bytes = findNextFrame(randomAccessVideoFile);
                    if (bytes == null) {
                        mediaCodec.release();
                        break;
                    }
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    // 查询 10000 毫秒后，如果 dSP 芯片的 buffer 全部被占用，返回 -1；存在则不小于 0
                    Log.d(TAG, "before dequeueInput and frame size:" + bytes.length);
                    int inIndex = mediaCodec.dequeueInputBuffer(10000);
                    Log.d(TAG, "after dequeueInput:" + inIndex);
                    if (inIndex >= 0) {
                        //根据返回的 index 拿到可以用的 buffer
                        ByteBuffer byteBuffer = inputBuffers[inIndex];
                        // 清空缓存
                        byteBuffer.clear();
                        //开始为 buffer填充数据
                        byteBuffer.put(bytes);
                        //填充数据后通知 mediacodec 查询 inIndex 索引的这个 buffer,
                        mediaCodec.queueInputBuffer(inIndex, 0, bytes.length, 0, 0);
                    } else {
                        //等待查询空的 buffer
                        continue;
                    }
                    // mediaCodec 查询 "mediaCodec的输出方队列"得到索引
                    int outIndex = mediaCodec.dequeueOutputBuffer(info, 10000);
                    Log.e(TAG, "outIndex " + outIndex);
                    if (outIndex >= 0) {
                        try {
                            // 暂时以休眠线程方式放慢播放速度
                            // 另外还可以减缓数据帧的输入操作，延长解码器的解码时间，否则会造成画面撕裂，native crash 等情况
                            Thread.sleep(1000 / fps);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        // 如果surface绑定了，则直接输入到surface渲染并释放
                        mediaCodec.releaseOutputBuffer(outIndex, true);
                    } else {
                        Log.e(TAG, "没有解码成功");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 当一帧数据超出 20M 时，认为该数据读取出错了
     */
    private final int FRAME_MAX_SIZE = 1024 * 1024 * 20;
    /**
     * 给个稍微大点的初始值避免递归中出现 StackOverflowError
     */
    private int frameSize = 1024 * 1024 * 5;
    private byte[] src;

    private byte[] findNextFrame(RandomAccessFile randomAccessFile) {
        src = new byte[frameSize];
        int length;
        try {
            long start = randomAccessFile.getFilePointer();
            if (start >= randomAccessFile.length() - 1) {
                return null;
            }
            length = randomAccessFile.read(src);
            if (length < 4) {
                return null;
            }
            for (int i = 2; i < length - 2; i++) {
                if (src[i] == 0x00 && src[i + 1] == 0x00) {
                    if ((src[i + 2] == 0x00 && src[i + 3] == 0x01) // 对 output.h264文件分析 可通过分隔符 0x00000001 读取真正的数据
                            || src[i + 2] == 0x01 //对 output.h265文件分析 可通过分隔符 0x000001 读取真正的数据
                    ) {
                        randomAccessFile.seek(start + i);
                        byte[] bytes = new byte[i];
                        System.arraycopy(src, 0, bytes, 0, bytes.length);
                        return bytes;
                    }
                }
            }
            // 以上都没有返回
            Log.d(TAG, "filePointer:" + randomAccessFile.getFilePointer() + " length:" + randomAccessFile.length());
            if (randomAccessFile.getFilePointer() == randomAccessFile.length() - 1) {
                // 说明是最后一帧了
                byte[] bytes = new byte[length];
                System.arraycopy(src, 0, bytes, 0, bytes.length);
                return bytes;
            } else {
                // 说明这一帧的大小超出 maxSize
                frameSize = frameSize + 1024 * 1024;
                if (frameSize > FRAME_MAX_SIZE) {
                    throw new RuntimeException("帧过大");
                }
                randomAccessFile.seek(start);
                return findNextFrame(randomAccessFile);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void release() {
        src = null;
        if (mediaCodec != null) {
            mediaCodec.release();
        }
    }
}
