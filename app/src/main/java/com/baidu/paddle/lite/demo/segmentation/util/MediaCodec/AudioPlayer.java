package com.baidu.paddle.lite.demo.segmentation.util.MediaCodec;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * MediaCodec解码器
 */
public class AudioPlayer {
    private static final String TAG = "AudioPlayer";
    private MediaExtractor mediaExtractor;
    private MediaCodec mediaCodec;
    private AudioTrack audioTrack;
    private String srcPath;
    private boolean isFinish = false;
    private String dstPath;

    public void setDstPath(String dstPath) {
        this.dstPath = dstPath;
    }

    /**
     * 解码音频
     *
     * @param srcPath 源文件路径
     */
    public void decodeAudio(String srcPath) {
        this.srcPath = srcPath;
        //初始化解码器
        initMediaCodec();
        //初始化播放器
        initAudioTrack();
        //开始解码播放
        new Thread(() -> decodeAndPlay()).start();
    }

    /**
     * 实际的解码工作
     */
    private void decodeAndPlay() {
        OutputStream os = null;
        if (!TextUtils.isEmpty(dstPath)){
            try {
                os = new FileOutputStream(dstPath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        ByteBuffer inputBuffer;
        while (!isFinish && mediaCodec != null) {
            // 获取可用的 inputBuffer，输入参数 -1 代表一直等到，0 代表不等待，10*1000代表 10 秒超时
            // 超时时间 10 秒
            long TIME_OUT_US = 10 * 1000;
            int inputIndex = mediaCodec.dequeueInputBuffer(TIME_OUT_US);
            if (inputIndex < 0) {
                break;
            }
            inputBuffer = mediaCodec.getInputBuffer(inputIndex);
            if (inputBuffer != null) {
                inputBuffer.clear();
            }else {
                continue;
            }
            // 读取数据到 bytebuffer 中，从 offset 偏移开始。选定轨道之后可以读取该轨道的数据。
            int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);
            if (sampleSize > 0) {
                // 入队解码
                mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, 0, 0);
                // 移动到下一个采样点
                mediaExtractor.advance();
            } else {
                break;
            }
            //取解码后的数据
            int outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIME_OUT_US);
            // 不一定能一次取完，所以要循环取
            ByteBuffer outputBuffer;
            byte[] pcmData;
            while (outputIndex >= 0) {
                outputBuffer = mediaCodec.getOutputBuffer(outputIndex);
                pcmData = new byte[bufferInfo.size];
                if (outputBuffer != null) {
                    outputBuffer.get(pcmData);
                    outputBuffer.clear();//用完后清空，复用
                }
                // 播放 pcm 数据
                audioTrack.write(pcmData, 0, bufferInfo.size);
                // 写入到本地文件中
                if (os != null) {
                    try {
                        os.write(pcmData);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                // 释放
                mediaCodec.releaseOutputBuffer(outputIndex, false);
                // 再次获取数据
                outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIME_OUT_US);
            }
        }
        //释放解码器
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
            Log.e(TAG, "stopPlay");
        }
        if (os != null) {
            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 初始化 MediaCodec
     */
    private void initMediaCodec() {
        try {
            mediaExtractor = new MediaExtractor();
            mediaExtractor.setDataSource(srcPath);
            //找到音频流的索引
            int audioTrackIndex = -1;
            String mime = null;
            MediaFormat trackFormat = null;
            for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                trackFormat = mediaExtractor.getTrackFormat(i);
                Log.d(TAG, "trackIndexFormat:" + trackFormat);
                mime = trackFormat.getString(MediaFormat.KEY_MIME);
                if (!TextUtils.isEmpty(mime) && mime.startsWith("audio")) {
                    audioTrackIndex = i;
                    Log.d(TAG, "找到音频流的索引为：" + audioTrackIndex + " 音频mime：" + mime);
                    break;
                }
            }
            //没有找到音频流的情况下
            if (audioTrackIndex == -1) {
                Log.e(TAG, "initAudioDecoder: 没有找到音频流");
                return;
            }
            // 选定特定的轨道，会影响 readSampleData(ByteBuffer, int),
            // getSampleTrackIndex() and getSampleTime()的输出，
            // 这三个函数输出的是选定轨道的信息。
            mediaExtractor.selectTrack(audioTrackIndex);
            // 创建解码器
            mediaCodec = MediaCodec.createDecoderByType(mime);
            mediaCodec.configure(trackFormat, null, null, 0);
            mediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 初始化播放器
     */
    private void initAudioTrack() {
        int streamType = AudioManager.STREAM_MUSIC;
        int sampleRate = 44100;
        int channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int mode = AudioTrack.MODE_STREAM;
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        audioTrack = new AudioTrack(streamType, sampleRate, channelConfig, audioFormat,
                Math.max(minBufferSize, 2048), mode);
        audioTrack.play();
    }

    public void onDestroy() {
        isFinish = true;
    }
}
