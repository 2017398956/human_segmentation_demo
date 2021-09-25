package com.baidu.paddle.lite.demo.segmentation.activity

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import cc.rome753.yuvtools.YUVTools
import com.baidu.paddle.lite.demo.segmentation.databinding.ActivitySecondBinding
import com.baidu.paddle.lite.demo.segmentation.util.SegmentationUtil
import com.baidu.paddle.lite.demo.segmentation.visual.ReplaceBackgroundVisualize
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class SecondActivity : AppCompatActivity(), CameraXConfig.Provider {
    private lateinit var binding: ActivitySecondBinding
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private val imageAnalysisExecutor = Executors.newSingleThreadExecutor()
    private val replaceBackgroundVisualize = ReplaceBackgroundVisualize()
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val testExecutor = Executors.newFixedThreadPool(100)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecondBinding.inflate(layoutInflater)
        setContentView(binding.root)
//        initSegmentation()
        initCamera()
    }

    private fun initSegmentation() {
        SegmentationUtil.instance.init(this)
        GlobalScope.launch(Dispatchers.IO) {
            if (SegmentationUtil.instance.loadModel()) {
                replaceBackgroundVisualize.setBackgroundImage(SegmentationUtil.instance.backgroundImage)
                replaceBackgroundVisualize.setScaledImage(SegmentationUtil.instance.scaledImage)
                if (SegmentationUtil.instance.runModel(replaceBackgroundVisualize)) {
                    GlobalScope.launch(Dispatchers.Main) {
                        binding.ivShow.setImageBitmap(SegmentationUtil.instance.segmentationBitmap)
                    }
                }
                val startTime = System.currentTimeMillis()
                for (i in 0 until 100) {
                    testExecutor.execute {
                        val image =
                            BitmapFactory.decodeStream(assets.open("image_segmentation/images/human${i % 4 + 1}.jpg"))
                        SegmentationUtil.instance.refreshInputBitmap(image)
                        replaceBackgroundVisualize.setBackgroundImage(SegmentationUtil.instance.backgroundImage)
                        replaceBackgroundVisualize.setScaledImage(SegmentationUtil.instance.scaledImage)
                        if (SegmentationUtil.instance.runModel(replaceBackgroundVisualize)) {
                            GlobalScope.launch(Dispatchers.Main) {
                                binding.ivShow.setImageBitmap(SegmentationUtil.instance.segmentationBitmap)
                                if (i == 99) {
                                    Toast.makeText(
                                        this@SecondActivity,
                                        "帧率：${100 * 1000 / (System.currentTimeMillis() - startTime)}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun initCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        var preview: Preview = Preview.Builder().build()
        var cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()
        preview.setSurfaceProvider(binding.previewView.surfaceProvider)
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER)
            .build()

        imageAnalysis.setAnalyzer(imageAnalysisExecutor, object : ImageAnalysis.Analyzer {
            override fun analyze(image: ImageProxy) {
                val rotationDegrees = image.imageInfo.rotationDegrees
                // insert your code here.
                Log.i("NFL", "================ ${Thread.currentThread().name}")
                val bitmap = YUVTools.nv12ToBitmap(
                    YUVTools.getBytesFromImage(image).bytes,
                    image.width,
                    image.height
                )
                GlobalScope.launch(Dispatchers.Main) {
                    binding.ivShow.setImageBitmap(bitmap)
                }
                image.close()
                if (true) {
                    return
                }
                cameraExecutor.execute {
                    if (SegmentationUtil.instance.loadModel()) {
                        SegmentationUtil.instance.refreshInputBitmap(bitmap)
                        replaceBackgroundVisualize.setBackgroundImage(SegmentationUtil.instance.backgroundImage)
                        replaceBackgroundVisualize.setScaledImage(SegmentationUtil.instance.scaledImage)
                        if (SegmentationUtil.instance.runModel(replaceBackgroundVisualize)) {
                            GlobalScope.launch(Dispatchers.Main) {
                                binding.ivShow.setImageBitmap(SegmentationUtil.instance.segmentationBitmap)
                            }
                        }
                    }
                }
            }

        })

        cameraProvider.unbindAll()
        var camera = cameraProvider.bindToLifecycle(
            this,
            cameraSelector,
            imageAnalysis,
            preview
        )
    }

    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }
}