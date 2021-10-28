package com.baidu.paddle.lite.demo.segmentation.activity

import android.content.Intent
import android.media.MediaPlayer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import cc.rome753.yuvtools.YUVTools
import com.baidu.paddle.lite.demo.segmentation.databinding.ActivityGetScreenBinding
import com.baidu.paddle.lite.demo.segmentation.util.AVCDecoder
import com.baidu.paddle.lite.demo.segmentation.util.AVCFileReader
import com.baidu.paddle.lite.demo.segmentation.util.CodecUtils
import com.baidu.paddle.lite.demo.segmentation.util.ScreenCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.*
import java.util.*

/**
 * 用于录屏并播放
 */
class GetScreenActivity : AppCompatActivity() {

    private val REQUEST_CODE = 1000
    private var mMediaProjectionManager: MediaProjectionManager? = null
    private var mMediaProjection: MediaProjection? = null
    private var mScreenCapture: ScreenCapture? = null
    private var height = 0
    private var width = 0
    private var avcFileReader:AVCFileReader? = null
    private var binding: ActivityGetScreenBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGetScreenBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        initData()
        setListeners()
    }

    private fun initData(){
        val dm = resources.displayMetrics
        width = dm.widthPixels
        height = dm.heightPixels
    }

    private fun setListeners(){
        binding?.btnRecordOrStop!!.setOnClickListener {
            if ("开始录制" == binding!!.btnRecordOrStop.text){
                requestScreenCapture()
            }else{
                stopWriteVideo()
            }
        }
        binding!!.btnPlay.setOnClickListener {
            if (binding!!.btnPlay.text == "开始播放"){
                if ("停止录制" == binding!!.btnRecordOrStop.text){
                    Toast.makeText(this@GetScreenActivity , "录制中，请结束录制后播放" , Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                binding!!.btnPlay.text = "停止播放"
                if (null == mScreenCapture){
                    Toast.makeText(this@GetScreenActivity , "请先录制" , Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val useMediaPlayer = false
                if (useMediaPlayer){
                    play()
                }else{
                    val avcDecoder = AVCDecoder(binding!!.sv , mScreenCapture?.mediaFormatType,
                        width ,height , mScreenCapture?.mediaFormat)
                    avcDecoder.imageView = binding!!.ivDisplay
                    avcFileReader = AVCFileReader(videoPath, avcDecoder)
                    avcFileReader?.setPlayListener(object : AVCFileReader.PlayListener{
                        override fun onReady() {

                        }

                        override fun onPlaying() {
                        }

                        override fun onFinished() {
                            GlobalScope.launch(Dispatchers.Main) {
                                binding!!.btnPlay.text = "开始播放"
                            }
                        }

                    })
                    avcFileReader?.start()
                }
            }else{
                binding!!.btnPlay.text = "开始播放"
                avcFileReader?.stopPlay()
            }
        }
    }

    private fun requestScreenCapture() {
        mMediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mMediaProjectionManager!!.createScreenCaptureIntent()
        startActivityForResult(captureIntent, REQUEST_CODE)
    }

    private fun play(){
        val mediaPlayer = MediaPlayer();
        val playAssetVideo = false
        if (playAssetVideo){
            val afd = assets.openFd("test.mp4")
            mediaPlayer.setDataSource(afd.fileDescriptor , afd.startOffset , afd.length)
        }else{
//            val videoFile = File(videoPath)
//            val inputStream = FileInputStream(videoFile)
//            mediaPlayer.setDataSource(inputStream.fd , 0 , videoFile.length())
            val afd = assets.openFd("mc_video.h264")
            mediaPlayer.setDataSource(afd.fileDescriptor , afd.startOffset , afd.length)
        }
        mediaPlayer.setDisplay(binding!!.sv.holder)
        mediaPlayer.prepareAsync()
        mediaPlayer.setOnPreparedListener {
            it.start()
            it.seekTo(0)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK || requestCode != REQUEST_CODE) return
        mMediaProjection = mMediaProjectionManager!!.getMediaProjection(resultCode, data!!)
        if (mMediaProjection == null) {
            Log.e("NFL", "获取屏幕失败！");
            return
        }

        mScreenCapture = ScreenCapture(width, height, mMediaProjection)
        // 使用自己的 surface 会将捕捉的屏幕显示出来
        mScreenCapture?.videoSurface = binding!!.sv.holder.surface
        var count = 0
        mScreenCapture!!.setOnCaptureVideoCallback { bytes, width, height ->
//            val bitmap = BitmapFactory.decodeByteArray(bytes , 0 , bytes.size)
//            val bitmap = YUVTools.nv12ToBitmap(bytes , width , height)
//            binding!!.ivDisplay.setImageBitmap(bitmap)
            Log.i("NFL", "刷新 view")
            count++
            val addFrameLengthMark = true
            var newBytes:ByteArray? = null
            if (addFrameLengthMark){
                // 帧数据前面都插入了4个字节记录当前帧的长度
                newBytes = ByteArray(bytes.size + 4)
                val head: ByteArray = CodecUtils.intToBytes(bytes.size)
                System.arraycopy(head, 0, newBytes, 0, 4)
                System.arraycopy(bytes, 0, newBytes, head.size, bytes.size)
            }else{
                newBytes = ByteArray(bytes.size)
                System.arraycopy(bytes, 0, newBytes, 0, bytes.size)
            }

//            val temp = newBytes.copyOfRange(4 , newBytes.size)
//            if (count == 1) {
//                Log.i("NFL", "sps:" + Base64.encodeToString(byteArrayOf(0x01 , 0x02) , Base64.DEFAULT));
//                // SPS 帧
//                Log.i("NFL", "sps:" + Base64.encodeToString(temp , Base64.DEFAULT));
//                Log.i("NFL", "sps:" + Base64.encodeToString(temp , Base64.CRLF));
//                Log.i("NFL", "sps:" + Base64.encodeToString(temp , Base64.NO_CLOSE));
//                Log.i("NFL", "sps:" + Base64.encodeToString(temp , Base64.NO_PADDING));
//                Log.i("NFL", "sps:" + Base64.encodeToString(temp , Base64.NO_WRAP));
//                Log.i("NFL", "sps:" + Base64.encodeToString(temp , Base64.URL_SAFE));
//            }else if (count == 2){
//                // PPS 帧
//                Log.i("NFL", "pps:" + Base64.encodeToString(temp , Base64.DEFAULT));
//                binding!!.ivDisplay.setImageBitmap(YUVTools.nv12ToBitmap(temp , 720 , 1280))
//            }
            if (newBytes != null) {
                Log.i("NFL" , "校验帧的大小：" + (newBytes.size - 4))
                writeVideo(newBytes)
            }
        }
        binding!!.btnRecordOrStop.text = "停止录制"
        mScreenCapture!!.startCapture()
    }

    private val videoPath = "sdcard/mc_video.h264"
    private var mVideoStream: OutputStream? = null

    private fun writeVideo(bytes: ByteArray) {
        if (mVideoStream == null) {
            val videoFile = File(videoPath);
            if (videoFile.exists()) {
                videoFile.delete()
            }
            try {
                videoFile.createNewFile();
                mVideoStream = FileOutputStream(videoFile);
            } catch (e: Exception) {
                Log.e("NFL", e.toString())
            }
        }
        try {
            mVideoStream?.write(bytes);
        } catch (e: Exception) {
            Log.e("NFL", e.toString())
        }
    }

    private fun stopWriteVideo() {
        mScreenCapture?.stopCapture()
        try {
            mVideoStream?.flush()
            mVideoStream?.close()
        } catch (e: Exception) {
            Log.e("NFL", e.toString())
        }
        mVideoStream = null
        binding?.btnRecordOrStop?.text = "开始录制"
    }

}