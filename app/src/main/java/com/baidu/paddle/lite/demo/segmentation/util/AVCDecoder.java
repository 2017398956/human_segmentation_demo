package com.baidu.paddle.lite.demo.segmentation.util;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Base64;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.ImageView;

import com.baidu.paddle.lite.demo.segmentation.BuildConfig;
import com.baidu.paddle.lite.demo.segmentation.beans.VideoFrame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

public class AVCDecoder {
    private String TAG = "AVCDecoder";
    private MediaCodec mediaCodec;
    private MediaFormat mediaFormat;
    private String mediaFormatType;
    private int width;
    private int height;
    private SurfaceView mSurfaceView;
    private LinkedList<VideoFrame> mFrameList = new LinkedList<>();

    public ImageView imageView;

    public final static int DECODE_ASYNC = 0;
    public final static int DECODE_SYNC = 1;
    public final static int DECODE_SYNC_DEPRECATED = 2;
    // 选择解码的工作方式
    private int mDecodeType = DECODE_ASYNC;
    private LinkedList<Integer> mInputIndexList = new LinkedList<>();

    private AVCDecoder() {
    }

    public AVCDecoder(SurfaceView surfaceView, String mediaFormatType, int width, int height, MediaFormat mediaFormat) {
        mSurfaceView = surfaceView;
        this.mediaFormatType = mediaFormatType;
        this.mediaFormat = mediaFormat;
        this.width = width;
        this.height = height;
        initDecoder();
    }

    public static AVCDecoder createFromScreenCaptureHelper(ScreenCaptureHelper screenCaptureHelper, SurfaceView surfaceView) {
        String mediaFormatType = MediaFormat.MIMETYPE_VIDEO_AVC;
        if (screenCaptureHelper.getScreenCapture() != null) {
            mediaFormatType = screenCaptureHelper.getScreenCapture().getMediaFormatType();
        }
        MediaFormat mediaFormat = ScreenCapture.getDefaultMediaFormat(
                screenCaptureHelper.getWidth(),
                screenCaptureHelper.getHeight()
        );
        if (screenCaptureHelper.getScreenCapture().getMediaFormat() == null) {
            mediaFormat = screenCaptureHelper.getScreenCapture().getMediaFormat();
        }
        return new AVCDecoder(surfaceView, mediaFormatType, screenCaptureHelper.getWidth(), screenCaptureHelper.getHeight(), mediaFormat);
    }

    public MediaFormat getMediaFormat() {
        return mediaFormat;
    }

