package com.baidu.paddle.lite.demo.segmentation.activity

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cc.rome753.yuvtools.ImageBytes
import cc.rome753.yuvtools.YUVTools
import com.baidu.paddle.lite.demo.segmentation.databinding.ActivityGetScreenBinding
import com.baidu.paddle.lite.demo.segmentation.service.CaptureScreenService
import com.baidu.paddle.lite.demo.segmentation.util.*
import com.baidu.paddle.lite.demo.segmentation.util.AVCFileReader.PlayListener
import com.baidu.paddle.lite.demo.segmentation.util.ScreenCapture.SurfaceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * 用于录屏并播放
 */
class GetScreenActivity : AppCompatActivity() {

    private val REQUEST_CODE_SCREEN_CAPTURE = 1000
    private lateinit var screenCaptureHelper: ScreenCaptureHelper

    private var mVideoStream: OutputStream? = null
    private var avcFileReader: AVCFileReader? = null

    private val binding by inflate<ActivityGetScreenBinding>()
    private var captureScreenService: Intent? = null

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
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
        binding.btnRecordOrStop.setOnClickListener {
            if (startRecordStr == binding.btnRecordOrStop.text) {
                startActivityForResult(
                    screenCaptureHelper.mediaProjectionManager.createScreenCaptureIntent(),
                    REQUEST_CODE_SCREEN_CAPTURE
                )
            } else {
                stopWriteVideo()
            }
        }
        binding.btnPlay.setOnClickListener {
            if (binding.btnPlay.text == startPlayStr) {
                if (stopRecordStr == binding.btnRecordOrStop.text) {
                    ToastUtil.showToast("录制中，请结束录制后播放")
                    return@setOnClickListener
                }
                if (ScreenCaptureHelper.getInstance().screenCapture == null) {
                    ToastUtil.showToast("请先录制")
                    return@setOnClickListener
                }
                binding.btnPlay.text = stopPlayStr
                when (ScreenCaptureHelper.getInstance().screenCapture.surfaceType) {
                    SurfaceType.MEDIA_RECORDER -> {
                        if (!File(ScreenCaptureHelper.getInstance().recorderMp4VideoPath).exists()) {
                            ToastUtil.showToast("请先录制")
                            return@setOnClickListener
                        }
                    }

                    SurfaceType.IMAGE_READER -> {
                        ToastUtil.showToast("图片集不需要播放")
                        return@setOnClickListener
                    }

                    SurfaceType.MEDIA_CODEC -> {
                        if (!File(ScreenCaptureHelper.getInstance().h264OutputFilePath).exists()) {
                            ToastUtil.showToast("请先录制")
                            return@setOnClickListener
                        }
                    }

                    else -> {

                    }
                }

                val useMediaPlayer = true
                if (useMediaPlayer) {
                    play(ScreenCaptureHelper.getInstance().screenCapture.surfaceType)
                } else {
                    val avcDecoder =
                        AVCDecoder.createFromScreenCaptureHelper(screenCaptureHelper, binding.sv)
                    avcDecoder.imageView = binding.ivDisplay
                    avcFileReader?.setPlayListener(object : PlayListener {
                        override fun onReady() {

                        }

                        override fun onPlaying() {
                        }

                        override fun onFinished() {
                            lifecycleScope.launch(Dispatchers.Main) {
                                binding.btnPlay.text = startPlayStr
                            }
                        }

                    })
                    avcFileReader?.start()
                }
            } else {
                binding.btnPlay.text = startPlayStr
                avcFileReader?.stopPlay()
            }
        }
    }

    private fun play(surfaceType: SurfaceType) {
        val mediaPlayer = MediaPlayer();
        when (surfaceType) {
            SurfaceType.IMAGE_READER -> {
                val videoFile = File(ScreenCaptureHelper.getInstance().recorderMp4VideoPath)
                val inputStream = FileInputStream(videoFile)
                mediaPlayer.setDataSource(inputStream.fd, 0, videoFile.length())
            }

            SurfaceType.MEDIA_RECORDER -> {
                // val afd = assets.openFd("test.mp4")
                // mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                mediaPlayer.setDataSource(ScreenCaptureHelper.getInstance().recorderMp4VideoPath)
            }

            SurfaceType.MEDIA_CODEC -> {
                if (addFrameLengthMarkWhenUseMediaCodec) {
                    val avcFileReader = AVCFileReader(
                        ScreenCaptureHelper.getInstance().h264OutputFilePath,
                        AVCDecoder.createFromScreenCaptureHelper(screenCaptureHelper, binding.sv)
                    )
                    avcFileReader.setPlayListener(object : PlayListener {
                        override fun onReady() {

                        }

                        override fun onPlaying() {
                        }

                        override fun onFinished() {
                            lifecycleScope.launch {
                                binding.btnPlay.text = startPlayStr
                            }
                        }

                    })
                    avcFileReader.start();
                    return
                } else {
                    // val afd = assets.openFd("mc_video.h264")
                    // mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    mediaPlayer.setDataSource(ScreenCaptureHelper.getInstance().h264OutputFilePath)
                }
            }
        }
        mediaPlayer.setDisplay(binding.sv.holder)
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
                var newBytes: ByteArray? = null
                if (addFrameLengthMarkWhenUseMediaCodec) {
                    // 帧数据前面都插入了 4 个字节记录当前帧的长度
                    newBytes = ByteArray(bytes.size + AVCFileReader.FRAME_LENGTH)
                    val head: ByteArray = CodecUtils.intToBytes(bytes.size)
                    System.arraycopy(head, 0, newBytes, 0, AVCFileReader.FRAME_LENGTH)
                    System.arraycopy(bytes, 0, newBytes, head.size, bytes.size)
                } else {
                    newBytes = ByteArray(bytes.size)
                    System.arraycopy(bytes, 0, newBytes, 0, bytes.size)
                }
                Log.i(TAG, "校验帧的大小：" + (newBytes.size - AVCFileReader.FRAME_LENGTH))
                writeVideo(newBytes)
            }
        })
        screenCaptureHelper.setOnImageAvailableListener(object :
            ScreenCapture.OnImageAvailableListener {
            var imageBytes: ImageBytes? = null
            var bitmap: Bitmap? = null
            var hasScreenSnapshot = false
            override fun onImage(image: Image?) {
                if (image == null || image.planes == null) return
                Log.d("NFL", "onImage:0x" + image.format.toString(16))
                imageBytes = YUVTools.getBytesFromImage(image)
                when (image.format) {
                    ImageFormat.YUV_420_888 -> {
                        bitmap = YUVTools.i420ToBitmap(
                            imageBytes!!.bytes,
                            imageBytes!!.width,
                            imageBytes!!.height
                        )
                    }
                }

                if (!hasScreenSnapshot) {
                    ImageUtil.onlySaveBitmap(this@GetScreenActivity, bitmap, "test_screen_snapshot")
                    hasScreenSnapshot = true
                }
                binding.ivDisplay.setImageBitmap(bitmap)
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            captureScreenService = Intent(this, CaptureScreenService::class.java)
            captureScreenService!!.putExtra(CaptureScreenService.CAPTURE_SCREEN_DATA, data)
            startForegroundService(captureScreenService)
        } else {
            screenCaptureHelper.startCapture(resultCode, data!!)
        }
        binding.btnRecordOrStop.text = stopRecordStr
    }

    private fun writeVideo(bytes: ByteArray) {
        if (mVideoStream == null) {
            val videoFile = File(ScreenCaptureHelper.getInstance().h264OutputFilePath);
            if (videoFile.exists()) {
                videoFile.delete()
            }
            try {
                videoFile.createNewFile();
                mVideoStream = FileOutputStream(videoFile);
            } catch (e: Exception) {
                Log.e(TAG, "writeVideo() failed: ${e.localizedMessage}")
            }
        }
        try {
            mVideoStream?.write(bytes);
            mVideoStream?.flush();
        } catch (e: Exception) {
            Log.e(TAG, "writeVideo() failed: ${e.localizedMessage}")
        }
    }

    private fun stopWriteVideo() {
        screenCaptureHelper.stopCapture()
        try {
            mVideoStream?.close()
        } catch (e: Exception) {
            Log.e(TAG, "stopWriteVideo() failed: ${e.localizedMessage}")
        }
        mVideoStream = null
        binding.btnRecordOrStop.text = startRecordStr
    }

    companion object {
        const val TAG = "GetScreenActivity"
        const val startRecordStr = "开始录制"
        const val startPlayStr = "开始播放"
        const val stopRecordStr = "停止录制"
        const val stopPlayStr = "停止播放"

        /**
         *  true : h264 格式已被修改，需要用
         *  @see AVCFileReader 解码播放
         *  false :  h264 视频文件，可以由专门的 h264 播放器播放
         */
        const val addFrameLengthMarkWhenUseMediaCodec = true
    }

}