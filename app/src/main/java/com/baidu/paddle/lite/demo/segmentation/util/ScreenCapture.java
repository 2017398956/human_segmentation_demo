package com.baidu.paddle.lite.demo.segmentation.util;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

import cc.rome753.yuvtools.ImageBytes;
import cc.rome753.yuvtools.YUVTools;

public class ScreenCapture {

    private int width, height;
    private MediaCodec mEncoder;
    private MediaProjection mediaProjection;
    private OnCaptureVideoCallback onCaptureVideoCallback;
    private VirtualDisplay mVirtualDisplay;

    private ScreenCapture() {
    }

    ;
    private boolean isCapturing = false;
    private Thread mCaptureThread;
    private Surface surface;

    public ScreenCapture(int width, int height, MediaProjection mediaProjection) {
        this.width = width;
        this.height = height;
        this.mediaProjection = mediaProjection;
    }

    public void startCapture() {
        if (isCapturing) {
            Log.w("NFL", "startCapture ignore 1");
            return;
        }
        initEncoder();
        if (mEncoder == null) {
            Log.w("NFL", "startCapture ignore 2");
            return;
        }
        Log.i("NFL", "startCapture");
        isCapturing = true;
        encodeAsync();
    }

    private void initEncoder() {
        MediaFormat mediaFormat =
                MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        // 设置视频输入颜色格式，这里选择使用Surface作为输入，可以忽略颜色格式的问题，并且不需要直接操作输入缓冲区。
        mediaFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        );
        // 这里指定 YUV
//        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
//                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        // 码率
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height);
        // 帧率
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
        // I帧间隔
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
        try {
            mEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//            if (surface == null){
//                surface = mEncoder.createInputSurface();
//            }else {
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                    mEncoder.setOutputSurface(surface);
//                }
//            }
            surface = mEncoder.createInputSurface();

            mVirtualDisplay = mediaProjection.createVirtualDisplay(
                    "-display", width, height, 1,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, surface, null, null
            );
        } catch (Exception e) {
            Log.w("NFL", e);
        }
    }

    /**
     * Android5.0之后异步获取
     */
    private void encodeAsync() {
        mCaptureThread = new Thread(() -> {
            mEncoder.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                    Log.i("NFL", "onInputBufferAvailable");
                    Image image = codec.getInputImage(index);
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
                    Image image2 = codec.getOutputImage(index) ;
                    Log.i("NFL", "MediaCodec:" + Thread.currentThread().getName());
                    Log.i("NFL", "这帧的大小：" + info.size);
                    if (false) {
                        codec.releaseOutputBuffer(index, true);
                        return;
                    }

                    boolean useImage = true;
                    if (info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME || true) {
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
                    Log.i("NFL", "onError");
                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                    Log.i("NFL", "onOutputFormatChanged");
                }
            });
            mEncoder.start();
        });
        mCaptureThread.start();
    }

    /**
     * Android5.0之后同步获取
     */
    private void encodeSync() {
        mCaptureThread = new Thread(new Runnable() {
            @Override
            public void run() {
                mEncoder.start();
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                while (isCapturing && mEncoder != null) {
                    try {
                        int index = mEncoder.dequeueOutputBuffer(bufferInfo, 0);
                        if (index >= 0) {
                            // Log.i(TAG, "dequeueOutputBuffer " + outId);
                            ByteBuffer buffer = mEncoder.getOutputBuffer(index);
                            byte[] bytes = new byte[bufferInfo.size];
                            buffer.get(bytes);
                            if (null != onCaptureVideoCallback) {
                                onCaptureVideoCallback.onCaptureVideo(bytes, width, height);
                            } else {
                                Log.w("NFL", "不应该出现！");
                            }
                            mEncoder.releaseOutputBuffer(index, false);
                        }
                    } catch (Exception e) {
                        Log.w("NFL", e);
                    }
                }
            }
        });
        mCaptureThread.start();
    }


    public interface OnCaptureVideoCallback {
        void onCaptureVideo(byte[] bytes, int width, int height);
    }

    public void setOnCaptureVideoCallback(OnCaptureVideoCallback onCaptureVideoCallback) {
        this.onCaptureVideoCallback = onCaptureVideoCallback;
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
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
    }

    public void setSurface(Surface surface) {
        this.surface = surface;
    }

    public Surface getSurface() {
        return surface;
    }
}
