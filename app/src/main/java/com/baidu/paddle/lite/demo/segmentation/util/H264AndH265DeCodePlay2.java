package com.baidu.paddle.lite.demo.segmentation.util;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class H264AndH265DeCodePlay2 {
    private static final String TAG = "H264DeCodePlay";
    //视频路径
    private String videoPath;
    //使用android MediaCodec解码
    private MediaCodec mediaCodec;
    private Surface surface;

    private String mime;
    private int width, height, fps;

    public H264AndH265DeCodePlay2(String videoPath, Surface surface, String mime, int width, int height, int fps) {
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
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 9);
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
                //1、IO流方式读取 h264文件【太大的视频分批加载】
                byte[] bytes = null;
                bytes = getBytes(videoPath);
                Log.e(TAG, "bytes size " + bytes.length);
                //2、拿到 mediaCodec 所有队列buffer[]
                ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
                //开始位置
                int startIndex = 0;
                //h264总字节数
                int totalSize = bytes.length;
                //3、解析
                while (true) {
                    //判断是否符合
                    if (totalSize == 0 || startIndex >= totalSize) {
                        break;
                    }
                    //寻找索引
                    int nextFrameStart = findByFrame(bytes, startIndex + 1, totalSize);
                    if (nextFrameStart == -1) break;
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    // 查询 10000 毫秒后，如果 dSP 芯片的 buffer 全部被占用，返回 -1；存在则不小于 0
                    int inIndex = mediaCodec.dequeueInputBuffer(10000);
                    if (inIndex >= 0) {
                        //根据返回的 index 拿到可以用的 buffer
                        ByteBuffer byteBuffer = inputBuffers[inIndex];
                        // 清空缓存
                        byteBuffer.clear();
                        //开始为 buffer填充数据
                        byteBuffer.put(bytes, startIndex, nextFrameStart - startIndex);
                        //填充数据后通知 mediacodec 查询 inIndex 索引的这个 buffer,
                        mediaCodec.queueInputBuffer(inIndex, 0, nextFrameStart - startIndex, 0, 0);
                        //为下一帧做准备，下一帧首就是前一帧的尾。
                        startIndex = nextFrameStart;
                    } else {
                        //等待查询空的buffer
                        continue;
                    }
                    //mediaCodec 查询 "mediaCodec的输出方队列"得到索引
                    int outIndex = mediaCodec.dequeueOutputBuffer(info, 10000);
                    Log.e(TAG, "outIndex " + outIndex);
                    if (outIndex >= 0) {
//                        try {
//                            //暂时以休眠线程方式放慢播放速度
//                            Thread.sleep(1000 / fps);
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
                        //如果surface绑定了，则直接输入到surface渲染并释放
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


    //读取一帧数据
    private int findByFrame(byte[] bytes, int start, int totalSize) {
        for (int i = start; i < totalSize - 4; i++) {
            if (bytes[i] == 0x00 && bytes[i + 1] == 0x00) {
                if (bytes[i + 2] == 0x00 && bytes[i + 3] == 0x01) {
                    //对 output.h264文件分析 可通过分隔符 0x00000001 读取真正的数据
                    return i;
                } else if (bytes[i + 2] == 0x01) {
                    //对 output.h265文件分析 可通过分隔符 0x000001 读取真正的数据
                    return i;
                }
            }
        }
        return -1;
    }

    private byte[] getBytes(String videoPath) throws IOException {
        InputStream is = new DataInputStream(new FileInputStream(new File(videoPath)));
        int len;
        int size = 1024;
        byte[] buf;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        buf = new byte[size];
        while ((len = is.read(buf, 0, size)) != -1)
            bos.write(buf, 0, len);
        buf = bos.toByteArray();
        return buf;
    }
}

