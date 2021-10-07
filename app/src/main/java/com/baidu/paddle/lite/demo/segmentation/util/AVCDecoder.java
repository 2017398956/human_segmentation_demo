package com.baidu.paddle.lite.demo.segmentation.util;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.SurfaceView;

import com.baidu.paddle.lite.demo.segmentation.beans.VideoFrame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;

public class AVCDecoder {
    private String TAG = "MC_AVCDecoder";
    private MediaCodec mCodec;
    private MediaFormat mCodecFormat;
    private final static String MIME_TYPE = "video/avc";
    private final static int VIDEO_WIDTH = 720;
    private final static int VIDEO_HEIGHT = 1280;
    private SurfaceView mSurfaceView;
    private LinkedList<VideoFrame> mFrameList = new LinkedList<>();

    public final static int DECODE_ASYNC = 0;
    public final static int DECODE_SYNC = 1;
    public final static int DECODE_SYNC_DEPRECATED = 2;
    private int mDecodeType = 0;
    private LinkedList<Integer> mInputIndexList = new LinkedList<>();

    public AVCDecoder(SurfaceView surfaceView) {
        mSurfaceView = surfaceView;
        initDecoder();
    }

    public void initDecoder() {
        try {
            mCodec = MediaCodec.createDecoderByType(MIME_TYPE);
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, 0, 0);
            mCodecFormat = format;

            if (mDecodeType == DECODE_ASYNC) {
                mCodec.setCallback(new MediaCodec.Callback() {
                    @Override
                    public void onInputBufferAvailable(MediaCodec codec, int index) {
                        //Log.i(TAG, "onInputBufferAvailable " + Thread.currentThread().getName());
                        mInputIndexList.add(index);
                    }

                    @Override
                    public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                        //Log.i(TAG, "onOutputBufferAvailable");
                        mCodec.releaseOutputBuffer(index, true);
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
                    ByteBuffer inputBuffer = mCodec.getInputBuffer(index);
                    inputBuffer.clear();
                    inputBuffer.put(frame.buf, frame.offset, frame.length);
                    // Log.i(TAG, "queueInputBuffer " + frame.offset + "/" + frame.length);
                    mCodec.queueInputBuffer(index, 0, frame.length - frame.offset, 0, 0);
                }
            }
        }).start();
    }

    public void onFrame(byte[] buf, int offset, int length) {
        // 首帧是SPS PPS，需要设置给解码器，才能工作
        if (CodecUtils.getFrameType(buf) == CodecUtils.NAL_SPS) {
            int ppsPosition = getPPSPosition(buf);
            if (ppsPosition > 0) {
                byte[] sps = new byte[ppsPosition];
                System.arraycopy(buf, 0, sps, 0, sps.length);
                byte[] pps = new byte[buf.length - sps.length];
                System.arraycopy(buf, ppsPosition, pps, 0, pps.length);
                Log.i("NFL" , "sps:" + Arrays.toString(sps) + " pps:" + Arrays.toString(pps)) ;
                mCodecFormat.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
                mCodecFormat.setByteBuffer("csd-1", ByteBuffer.wrap(pps));
                mCodec.configure(mCodecFormat, mSurfaceView.getHolder().getSurface(),
                        null, 0);
                mCodec.start();
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

    private int getPPSPosition(byte[] buf) {
        return 0;
    }

    private void decodeAsync(byte[] buf, int offset, int length) {
        VideoFrame frame = new VideoFrame();
        frame.buf = buf;
        frame.offset = offset;
        frame.length = length;
        mFrameList.add(frame);
    }

    private void decodeSync(byte[] buf, int offset, int length) {

        int inputBufferIndex = mCodec.dequeueInputBuffer(100);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = mCodec.getInputBuffer(inputBufferIndex);
            inputBuffer.clear();
            inputBuffer.put(buf, offset, length);
            mCodec.queueInputBuffer(inputBufferIndex, 0, length, System.currentTimeMillis(), 0);
        } else {
            return;
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 100);
        while (outputBufferIndex >= 0) {
            mCodec.releaseOutputBuffer(outputBufferIndex, true);
            outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 0);
        }
    }

    private void decodeDeprecated(byte[] buf, int offset, int length) {

        ByteBuffer[] inputBuffers = mCodec.getInputBuffers();
        int inputBufferIndex = mCodec.dequeueInputBuffer(100);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(buf, offset, length);
            mCodec.queueInputBuffer(inputBufferIndex, 0, length, System.currentTimeMillis(), 0);
        } else {
            return;
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 100);
        while (outputBufferIndex >= 0) {
            mCodec.releaseOutputBuffer(outputBufferIndex, true);
            outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 0);
        }
    }


}

