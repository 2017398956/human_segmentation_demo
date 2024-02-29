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
import com.baidu.paddle.lite.demo.segmentation.util.H264AndH265DeCodePlay
import com.baidu.paddle.lite.demo.segmentation.util.inflate

class VideoPlayerActivity : AppCompatActivity() {

    private val h264VideoPath = "/sdcard/Download/h264.h264"
    private val h265VideoPath = "/sdcard/Download/h265.h265"
    private val h264VideoPath2 = "/sdcard/Download/1.h264"
    private val h265VideoPath2 = "/sdcard/Download/1.h265"

    private val binding by inflate<ActivityVideoPlayerBinding>()
    private var h264AndH265DeCodePlay: H264AndH265DeCodePlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        binding.sfvVideo.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.d(TAG, "surfaceCreated")
                binding.btnH264.setOnClickListener {
                    binding.sfvVideo.layoutParams.let {
                        it.width = 1080
                        it.height = 1920
                        binding.sfvVideo.layoutParams = it
                    }
                    h264AndH265DeCodePlay = H264AndH265DeCodePlay(
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
                    h264AndH265DeCodePlay = H264AndH265DeCodePlay(
                        h265VideoPath,
                        holder.surface,
                        MediaFormat.MIMETYPE_VIDEO_HEVC,
                        1080,
                        1920,
                        30
                    )
                    h264AndH265DeCodePlay!!.decodePlay()
                }
                binding.btnH2642.setOnClickListener {
                    binding.sfvVideo.layoutParams.let {
                        it.width = (640 * 1.5).toInt()
                        it.height = (320 * 1.5).toInt()
                        binding.sfvVideo.layoutParams = it
                    }
                    h264AndH265DeCodePlay = H264AndH265DeCodePlay(
                        h264VideoPath2,
                        holder.surface,
                        MediaFormat.MIMETYPE_VIDEO_HEVC,
                        640,
                        320,
                        30
                    )
                    h264AndH265DeCodePlay!!.decodePlay()
                }
                binding.btnH2652.setOnClickListener {
                    binding.sfvVideo.layoutParams.let {
                        it.width = (640 * 1.5).toInt()
                        it.height = (320 * 1.5).toInt()
                        binding.sfvVideo.layoutParams = it
                    }
                    h264AndH265DeCodePlay = H264AndH265DeCodePlay(
                        h265VideoPath2,
                        holder.surface,
                        MediaFormat.MIMETYPE_VIDEO_HEVC,
                        640,
                        320,
                        30
                    )
                    h264AndH265DeCodePlay!!.decodePlay()
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
    }

    override fun onDestroy() {
        h264AndH265DeCodePlay?.release()
        super.onDestroy()
    }

    companion object {
        const val TAG = "VideoPlayerActivity"
    }
}