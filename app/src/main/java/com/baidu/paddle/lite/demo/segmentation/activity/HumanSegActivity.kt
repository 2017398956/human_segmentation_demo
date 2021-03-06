package com.baidu.paddle.lite.demo.segmentation.activity

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import cc.rome753.yuvtools.YUVTools
import com.baidu.paddle.lite.demo.segmentation.databinding.ActivitySecondBinding
import com.baidu.paddle.lite.demo.segmentation.util.ImageUtil
import com.baidu.paddle.lite.demo.segmentation.util.SegmentationUtil
import com.baidu.paddle.lite.demo.segmentation.visual.ReplaceBackgroundVisualize
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class HumanSegActivity : AppCompatActivity(), CameraXConfig.Provider {
    private lateinit var binding: ActivitySecondBinding
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private val imageAnalysisExecutor = Executors.newSingleThreadExecutor()
    private val replaceBackgroundVisualize = ReplaceBackgroundVisualize()
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var surfaceHolder: SurfaceHolder? = null
    private lateinit var humanSegPaint: Paint
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecondBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initData()
        initSegmentation()
        initCamera()
    }

    private fun initData() {
        humanSegPaint = Paint(Paint.ANTI_ALIAS_FLAG);
        humanSegPaint.isFilterBitmap = true;
        humanSegPaint.isDither = true;
    }

    private fun initSegmentation() {
        SegmentationUtil.instance.init(this)
        GlobalScope.launch(Dispatchers.IO) {
            if (SegmentationUtil.instance.loadModel()) {
                replaceBackgroundVisualize.setBackgroundImage(SegmentationUtil.instance.backgroundImage)
                replaceBackgroundVisualize.setScaledImage(SegmentationUtil.instance.scaledImage)
                if (SegmentationUtil.instance.runModel(replaceBackgroundVisualize)) {
                    // ????????????????????????????????????????????????????????????
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
            .setTargetResolution(Size(192, 192))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(imageAnalysisExecutor) { image ->
            val rotationDegrees = image.imageInfo.rotationDegrees
            val width = image.width
            val height = image.height
            Log.i("NFL", "??????????????????${rotationDegrees},?????????x??????${width}x${height}")
            val src = YUVTools.getBytesFromImage(image).bytes
            val dest = ByteArray(src.size)
            YUVTools.rotateSP270(src, dest, width, height)
//                val bitmap = YUVTools.nv12ToBitmap(src, width, height)
            val bitmap = YUVTools.nv12ToBitmap(dest, width, height)
            image.close()
            // ????????????
            SegmentationUtil.instance.refreshInputBitmap(bitmap)
            replaceBackgroundVisualize.setBackgroundImage(SegmentationUtil.instance.backgroundImage)
            replaceBackgroundVisualize.setScaledImage(SegmentationUtil.instance.scaledImage)
            if (SegmentationUtil.instance.runModel(replaceBackgroundVisualize)) {
                if (null == surfaceHolder) {
                    surfaceHolder = binding.svHumanSeg.holder
                }
                val canvas = surfaceHolder!!.lockCanvas()
                val outputBitmap = SegmentationUtil.instance.segmentationBitmap
                Log.i("NFL", "?????????????????????x??????${outputBitmap.width}x${outputBitmap.height}")
                val scale = outputBitmap.height.toFloat() / binding.svHumanSeg.height
                val scaleBitmap = Bitmap.createScaledBitmap(
                    outputBitmap,
                    (outputBitmap.width * scale).toInt(),
                    binding.svHumanSeg.height,
                    true
                )
                val srcRect = Rect(0, 0, scaleBitmap.width, scaleBitmap.height)
                val destRect = Rect(
                    (binding.svHumanSeg.width - scaleBitmap.width) / 2,
                    0,
                    scaleBitmap.width,
                    scaleBitmap.height
                )
                canvas.drawBitmap(scaleBitmap, srcRect, destRect, humanSegPaint)
                surfaceHolder!!.unlockCanvasAndPost(canvas)
            }
        }

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