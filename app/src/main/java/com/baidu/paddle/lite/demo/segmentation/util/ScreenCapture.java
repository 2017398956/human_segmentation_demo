package com.baidu.paddle.lite.demo.segmentation.util;

import android.graphics.ImageFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;

import cc.rome753.yuvtools.ImageBytes;
import cc.rome753.yuvtools.YUVTools;

/**
 * 处理捕捉的屏幕视频流有三种方式，都是将生成的 {@link Surface} 传递给
 * {@link MediaProjection#createVirtualDisplay(String, int, int, int, int, Surface, VirtualDisplay.Callback, Handler)}.
 * 1. {@link MediaCodec#createInputSurface()}
 * 2. {@link ImageReader#getSurface()}
 * 3. {@link MediaRecorder#getSurface()}
 */
public class ScreenCapture {

    private static final String TAG = "ScreenCapture";
    private int width, height;
    private SurfaceType surfaceType = SurfaceType.MEDIA_CODEC;
    private MediaRecorder mediaRecorder = null;
    private MediaCodec mediaCodec;
    private MediaFormat mediaFormat ;
    private MediaProjection mediaProjection;
    private OnCaptureVideoCallback onCaptureVideoCallback;
    private OnImageAvailableListener onImageAvailableListener;
    private VirtualDisplay mVirtualDisplay;

    public enum SurfaceType {
        /**
         * MEDIA_CODEC : 会生成自定义格式的 h264 ，当然也可以把自定义格式去掉，对应 onCaptureVideoCallback
         * IMAGE_READER : 目前只是展示图像的处理功能，对应 onImageAvailableListener
         * MEDIA_RECORDER : 将屏幕共享流录制成 mp4 的格式，系统提供的方法不需要额外处理
         */
        MEDIA_CODEC, IMAGE_READER, MEDIA_RECORDER
    }

    private ScreenCapture() {
    }

    private boolean isCapturing = false;
    private Thread mCaptureThread;
    // 用于展示录制 video 的 surface
    private Surface videoSurface;
    // 用于提供给 mediaProjection 以便于获取视频数据流
    private Surface surface;

    public ScreenCapture(int width, int height, MediaProjection mediaProjection) {
        this.width = width;
        this.height = height;
        this.mediaProjection = mediaProjection;
        mediaFormat = getDefaultMediaFormat(width, height);
    }

    public void startCapture() {
        if (isCapturing) {
            Log.w("NFL", "startCapture ignore 1");
            return;
        }
        Log.i("NFL", "startCapture");
        isCapturing = true;
        initEncoder();
    }

