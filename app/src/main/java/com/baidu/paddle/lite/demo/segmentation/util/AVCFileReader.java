package com.baidu.paddle.lite.demo.segmentation.util;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;

/**
 * 解析本地文件获取h264视频流数据
 */
public class AVCFileReader extends Thread {
    //文件路径
    private String path = "sdcard/mc_video.h264";
    //文件读取完成标识
    private boolean isFinish = false;
    private AVCDecoder mDecoder;

    public void setDecoder(AVCDecoder decoder) {
        mDecoder = decoder;
    }

    @Override
    public void run() {
        super.run();
        File file = new File(path);
        //判断文件是否存在
        if (file.exists()) {
            try {
                FileInputStream fis = new FileInputStream(file);
                // 保存帧文件的时候，每帧数据的前4个字节记录了当前帧的长度，方便读取
                byte[] frameLength = new byte[4];
                //当前帧长度
                int frameLen = 0;
                //每次从文件读取的数据
                byte[] readData;
                //循环读取数据
                int count = 0;
                while (!isFinish) {
                    if (fis.available() > 0) {
                        // 读取帧长度
                        fis.read(frameLength);
                        frameLen = CodecUtils.bytesToInt(frameLength);
                        readData = new byte[frameLen];
                        // 读取帧内容
                        fis.read(readData);
                        count++;
                        onFrame(readData, 0, readData.length);
                        try {
                            // 由于每帧数据读取完毕立即丢给解码器显示，没有时间戳（PTS、DTS）控制解码显示
                            // 这里通过sleep做个简单的显示控制 1000/60 ≈ 16
                            Thread.sleep(16);
                        } catch (Exception e) {
                            Log.w(TAG, e);
                        }
                    } else {
                        //文件读取结束
                        isFinish = true;
                    }
                }

                //Log.i(TAG, "frameLen finish " + count);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String TAG = getClass().getSimpleName();

    /**
     * 将帧内容交给解析器解析
     * @param frame
     * @param offset
     * @param length
     */
    private void onFrame(byte[] frame, int offset, int length) {
        //Log.i(TAG, "onFrame " + offset + "/" + length);
        mDecoder.onFrame(frame, offset, length);
    }
}

