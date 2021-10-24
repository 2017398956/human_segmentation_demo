//package com.baidu.paddle.lite.demo.segmentation.activity;
//// android H264硬解码为YUV
//// 音视频方面的视频流大多是h264编码的，现在要把这些视频流通过android硬编码解码为YUV。
//// 解码时注意以及几点：
//// 1，视频文件的分辨率
//// 2 ，设置的颜色格式要正确。  int format = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
////  mMF.setInteger(MediaFormat.KEY_COLOR_FORMAT, format);
//// 3，解码输出时，bytebuffer大小够用，不然输出空间不够用会有异常。
//// 4，要把surface设置为null，如果不设置为null，数据就显示到界面上了，dequeueOutputBuffer 就输出不来有效的data了。
//
//import android.media.MediaCodec;
//import android.media.MediaCodecInfo;
//import android.media.MediaFormat;
//import android.view.Surface;
//
//import java.io.IOException;
//
//public class Test {
//    private final String MIME_TYPE = "video/avc";
//    //要解码的为YUV420
//    private final int decodeColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
//
//    private MediaCodec mMC = MediaCodec.createDecoderByType(MIME_TYPE);
//    private MediaFormat mMF ;
//
//    public Test() throws IOException {
//    }
//
//    public void configure(Surface surface){
//        int[] width = new int[1];
//        int[] height = new int[1];
//        width[0]=1280;//视频源的实际宽高
//        height[0]=960;
//        mMF = MediaFormat.createVideoFormat(MIME_TYPE, width[0], height[0]);
//        mMF.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width[0] * height[0]);
//        mMF.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
//        if (isColorFormatSupported(decodeColorFormat, mMC.getCodecInfo().getCapabilitiesForType(MIME_TYPE))) {
//            mMF.setInteger(MediaFormat.KEY_COLOR_FORMAT, decodeColorFormat);
//        }
//        mMC.configure(mMF, null, null, 0);//这里的surface是null
//    }
//
//    private boolean isColorFormatSupported(int colorFormat, MediaCodecInfo.CodecCapabilities caps) {
//        for (int c : caps.colorFormats) {
//            if (c == colorFormat) {
//                return true;
//            }
//        }
//        return false;
//    }
//
//    public int output(/*out*/byte[] data,/* out */int[] len,/* out */long[] ts){
//        int i = mMC.dequeueOutputBuffer(mBI, BUFFER_TIMEOUT);
//        if(i >= 0){
//            if((data != null)&&(mBI.size > data.length)) return BUFFER_TOO_SMALL;
//            outputBuffers[i].position(mBI.offset);
//            outputBuffers[i].limit(mBI.offset + mBI.size);
//            if (data != null)
//                outputBuffers[i].get(data, mBI.offset, mBI.size);
//            len[0] = mBI.size ;
//            ts[0] = mBI.presentationTimeUs;
//
//            mMC.releaseOutputBuffer(i, false);
//        } else if (i == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
//            outputBuffers = mMC.getOutputBuffers();
//            return OUTPUT_UPDATE;
//        } else if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
//            mMF = mMC.getOutputFormat();
//            return OUTPUT_UPDATE;
//        } else if (i == MediaCodec.INFO_TRY_AGAIN_LATER) {
//            return TRY_AGAIN_LATER;
//        }
//        return BUFFER_OK;
//    }
//}
