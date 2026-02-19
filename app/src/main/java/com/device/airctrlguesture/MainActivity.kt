package com.device.airctrlguesture

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.core.processing.YuvToRgbConverter
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker.FaceLandmarkerOptions
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    // 手动声明控件（替代 View Binding）
    private lateinit var viewFinder: PreviewView
    private lateinit var overlayView: OverlayView

    private var cameraProvider: ProcessCameraProvider? = null
    private var faceLandmarker: FaceLandmarker? = null
    private var handLandmarker: HandLandmarker? = null
    private lateinit var cameraExecutor: ExecutorService

    // 权限请求码
    private val REQUEST_CAMERA_PERMISSION = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 加载布局 + 获取控件实例
        setContentView(R.layout.activity_main)
        viewFinder = findViewById(R.id.viewFinder)
        overlayView = findViewById(R.id.overlayView)

        // 初始化线程池
        cameraExecutor = Executors.newSingleThreadExecutor()

        // 检查相机权限
        if (allPermissionsGranted()) {
            startCamera()
            initMediaPipeModels()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }
    }

    // 初始化MediaPipe模型（保持不变，仅修改检测时的图像类型）
    private fun initMediaPipeModels() {
        CoroutineScope(Dispatchers.IO).launch {
            // 初始化面部检测器
            val faceOptions = FaceLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("face_landmarker.task") // 模型文件放在assets目录
                        .build()
                )
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                .setResultListener { result, inputImage ->
                    // 回调到主线程更新UI
                    runOnUiThread {
                        overlayView.updateResults(
                            result,
                            null,
                            inputImage.width,
                            inputImage.height
                        )
                    }
                }
                .setErrorListener { error ->
                    Log.e("FaceLandmarker", "检测错误: ${error.message}")
                }
                .build()
            faceLandmarker = FaceLandmarker.createFromOptions(this@MainActivity, faceOptions)

            // 初始化手部检测器
            val handOptions = HandLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("hand_landmarker.task") // 模型文件放在assets目录
                        .build()
                )
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(2)
                .setResultListener { result, inputImage ->
                    // 回调到主线程更新UI
                    runOnUiThread {
                        overlayView.updateResults(
                            null,
                            result,
                            inputImage.width,
                            inputImage.height
                        )
                    }
                }
                .setErrorListener { error ->
                    Log.e("HandLandmarker", "检测错误: ${error.message}")
                }
                .build()
            handLandmarker = HandLandmarker.createFromOptions(this@MainActivity, handOptions)
        }
    }

    // 启动相机（保持不变）
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // 配置预览
            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(viewFinder.surfaceProvider) }

            // 配置图像分析（实时帧处理）
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy: ImageProxy ->
                        processImage(imageProxy)
                    }
                }

            // 选择前置摄像头
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("CameraX", "相机绑定失败: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage: Image = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        // 关键修复：YUV_420_888 → RGBA_8888 Bitmap → MPImage
        val mpImage: MPImage = try {
            // 1. 将 YUV Image 转换为 RGBA_8888 格式的 Bitmap
            val bitmap = mediaImage.toRgbaBitmap(imageProxy.width, imageProxy.height)
            // 2. 通过 Bitmap 构建 MPImage（MediaPipe 官方支持）
            com.google.mediapipe.framework.image.BitmapImageBuilder(bitmap)
                .setRotation(imageProxy.imageInfo.rotationDegrees) // 适配相机旋转角度
                .build()
        } catch (e: Exception) {
            Log.e("ImageConversion", "图像转换失败: ${e.message}", e)
            imageProxy.close()
            return
        }

        // 提交帧到面部/手部检测器（异步）
        faceLandmarker?.detectAsync(mpImage, imageProxy.imageInfo.timestamp)
        handLandmarker?.detectAsync(mpImage, imageProxy.imageInfo.timestamp)

        // 延迟关闭 ImageProxy（确保检测器完成处理）
        cameraExecutor.execute {
            imageProxy.close()
        }
    }

    // 新增：YUV_420_888 Image 转 RGBA_8888 Bitmap 的工具函数
    private fun Image.toRgbaBitmap(width: Int, height: Int): Bitmap {
        val yuvToRgbConverter = YuvToRgbConverter(this@MainActivity)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        yuvToRgbConverter.yuvToRgb(this, bitmap)
        return bitmap
    }

    // 检查权限（保持不变）
    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this,
        android.Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    // 权限请求回调（保持不变）
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (allPermissionsGranted()) {
                startCamera()
                initMediaPipeModels()
            } else {
                Toast.makeText(
                    this,
                    "相机权限被拒绝，应用无法正常工作",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    // 释放资源（保持不变）
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceLandmarker?.close()
        handLandmarker?.close()
        cameraProvider?.unbindAll()
    }
}