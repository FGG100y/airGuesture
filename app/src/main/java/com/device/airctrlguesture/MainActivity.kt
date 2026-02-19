package com.device.airctrlguesture

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
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
    // 标记是否是前置摄像头（用于镜像处理）
    private var isFrontCamera = true

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

    // 启动相机（保持不变，新增前置摄像头标记）
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
            isFrontCamera = true // 标记为前置摄像头

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

    // 核心修复：使用 YuvToArgbConverter 处理 YUV 转 ARGB，解决缓冲区不足问题
    private fun processImage(imageProxy: ImageProxy) {
        val frameTime = SystemClock.uptimeMillis()
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        try {
            // 1. YUV_420_888 转 ARGB_8888（前置摄像头同时做镜像）
            val argbBitmap = YuvToArgbConverter.convert(mediaImage, applyMirror = isFrontCamera)

            // 2. 旋转位图适配相机预览方向
            val rotatedBitmap = YuvToArgbConverter.rotateBitmap(
                argbBitmap,
                imageProxy.imageInfo.rotationDegrees
            )

            // 3. 构建 MPImage（MediaPipe 官方推荐方式）
            val mpImage: MPImage = BitmapImageBuilder(rotatedBitmap)
                .build()

            // 4. 提交帧到面部/手部检测器（异步）
            faceLandmarker?.detectAsync(mpImage, frameTime)
            handLandmarker?.detectAsync(mpImage, frameTime)

        } catch (e: Exception) {
            Log.e("ImageConversion", "图像转换失败: ${e.message}", e)
        } finally {
            // 5. 必须关闭 ImageProxy 释放相机资源
            imageProxy.close()
        }
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