    private void initDecoder() {
        try {
            mediaCodec = MediaCodec.createDecoderByType(mediaFormatType);
            if (mDecodeType == DECODE_ASYNC) {
                mediaCodec.setCallback(new MediaCodec.Callback() {
                    @Override
                    public void onInputBufferAvailable(MediaCodec codec, int index) {
                        Log.d("NFL", "onInputBufferAvailable:AVCDecoder " + index);
                        mInputIndexList.add(index);
                    }

                    @Override
                    public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                        codec.releaseOutputBuffer(index, true);
                    }

                    @Override
                    public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                        Log.i(TAG, "onError");
                    }

                    @Override
                    public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                        Log.i(TAG, "onOutputFormatChanged");
                    }
                });
                queueInputBuffer();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void queueInputBuffer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    if (mFrameList.isEmpty() || mInputIndexList.isEmpty()) {
                        continue;
                    }
                    VideoFrame frame = mFrameList.poll();
                    Integer index = mInputIndexList.poll();
                    ByteBuffer inputBuffer = mediaCodec.getInputBuffer(index);
                    inputBuffer.clear();
                    inputBuffer.put(frame.buf, frame.offset, frame.length);
                    Log.d("NFL", "prepare for AVCDecoder's inputBuffer:" + index);
                    mediaCodec.queueInputBuffer(index, 0, frame.length - frame.offset, 0, 0);
                }
            }
        }).start();
    }

    /**
     * 提供给 {@link AVCFileReader} 用于处理接收的数据
     *
     * @param buf    这里传递的是真实的 h264 数据帧
     * @param offset FIXME：应该为 0 ，因为下面没处理 buf 的偏移量
     * @param length
     */
    public void onFrame(byte[] buf, int offset, int length) {
        // 首帧是SPS PPS，需要设置给解码器，才能工作
        if (CodecUtils.getFrameType(buf) == CodecUtils.NAL_SPS) {
            // 数据格式：sps+pps，所以 pps 之前的都是 sps 的数据
            // sps 和 pps 的数据都要包括 0x00 0x00 0x00 0x01
            int ppsPosition = CodecUtils.getPPSPosition(buf);
            if (ppsPosition > 0) {
                // 获取 sps 和 pps 的数据
                byte[] sps = new byte[ppsPosition];
                System.arraycopy(buf, 0, sps, 0, sps.length);
                byte[] pps = new byte[buf.length - sps.length];
                System.arraycopy(buf, ppsPosition, pps, 0, pps.length);
                if (BuildConfig.DEBUG) {
                    // 将 sps 00 00 00 01 后面的内容转成 base64 以供 spsparser.exe 解析
                    byte[] spsTemp = new byte[sps.length - 4];
                    System.arraycopy(sps, 4, spsTemp, 0, spsTemp.length);
                    Log.d("NFL", "sps:" + Base64.encodeToString(spsTemp, Base64.NO_WRAP));
                    // 将 pps 00 00 00 01 后面的内容转成 base64 以供 spsparser.exe 解析
                    byte[] ppsTemp = new byte[pps.length - 4];
                    System.arraycopy(pps, 4, ppsTemp, 0, ppsTemp.length);
                    Log.d("NFL", "pps:" + Base64.encodeToString(ppsTemp, Base64.NO_WRAP));
                }
                // 设置解码器参数
                mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
                mediaFormat.setByteBuffer("csd-1", ByteBuffer.wrap(pps));
                mediaCodec.configure(mediaFormat, mSurfaceView.getHolder().getSurface(),
                        null, 0);
                mediaCodec.start();
            }
        }else {
            if (imageView != null){
                // 视频帧
                if (!"1".equals(imageView.getTag())){
                    // TODO h264 需要先转换成 YUV
                    // imageView.setImageBitmap(YUVTools.nv12ToBitmap(buf , width , height));
                    imageView.setTag("1");
                }
            }
        }

        switch (mDecodeType) {
            case DECODE_ASYNC:
                decodeAsync(buf, offset, length);
                break;
            case DECODE_SYNC:
                decodeSync(buf, offset, length);
                break;
            case DECODE_SYNC_DEPRECATED:
                decodeDeprecated(buf, offset, length);
                break;
        }
    }

    public void stop() {
        mediaCodec.stop();
        mediaCodec.release();
    }

    private void decodeAsync(byte[] buf, int offset, int length) {
        VideoFrame frame = new VideoFrame();
        frame.buf = buf;
        frame.offset = offset;
        frame.length = length;
        mFrameList.add(frame);
    }

    private void decodeSync(byte[] buf, int offset, int length) {
        int inputBufferIndex = mediaCodec.dequeueInputBuffer(100);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
            inputBuffer.clear();
            inputBuffer.put(buf, offset, length);
            mediaCodec.queueInputBuffer(inputBufferIndex, 0, length, System.currentTimeMillis(), 0);
        } else {
            return;
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 100);
        while (outputBufferIndex >= 0) {
            mediaCodec.releaseOutputBuffer(outputBufferIndex, true);
            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        }
    }

    private void decodeDeprecated(byte[] buf, int offset, int length) {
        ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
        int inputBufferIndex = mediaCodec.dequeueInputBuffer(100);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(buf, offset, length);
            mediaCodec.queueInputBuffer(inputBufferIndex, 0, length, System.currentTimeMillis(), 0);
        } else {
            return;
        }
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 100);
        while (outputBufferIndex >= 0) {
            mediaCodec.releaseOutputBuffer(outputBufferIndex, true);
            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        }
    }

}

