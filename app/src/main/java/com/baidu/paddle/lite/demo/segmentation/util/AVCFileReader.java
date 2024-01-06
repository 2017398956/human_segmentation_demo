package com.baidu.paddle.lite.demo.segmentation.util;

import android.media.MediaFormat;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;

/**
 * 解析本地文件获取的已经修改的 h264 视频流数据
 */
public class AVCFileReader extends Thread {

    // 一帧数据中前多少位用于保存该帧的长度
    public final static int FRAME_LENGTH = 4;
    //文件路径
    private String path ;
    //文件读取完成标识
    private boolean isFinish = false;
    private AVCDecoder mDecoder;
    private PlayListener playListener ;
    private int fps = 30 ;

    private AVCFileReader(){}

    public AVCFileReader(String videoPath , AVCDecoder decoder){
        try {
            fps = decoder.getMediaFormat().getInteger(MediaFormat.KEY_FRAME_RATE) ;
        }catch (Exception e){
        }
        this.path = videoPath ;
        this.mDecoder = decoder ;
        if (null != playListener){
            playListener.onReady();
        }
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
                byte[] frameLength = new byte[FRAME_LENGTH];
                //当前帧长度
                int frameLen ;
                //每次从文件读取的数据
                byte[] readData;
                //循环读取数据
                int count = 0;
                if (null != playListener){
                    playListener.onPlaying();
                }
                while (!isFinish) {
                    if (fis.available() > 0) {
                        // 读取帧长度
                        fis.read(frameLength);
                        frameLen = CodecUtils.bytesToInt(frameLength);
                        readData = new byte[frameLen];
                        // 读取帧内容
                        fis.read(readData);
                        count++;
                        Log.i("NFL" , "读取的帧大小：" + frameLen);
                        onFrame(readData, 0, readData.length);
                        try {
                            // 由于每帧数据读取完毕立即丢给解码器显示，没有时间戳（PTS、DTS）控制解码显示
                            // 这里通过sleep做个简单的显示控制 1000/60 ≈ 16
                            Thread.sleep(1000L / fps);
                        } catch (Exception e) {
                        }
                    } else {
                        //文件读取结束
                        isFinish = true;
                    }
                }
                if (null != playListener){
                    Log.d("NFL" , "一共有 " + count + " 帧");
                    playListener.onFinished();
                }
                fis.close();
                fis = null;
                mDecoder.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void stopPlay(){
        isFinish = true;
        mDecoder.stop();
    }

    private static final String TAG = "AVCFileReader";

    /**
     * 将帧内容交给解析器解析
     * @param frame 这里传递的是真实的 h264 数据帧
     * @param offset
     * @param length
     */
    private void onFrame(byte[] frame, int offset, int length) {
        mDecoder.onFrame(frame, offset, length);
    }

    public interface PlayListener{
        void onReady();
        void onPlaying();
        void onFinished();
    }

    public void setPlayListener(PlayListener playListener) {
        this.playListener = playListener;
    }
}

