package com.baidu.paddle.lite.demo.segmentation.util;

import android.util.Log;

import java.util.Arrays;

public class CodecUtils {
    // 7(sps)或者8(pps), 及data[4] & 0x1f == 7 || data[4] & 0x1f == 8
    public static int NAL_SPS = 7;
    public static int NAL_PPS = 8 ;

    /**
     * 处理 4 位长的 byte
     * @param bytes
     * @return
     */
    public static int bytesToInt(byte[] bytes){
        int value = 0;
        for(int i = 0; i < 4; i++) {
            int shift= (3-i) * 8;
            value +=(bytes[i] & 0xFF) << shift;
        }
        return value;
    }

    /**
     * 处理 2 位长的 byte
     * @param bt
     * @return
     */
    private static int bytes2Int(byte[] bt) {
        int ret = bt[0];
        ret <<= 8;
        ret |= bt[1];
        return ret;
    }

    public static byte[] intToBytes(int i) {
        byte[] result = new byte[4];
        result[0] = (byte)((i >> 24) & 0xFF);
        result[1] = (byte)((i >> 16) & 0xFF);
        result[2] = (byte)((i >> 8) & 0xFF);
        result[3] = (byte)(i & 0xFF);
        return result;
    }

    public static int getFrameType(byte[] buf) {
        return buf[4] & 0x1f;
    }

    public static void getSPSAndPPS(byte[] frame){
        // 'a'=0x61, 'v'=0x76, 'c'=0x63, 'C'=0x43
        byte[] avcC = new byte[] { 0x61, 0x76, 0x63, 0x43 };
        // avcC的起始位置
        int avcRecord = 0;
        for (int i = 0; i < frame.length; ++i) {
            if (frame[i] == avcC[0] && frame[i + 1] == avcC[1]
                    && frame[i + 2] == avcC[2]
                    && frame[i + 3] == avcC[3]) {
                // 找到avcC，则记录 avcRecord 起始位置，然后退出循环。
                avcRecord = i + 4;
                break;
            }
        }
        if (0 == avcRecord) {
            Log.e("NFL" ,"没有找到avcC，请检查文件格式是否正确");
            return;
        }

        // 加 6 的目的是为了跳过
        // (1)1字节的 configurationVersion
        // (2)1字节的 AVCProfileIndication
        // (3)1字节的 profile_compatibility
        // (4)1 字节的 AVCLevelIndication
        // (5)6 bit 的 reserved
        // (6)2 bit 的 lengthSizeMinusOne
        // (7)3 bit 的 reserved
        // (8)5 bit 的numOfSequenceParameterSets
        // 共 6 个字节，然后到达 sequenceParameterSetLength 的位置
        int spsStartPos = avcRecord + 6;
        // 获取表示 sps 长度的字节
        byte[] spsByte = new byte[] {frame[spsStartPos],
                frame[spsStartPos + 1]};
        int spsLength = bytes2Int(spsByte);
        byte[] sps = new byte[spsLength];
        // 跳过 2 个字节的 sequenceParameterSetLength
        spsStartPos += 2;
        System.arraycopy(frame, spsStartPos, sps, 0, spsLength);
        printResult("SPS", sps, spsLength);

        // 底下部分为获取 PPS
        // spsStartPos + spsLength 可以跳到 pps位置
        // 再加 1 的目的是跳过 1 字节的 numOfPictureParameterSets
        int ppsStartPos = spsStartPos + spsLength + 1;
        // 获取表示 pps 长度的字节
        byte[] ppsByte = new byte[] { frame[ppsStartPos],
                frame[ppsStartPos + 1] };
        int ppsLength = bytes2Int(ppsByte);
        byte[] PPS = new byte[ppsLength];
        ppsStartPos += 2;
        System.arraycopy(frame, ppsStartPos, PPS, 0, ppsLength);
        printResult("PPS", PPS, ppsLength);
    }

    private static void printResult(String type, byte[] bt, int len) {
        Log.i("NFL" , type + "长度为：" + len + ",内容为：" + Arrays.toString(bt)) ;
        for (int ix = 0; ix < len; ++ix) {
            System.out.printf("%02x ", bt[ix]);
        }
        System.out.println("\n----------");
    }
}
