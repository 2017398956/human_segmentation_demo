package com.baidu.paddle.lite.demo.segmentation.activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.baidu.paddle.lite.demo.segmentation.R
import com.baidu.paddle.lite.demo.segmentation.databinding.ActivityLauncherBinding
import com.baidu.paddle.lite.demo.segmentation.util.inflate

class LauncherActivity : AppCompatActivity() {

    private val binding by inflate<ActivityLauncherBinding>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.btnCaptureScreen.setOnClickListener {
            startActivity(Intent(this, GetScreenActivity::class.java))
        }
        binding.btnHumanSeg.setOnClickListener {
            startActivity(Intent(this, HumanSegActivity::class.java))
        }
        binding.btnBaiduHumanSeg.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.btnPlayer.setOnClickListener {
            startActivity(Intent(this, VideoPlayerActivity::class.java))
        }
    }
}