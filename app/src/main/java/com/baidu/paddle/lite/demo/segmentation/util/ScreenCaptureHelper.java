package com.baidu.paddle.lite.demo.segmentation.util;

import android.content.Context;
import android.content.Intent;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import java.io.File;

public class ScreenCaptureHelper {

    private static ScreenCaptureHelper screenCaptureHelper ;
    private Context context  ;
    private MediaProjectionManager mediaProjectionManager ;

    private MediaProjection mediaProjection ;
    private ScreenCapture screenCapture ;
    private Surface videoSurface ;
    private ScreenCapture.OnCaptureVideoCallback onCaptureVideoCallback ;
    private ScreenCapture.OnImageAvailableListener onImageAvailableListener ;
    private int height = 0 ;
    private int width = 0 ;

    public static ScreenCaptureHelper getInstance(){
        if (null == screenCaptureHelper){
            screenCaptureHelper = new ScreenCaptureHelper() ;
        }
        return screenCaptureHelper ;
    }

    /**
     * 用于 {@link com.baidu.paddle.lite.demo.segmentation.util.ScreenCapture.SurfaceType#MEDIA_RECORDER}
     * 情形下的视频输出目录
     * @return
     */
    public String getOutputFilePath(){
        File file ;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) , "mc_video.mp4") ;
        }else {
            file = new File("sdcard/mc_video.mp4") ;
        }
        return file.getAbsolutePath();
    }

    private ScreenCaptureHelper(){}

    public ScreenCaptureHelper init(Context context , int width , int height){
        if (this.context != null){
            throw new RuntimeException("ScreenCaptureHelper 已经初始化了") ;
        }
        this.context = context ;
        this.width = width ;
        this.height = height ;
        mediaProjectionManager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        return screenCaptureHelper;
    }

    public MediaProjectionManager getMediaProjectionManager() {
        return mediaProjectionManager;
    }

    public void setVideoSurface(Surface videoSurface) {
        this.videoSurface = videoSurface;
    }

    /**
     * 当 {@link ScreenCapture#getSurfaceType()} 为
     * {@link com.baidu.paddle.lite.demo.segmentation.util.ScreenCapture.SurfaceType#MEDIA_CODEC} 时才有效
     * @param onCaptureVideoCallback
     */
    public void setOnCaptureVideoCallback(ScreenCapture.OnCaptureVideoCallback onCaptureVideoCallback) {
        this.onCaptureVideoCallback = onCaptureVideoCallback;
    }

    /**
     * 当 {@link ScreenCapture#getSurfaceType()} 为
     * {@link com.baidu.paddle.lite.demo.segmentation.util.ScreenCapture.SurfaceType#IMAGE_READER} 时才有效
     * @param onImageAvailableListener
     */
    public void setOnImageAvailableListener(ScreenCapture.OnImageAvailableListener onImageAvailableListener) {
        this.onImageAvailableListener = onImageAvailableListener;
    }

    public ScreenCapture getScreenCapture() {
        return screenCapture;
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public void startCapture(int resultCode , Intent data){
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data) ;
        if (mediaProjection == null) {
            Log.e("NFL", "获取屏幕失败！");
            return;
        }
        screenCapture = new ScreenCapture(width, height, mediaProjection) ;
        // 使用自己的 surface 会将捕捉的屏幕显示出来
        screenCapture.setVideoSurface(videoSurface);
        screenCapture.setOnCaptureVideoCallback(onCaptureVideoCallback);
        screenCapture.setOnImageAvailableListener(onImageAvailableListener);
        screenCapture.startCapture();
    }

    public void stopCapture(){
        screenCapture.stopCapture();
    }
}
