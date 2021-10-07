package com.baidu.paddle.lite.demo.segmentation.activity

import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import cc.rome753.yuvtools.YUVTools
import com.baidu.paddle.lite.demo.segmentation.databinding.ActivityGetScreenBinding
import com.baidu.paddle.lite.demo.segmentation.util.AVCDecoder
import com.baidu.paddle.lite.demo.segmentation.util.AVCFileReader
import com.baidu.paddle.lite.demo.segmentation.util.ScreenCapture
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.*


class GetScreenActivity : AppCompatActivity() {

    private val REQUEST_CODE = 1000
    private var mMediaProjectionManager: MediaProjectionManager? = null
    private var mMediaProjection: MediaProjection? = null
    private var mScreenCapture: ScreenCapture? = null
    private var binding: ActivityGetScreenBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGetScreenBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        requestScreenCapture()
        binding?.btnStop!!.setOnClickListener {
            stopWriteVideo()
        }
        binding!!.btnPlay.setOnClickListener {
            val avcDecoder = AVCDecoder(binding!!.sv)
            val avcFileReader = AVCFileReader()
            avcFileReader.setDecoder(avcDecoder)
            avcFileReader.start()
        }
    }

    private fun requestScreenCapture() {
        mMediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mMediaProjectionManager!!.createScreenCaptureIntent()
        startActivityForResult(captureIntent, REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK || requestCode != REQUEST_CODE) return
        mMediaProjection = mMediaProjectionManager!!.getMediaProjection(resultCode, data)
        if (mMediaProjection == null) {
            Log.e("NFL", "获取屏幕失败！");
            return
        }
        val dm = resources.displayMetrics
        val width = dm.widthPixels
        val height = dm.heightPixels
        mScreenCapture = ScreenCapture(width, height, mMediaProjection)
        // 使用自己的 surface 会将捕捉的屏幕显示出来
//        mScreenCapture?.surface = binding!!.sv.holder.surface
        var count = 0
        mScreenCapture!!.setOnCaptureVideoCallback { bytes, width, height ->
//            val bitmap = BitmapFactory.decodeByteArray(bytes , 0 , bytes.size)
//            val bitmap = YUVTools.nv12ToBitmap(bytes , width , height)
//            binding!!.ivDisplay.setImageBitmap(bitmap)
            Log.i("NFL", "刷新 view")

            count++
            // 帧数据前面都插入了4个字节记录当前帧的长度
//            val newBytes = ByteArray(bytes.size + 4)
//            val head: ByteArray = CodecUtils.intToBytes(bytes.size)
//            System.arraycopy(head, 0, newBytes, 0, 4)
//            System.arraycopy(bytes, 0, newBytes, head.size, bytes.size)

            val newBytes = ByteArray(bytes.size)
            System.arraycopy(bytes, 0, newBytes, 0, bytes.size)

            val temp = newBytes.copyOfRange(4 , newBytes.size)
            if (count == 1) {
                Log.i("NFL", "sps:" + Base64.encodeToString(byteArrayOf(0x01 , 0x02) , Base64.DEFAULT));
                // SPS 帧
                Log.i("NFL", "sps:" + Base64.encodeToString(temp , Base64.DEFAULT));
                Log.i("NFL", "sps:" + Base64.encodeToString(temp , Base64.CRLF));
                Log.i("NFL", "sps:" + Base64.encodeToString(temp , Base64.NO_CLOSE));
                Log.i("NFL", "sps:" + Base64.encodeToString(temp , Base64.NO_PADDING));
                Log.i("NFL", "sps:" + Base64.encodeToString(temp , Base64.NO_WRAP));
                Log.i("NFL", "sps:" + Base64.encodeToString(temp , Base64.URL_SAFE));
            }else if (count == 2){
                // PPS 帧
                Log.i("NFL", "pps:" + Base64.encodeToString(temp , Base64.DEFAULT));
                binding!!.ivDisplay.setImageBitmap(YUVTools.nv12ToBitmap(temp , 720 , 1280))
            }
            writeVideo(newBytes)
        }
        mScreenCapture!!.startCapture()
    }

    private val videoPath = "sdcard/mc_video.h264"
    private var mVideoStream: OutputStream? = null

    private fun writeVideo(bytes: ByteArray) {
        //Log.i(TAG, "writeVideo");
        if (mVideoStream == null) {
            val videoFile = File(videoPath);
            if (videoFile.exists()) {
                videoFile.delete()
            }
            try {
                videoFile.createNewFile();
                mVideoStream = FileOutputStream(videoFile);
            } catch (e: Exception) {
                Log.w("NFL", e);
            }
        }
        try {
            mVideoStream?.write(bytes);
        } catch (e: Exception) {
            Log.w("NFL", e);
        }
    }

    private fun stopWriteVideo() {
        try {
            mVideoStream?.flush();
            mVideoStream?.close();
        } catch (e: Exception) {
            Log.w("NFL", e);
        }
        mVideoStream = null;
    }

}