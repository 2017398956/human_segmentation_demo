package com.baidu.paddle.lite.demo.segmentation.util;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.Log;
import android.view.Surface;

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

    public void setOnCaptureVideoCallback(ScreenCapture.OnCaptureVideoCallback onCaptureVideoCallback) {
        this.onCaptureVideoCallback = onCaptureVideoCallback;
    }

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
