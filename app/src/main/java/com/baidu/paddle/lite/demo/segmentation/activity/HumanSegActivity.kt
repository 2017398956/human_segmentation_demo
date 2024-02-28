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
import androidx.lifecycle.lifecycleScope
import cc.rome753.yuvtools.YUVTools
import com.baidu.paddle.lite.demo.segmentation.databinding.ActivitySecondBinding
import com.baidu.paddle.lite.demo.segmentation.util.ImageUtil
import com.baidu.paddle.lite.demo.segmentation.util.SegmentationUtil
import com.baidu.paddle.lite.demo.segmentation.util.inflate
import com.baidu.paddle.lite.demo.segmentation.visual.ReplaceBackgroundVisualize
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class HumanSegActivity : AppCompatActivity(), CameraXConfig.Provider {
    private val binding by inflate<ActivitySecondBinding>()
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private val imageAnalysisExecutor = Executors.newSingleThreadExecutor()
    private val replaceBackgroundVisualize = ReplaceBackgroundVisualize()
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var surfaceHolder: SurfaceHolder? = null
    private lateinit var humanSegPaint: Paint
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        lifecycleScope.launch(Dispatchers.IO) {
            if (SegmentationUtil.instance.loadModel()) {
                replaceBackgroundVisualize.setBackgroundImage(SegmentationUtil.instance.backgroundImage)
                replaceBackgroundVisualize.setScaledImage(SegmentationUtil.instance.scaledImage)
                if (SegmentationUtil.instance.runModel(replaceBackgroundVisualize)) {
                    // 这里只是准备一下环境，方便后面的刷新操作
                }
            }
        }
    }

    private fun initCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview: Preview = Preview.Builder().build()
        val cameraSelector: CameraSelector = CameraSelector.Builder()
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
            Log.d(TAG, "旋转的度数：${rotationDegrees},图像宽x高：${width}x${height}")
            val src = YUVTools.getBytesFromImage(image).bytes
            val dest = ByteArray(src.size)
            YUVTools.rotateSP270(src, dest, width, height)
            // val bitmap = YUVTools.nv12ToBitmap(src, width, height)
            val bitmap = YUVTools.nv12ToBitmap(dest, width, height)
            image.close()
            // 刷新界面
            SegmentationUtil.instance.refreshInputBitmap(bitmap)
            replaceBackgroundVisualize.setBackgroundImage(SegmentationUtil.instance.backgroundImage)
            replaceBackgroundVisualize.setScaledImage(SegmentationUtil.instance.scaledImage)
            if (SegmentationUtil.instance.runModel(replaceBackgroundVisualize)) {
                if (null == surfaceHolder) {
                    surfaceHolder = binding.svHumanSeg.holder
                }
                val canvas = surfaceHolder!!.lockCanvas()
                val outputBitmap = SegmentationUtil.instance.segmentationBitmap
                Log.d(TAG, "输出的图片的宽x高：${outputBitmap.width}x${outputBitmap.height}")
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

    companion object {
        const val TAG = "HumanSegActivity"
    }
}