    /**
     * 用于 {@link #surfaceType} = {@link SurfaceType#MEDIA_CODEC} 时
     */
    public static MediaFormat getDefaultMediaFormat(int width, int height) {
        MediaFormat mediaFormat =
                MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        Log.d("NFL" , "width:" + width + "  height:" + height) ;
        // 设置视频输入颜色格式，这里选择使用Surface作为输入，可以忽略颜色格式的问题，并且不需要直接操作输入缓冲区。
        mediaFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        );
        // 这里指定 YUV
//        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
//                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
//        if (isColorFormatSupported(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
//                mEncoder.getCodecInfo().getCapabilitiesForType(mediaFormatType))) {
//
//        }
        // 码率
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height);
        // 帧率
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        // I帧间隔
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
        return mediaFormat ;
    }

    private void initEncoder() {
        switch (surfaceType) {
            case MEDIA_CODEC:
                try {
                    Log.d(TAG, "选则的 mime 类型为：" + mediaFormat.getString(MediaFormat.KEY_MIME));
                    mediaCodec = MediaCodec.createEncoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));
                } catch (IOException e) {
                    Log.e("NFL" , e.getLocalizedMessage()) ;
                    e.printStackTrace();
                    throw new RuntimeException("不支持 " + mediaFormat.getString(MediaFormat.KEY_MIME) + " 格式");
                }
                boolean renderOnSurfaceView = false ;
                mediaCodec.configure(mediaFormat, renderOnSurfaceView ? videoSurface : null, null,
                        MediaCodec.CONFIGURE_FLAG_ENCODE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // mediaCodec.setOutputSurface(videoSurface);
                }
                surface = mediaCodec.createInputSurface();
                break;
            case IMAGE_READER:
                ImageReader imageReader = ImageReader.newInstance(width, height, ImageFormat.YV12, 2);
                imageReader.setOnImageAvailableListener(reader -> {
                    if (null != onImageAvailableListener) {
                        onImageAvailableListener.onImage(reader.acquireNextImage());
                    }
                }, null);
                surface = imageReader.getSurface();
                break;
            case MEDIA_RECORDER:
                mediaRecorder = createMediaRecorder();
                surface = mediaRecorder.getSurface();
                break;
        }
        mVirtualDisplay = mediaProjection.createVirtualDisplay(
                "-display", width, height, 1,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, surface,
                null, null
        );
        switch (surfaceType) {
            case MEDIA_CODEC:
                encodeAsync();
                break;
            case IMAGE_READER:

                break;
            case MEDIA_RECORDER:
                mediaRecorder.start();
                break;
        }
    }

    private MediaRecorder createMediaRecorder() {
        MediaRecorder mediaRecorder = new MediaRecorder();
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(ScreenCaptureHelper.getInstance().getRecorderMp4VideoPath());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoSize(width, height);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoEncodingBitRate(1000);
        try {
            mediaRecorder.prepare();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mediaRecorder;
    }


    /**
     * Android5.0之后异步获取
     */
    private void encodeAsync() {
        mCaptureThread = new Thread(() -> {
            mediaCodec.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                    Log.i(TAG, "onInputBufferAvailable");
                    Image image = codec.getInputImage(index);
                    Log.d(TAG, "onInputBufferAvailable image:" + image + " and index:" + index);
                    if (null != image) {
                        ImageBytes imageBytes = YUVTools.getBytesFromImage(image);
                        Log.i("NFL", imageBytes.width + ":" + imageBytes.height);
                    }
                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                    // 这里运行在主线程
                    //Log.i(TAG,"onOutputBufferAvailable");
                    MediaFormat format = codec.getOutputFormat();
                    // 这里的 image2 总是 null
                    Image image2 = codec.getOutputImage(index) ;
                    Log.d(TAG, "onOutputBufferAvailable image2:" + image2 + " and index:" + index);
                    Log.i(TAG, "这帧的大小：" + info.size);
                    if (false) {
                        codec.releaseOutputBuffer(index, true);
                        return;
                    }

                    boolean useImage = false;
                    // 不抛弃非关键帧
                    boolean doNotAbandonNoKeyFrame = true ;
                    if (info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME || doNotAbandonNoKeyFrame) {
                        if (useImage) {
                            Image image = codec.getOutputImage(index);
                            if (image == null) {
                                Log.w("NFL", "不应该出现！");
                            } else {
                                Log.i("NFL", "帧的格式：" + image.getFormat());
                                ImageBytes imageBytes = YUVTools.getBytesFromImage(image);
                                if (null != onCaptureVideoCallback) {
                                    onCaptureVideoCallback.onCaptureVideo(imageBytes.bytes, imageBytes.width, imageBytes.height);
                                }
                            }
                        } else {
                            ByteBuffer buffer = codec.getOutputBuffer(index);
                            // h264 数据
                            byte[] bytes = new byte[info.size];
                            buffer.get(bytes);
                            if (null != onCaptureVideoCallback) {
                                onCaptureVideoCallback.onCaptureVideo(bytes, width, height);
                            } else {
                                Log.w("NFL", "不应该出现！");
                            }
                        }
                    }
                    codec.releaseOutputBuffer(index, false);
                }

                @Override
                public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                    Log.e(TAG, "MediaCodec error:" + e.getLocalizedMessage());
                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                    Log.i(TAG, "MediaCodec onOutputFormatChanged to:" + format);
                }
            });
            mediaCodec.start();
        });
        mCaptureThread.start();
    }

    /**
     * Android5.0之后同步获取
     */
    private void encodeSync() {
        mCaptureThread = new Thread(() -> {
            mediaCodec.start();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            while (isCapturing && mediaCodec != null) {
                try {
                    int index = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                    if (index >= 0) {
                        // Log.i(TAG, "dequeueOutputBuffer " + outId);
                        ByteBuffer buffer = mediaCodec.getOutputBuffer(index);
                        byte[] bytes = new byte[bufferInfo.size];
                        buffer.get(bytes);
                        if (null != onCaptureVideoCallback) {
                            onCaptureVideoCallback.onCaptureVideo(bytes, width, height);
                        } else {
                            Log.w("NFL", "不应该出现！");
                        }
                        mediaCodec.releaseOutputBuffer(index, false);
                    }
                } catch (Exception e) {
                    Log.w("NFL", e);
                }
            }
        });
        mCaptureThread.start();
    }

    /**
     * 当 {@link #surfaceType} = {@link SurfaceType#MEDIA_CODEC} 时才有效
     */
    public interface OnCaptureVideoCallback {
        void onCaptureVideo(byte[] bytes, int width, int height);
    }

    /**
     * 当 {@link #surfaceType} = {@link SurfaceType#IMAGE_READER} 时才有效
     */
    public interface OnImageAvailableListener {
        void onImage(Image image);
    }

    public void setOnCaptureVideoCallback(OnCaptureVideoCallback onCaptureVideoCallback) {
        this.onCaptureVideoCallback = onCaptureVideoCallback;
    }

    public void setOnImageAvailableListener(OnImageAvailableListener onImageAvailableListener) {
        this.onImageAvailableListener = onImageAvailableListener;
    }

    public void stopCapture() {
        Log.i("NFL", "stopCapture");
        isCapturing = false;
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        if (mediaCodec != null) {
            mediaCodec.release();
//            mediaCodec.stop();
            mediaCodec = null;
        }
        if (mediaRecorder != null){
            mediaRecorder.release();
//            mediaRecorder.stop();
            mediaRecorder = null ;
        }
    }

    public Surface getVideoSurface() {
        return videoSurface;
    }

    public void setVideoSurface(Surface videoSurface) {
        this.videoSurface = videoSurface;
    }

    public MediaFormat getMediaFormat() {
        return mediaFormat;
    }

    public String getMediaFormatType() {
        return mediaFormat.getString(MediaFormat.KEY_MIME);
    }

    public SurfaceType getSurfaceType() {
        return surfaceType;
    }

    public void setSurfaceType(SurfaceType surfaceType) {
        this.surfaceType = surfaceType;
    }

    private boolean isColorFormatSupported(int colorFormat, MediaCodecInfo.CodecCapabilities caps) {
        for (int c : caps.colorFormats) {
            if (c == colorFormat) {
                return true;
            }
        }
        Log.w("NFL", "不支持 CodecCapabilities");
        return false;
    }
}
