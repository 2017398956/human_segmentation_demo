package com.baidu.paddle.lite.demo.segmentation.activity

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.media.Image
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import cc.rome753.yuvtools.ImageBytes
import cc.rome753.yuvtools.YUVTools
import com.baidu.paddle.lite.demo.segmentation.databinding.ActivityGetScreenBinding
import com.baidu.paddle.lite.demo.segmentation.service.CaptureScreenService
import com.baidu.paddle.lite.demo.segmentation.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * 用于录屏并播放
 */
class GetScreenActivity : AppCompatActivity() {

    private val REQUEST_CODE_SCREEN_CAPTURE = 1000
    private lateinit var screenCaptureHelper: ScreenCaptureHelper

    private val videoPath = "sdcard/mc_video.h264"
    private var mVideoStream: OutputStream? = null
    private var avcFileReader: AVCFileReader? = null

    private lateinit var binding: ActivityGetScreenBinding
    private var captureScreenService: Intent? = null

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGetScreenBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        initData()
        setListeners()
        requestPermissions(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ), 1001
        )
    }

    private fun initData() {
        val dm = resources.displayMetrics
        // windowManager.defaultDisplay.getRealMetrics(dm)
        screenCaptureHelper =
            ScreenCaptureHelper.getInstance().init(this, dm.widthPixels, dm.heightPixels)
    }

    private fun setListeners() {
        binding?.btnRecordOrStop!!.setOnClickListener {
            if ("开始录制" == binding!!.btnRecordOrStop.text) {
                startActivityForResult(
                    screenCaptureHelper.mediaProjectionManager.createScreenCaptureIntent(),
                    REQUEST_CODE_SCREEN_CAPTURE
                )
            } else {
                stopWriteVideo()
            }
        }
        binding!!.btnPlay.setOnClickListener {
            if (binding!!.btnPlay.text == "开始播放") {
                if ("停止录制" == binding!!.btnRecordOrStop.text) {
                    Toast.makeText(this@GetScreenActivity , "录制中，请结束录制后播放" , Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                binding!!.btnPlay.text = "停止播放"
                if (!File(videoPath).exists()) {
                    Toast.makeText(this@GetScreenActivity, "请先录制", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val useMediaPlayer = true
                if (useMediaPlayer){
                    play()
                }else {
                    val avcDecoder = AVCDecoder.createFromScreenCaptureHelper(screenCaptureHelper , binding.sv)
                    avcDecoder.imageView = binding!!.ivDisplay
                    avcFileReader = AVCFileReader(videoPath, avcDecoder)
                    avcFileReader?.setPlayListener(object : AVCFileReader.PlayListener {
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

    private fun play() {
        val mediaPlayer = MediaPlayer();
        var playType = 0
        when (playType) {
            0 -> {
                val videoFile = File(ScreenCaptureHelper.getInstance().outputFilePath)
                val inputStream = FileInputStream(videoFile)
                mediaPlayer.setDataSource(inputStream.fd, 0, videoFile.length())
            }
            1 -> {
                val afd = assets.openFd("test.mp4")
                mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            2 -> {
                val afd = assets.openFd("mc_video.h264")
                mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
        }
        mediaPlayer.setDisplay(binding!!.sv.holder)
        mediaPlayer.prepareAsync()
        mediaPlayer.setOnPreparedListener {
            it.start()
            it.seekTo(0)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || requestCode != REQUEST_CODE_SCREEN_CAPTURE) return
        screenCaptureHelper.setVideoSurface(binding.sv.holder.surface)
        screenCaptureHelper.setOnCaptureVideoCallback(object :
            ScreenCapture.OnCaptureVideoCallback {
            var count = 0
            override fun onCaptureVideo(bytes: ByteArray, width: Int, height: Int) {
                // 这里的数据是 h264 格式的不能把 bytes 直接转换成 bitmap
                count++
                /**
                 *  true : h264 格式已被修改，需要用 AVCDecoder 解码播放
                 *  false :  h264 视频文件
                 */
                val addFrameLengthMark = true
                var newBytes: ByteArray? = null
                if (addFrameLengthMark) {
                    // 帧数据前面都插入了4个字节记录当前帧的长度
                    newBytes = ByteArray(bytes.size + AVCFileReader.FRAME_LENGTH)
                    val head: ByteArray = CodecUtils.intToBytes(bytes.size)
                    System.arraycopy(head, 0, newBytes, 0, AVCFileReader.FRAME_LENGTH)
                    System.arraycopy(bytes, 0, newBytes, head.size, bytes.size)
                } else {
                    newBytes = ByteArray(bytes.size)
                    System.arraycopy(bytes, 0, newBytes, 0, bytes.size)
                }
                if (newBytes != null) {
                    Log.i("NFL", "校验帧的大小：" + (newBytes.size - AVCFileReader.FRAME_LENGTH))
                    writeVideo(newBytes)
                }
            }
        })
        screenCaptureHelper.setOnImageAvailableListener(object :
            ScreenCapture.OnImageAvailableListener {
            var imageBytes: ImageBytes? = null
            var bitmap:Bitmap? = null
            var hasScreenSnapshot = false
            override fun onImage(image: Image?) {
                if (image == null) return
                Log.d("NFL", "setOnImageAvailableListener")
                imageBytes = YUVTools.getBytesFromImage(image)
                bitmap = YUVTools.yv12ToBitmap(imageBytes!!.bytes,imageBytes!!.width, imageBytes!!.height)
                if (!hasScreenSnapshot){
                    ImageUtil.onlySaveBitmap(this@GetScreenActivity,bitmap,"test_screen_snapshot")
                    hasScreenSnapshot = true
                }
                binding?.ivDisplay?.setImageBitmap(bitmap)
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            captureScreenService = Intent(this, CaptureScreenService::class.java)
            captureScreenService!!.putExtra(CaptureScreenService.CAPTURE_SCREEN_DATA, data)
            startForegroundService(captureScreenService)
        } else {
            screenCaptureHelper.startCapture(resultCode, data!!)
        }
        binding!!.btnRecordOrStop.text = "停止录制"
    }

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
            mVideoStream?.flush();
        } catch (e: Exception) {
            Log.e("NFL", e.toString())
        }
    }

    private fun stopWriteVideo() {
        screenCaptureHelper.stopCapture()
        try {
            mVideoStream?.close()
        } catch (e: Exception) {
            Log.e("NFL", e.toString())
        }
        mVideoStream = null
        binding?.btnRecordOrStop?.text = "开始录制"
    }

}