package com.baidu.paddle.lite.demo.segmentation.activity

import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.baidu.paddle.lite.demo.segmentation.databinding.ActivityVideoPlayerBinding
import com.baidu.paddle.lite.demo.segmentation.util.MediaCodec.AudioPlayer
import com.baidu.paddle.lite.demo.segmentation.util.MediaCodec.H264AndH265DeCodePlay
import com.baidu.paddle.lite.demo.segmentation.util.MediaCodec.VideoPlayer
import com.baidu.paddle.lite.demo.segmentation.util.inflate

class VideoPlayerActivity : AppCompatActivity() {

    private val h264VideoPath = "/sdcard/Download/h264.h264"
    private val h265VideoPath = "/sdcard/Download/h265.h265"

    //    // 普通大文件
//    private val h264VideoPath2 = "/sdcard/Download/1.h264"
//    private val h265VideoPath2 = "/sdcard/Download/1.h265"
    // 1G 大文件
    private val h264VideoPath2 = "/sdcard/Download/2.h264"
    private val h265VideoPath2 = "/sdcard/Download/2.h265"

    private val mp4File = "/sdcard/Download/test.mp4"

    private val binding by inflate<ActivityVideoPlayerBinding>()
    private var h264AndH265DeCodePlay: H264AndH265DeCodePlay? = null
    private var audioPlayer: AudioPlayer? = null
    private var videoPlay: VideoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        printCodecInfo()
        binding.sfvVideo.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.d(TAG, "surfaceCreated")
                binding.btnH264.setOnClickListener {
                    binding.sfvVideo.layoutParams.let {
                        it.width = 1080
                        it.height = 1920
                        binding.sfvVideo.layoutParams = it
                    }
                    h264AndH265DeCodePlay =
                        H264AndH265DeCodePlay(
                            h264VideoPath,
                            holder.surface,
                            MediaFormat.MIMETYPE_VIDEO_AVC,
                            1080,
                            1920,
                            30
                        )
                    h264AndH265DeCodePlay!!.decodePlay()
                }
                binding.btnH265.setOnClickListener {
                    binding.sfvVideo.layoutParams.let {
                        it.width = 1080
                        it.height = 1920
                        binding.sfvVideo.layoutParams = it
                    }
                    h264AndH265DeCodePlay =
                        H264AndH265DeCodePlay(
                            h265VideoPath,
                            holder.surface,
                            MediaFormat.MIMETYPE_VIDEO_HEVC,
                            1080,
                            1920,
                            30
                        )
                    h264AndH265DeCodePlay!!.decodePlay()
                }
                // 1.h26x
//                val bigVideoFileWidth = 640
//                val bigVideoFileHeight = 320
//                val videoViewWidth = (bigVideoFileWidth * 1.5).toInt()
//                val videoViewHeight = (bigVideoFileHeight * 1.5).toInt()

                // 2.h26x
                val bigVideoFileWidth = 1278
                val bigVideoFileHeight = 720
                val videoViewWidth = (bigVideoFileWidth * 1.0).toInt()
                val videoViewHeight = (bigVideoFileHeight * 1.0).toInt()
                binding.btnH2642.setOnClickListener {
                    binding.sfvVideo.layoutParams.let {
                        it.width = videoViewWidth
                        it.height = videoViewHeight
                        binding.sfvVideo.layoutParams = it
                    }
                    h264AndH265DeCodePlay =
                        H264AndH265DeCodePlay(
                            h264VideoPath2,
                            holder.surface,
                            MediaFormat.MIMETYPE_VIDEO_AVC,
                            bigVideoFileWidth,
                            bigVideoFileHeight,
                            30
                        )
                    h264AndH265DeCodePlay!!.decodePlay()
                }
                binding.btnH2652.setOnClickListener {
                    binding.sfvVideo.layoutParams.let {
                        it.width = videoViewWidth
                        it.height = videoViewHeight
                        binding.sfvVideo.layoutParams = it
                    }
                    h264AndH265DeCodePlay =
                        H264AndH265DeCodePlay(
                            h265VideoPath2,
                            holder.surface,
                            MediaFormat.MIMETYPE_VIDEO_HEVC,
                            bigVideoFileWidth,
                            bigVideoFileHeight,
                            30
                        )
                    h264AndH265DeCodePlay!!.decodePlay()
                }

                binding.btnMP4.setOnClickListener {
                    binding.sfvVideo.layoutParams.let {
                        it.width = (640 * 1.5).toInt()
                        it.height = (360 * 1.5).toInt()
                        binding.sfvVideo.layoutParams = it
                    }
                    videoPlay =
                        VideoPlayer(
                            mp4File,
                            holder.surface,
                            MediaFormat.MIMETYPE_VIDEO_AVC,
                            640,
                            360,
                            30
                        )
                    videoPlay!!.decodePlay()
                    binding.btnAudio.performClick()
                }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
            }

        })
        binding.btnAudio.setOnClickListener {
            audioPlayer = AudioPlayer()
            audioPlayer!!.decodeAudio(mp4File)
        }
    }

    override fun onDestroy() {
        h264AndH265DeCodePlay?.release()
        audioPlayer?.onDestroy()
        videoPlay?.release()
        super.onDestroy()
    }

    companion object {
        const val TAG = "VideoPlayerActivity"
    }

    private fun printCodecInfo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val supportCodes = list.codecInfos
            Log.i(TAG, "解码器列表：")
            for (codec in supportCodes) {
                if (!codec.isEncoder) {
                    val name = codec.name
                    if (name.startsWith("OMX.google")) {
                        Log.i(TAG, "软解->$name")
                        if (name == "OMX.google.hevc.decoder") {
                            binding.btnH265.visibility = View.VISIBLE
                            binding.btnH265.text = "软解播放 H265"
                        } else if (name == "OMX.google.h264.decoder") {
                            binding.btnH264.visibility = View.VISIBLE
                            binding.btnH264.text = "软解播放 H264"
                        }
                    }
                }
            }
            for (codec in supportCodes) {
                if (!codec.isEncoder) {
                    val name = codec.name
                    if (!name.startsWith("OMX.google")) {
                        Log.i(TAG, "硬解->$name")
                        if (name == "OMX.MTK.VIDEO.DECODER.HEVC") {
                            binding.btnH265.visibility = View.VISIBLE
                            binding.btnH265.text = "硬解播放 H265"
                        } else if (name == "OMX.MTK.VIDEO.DECODER.AVC") {
                            binding.btnH264.visibility = View.VISIBLE
                            binding.btnH264.text = "硬解播放 H264"
                        }
                    }
                }
            }
            Log.i(TAG, "编码器列表：")
            for (codec in supportCodes) {
                if (codec.isEncoder) {
                    val name = codec.name
                    if (name.startsWith("OMX.google")) {
                        Log.i(TAG, "软编->$name")
                    }
                }
            }
            for (codec in supportCodes) {
                if (codec.isEncoder) {
                    val name = codec.name
                    if (!name.startsWith("OMX.google")) {
                        Log.i(TAG, "硬编->$name")
                    }
                }
            }
        }
    }
}