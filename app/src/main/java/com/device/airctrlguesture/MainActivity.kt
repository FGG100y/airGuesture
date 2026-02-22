package com.device.airctrlguesture

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Surface
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
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
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var cameraProvider: ProcessCameraProvider? = null
    private var faceLandmarker: FaceLandmarker? = null
    private var handLandmarker: HandLandmarker? = null
    private lateinit var cameraExecutor: ExecutorService

    // 相机参数变量（用于传给OverlayView）
    private var isFrontCamera = true // 默认前置

    private var rotationDegrees = 0

    // 权限请求码
    private val REQUEST_CAMERA_PERMISSION = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.viewFinder)
        overlayView = findViewById(R.id.overlayView)

        // 初始化线程池
        cameraExecutor = Executors.newSingleThreadExecutor()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        // 检查相机权限
        if (allPermissionsGranted()) {
            initMediaPipeModels()
            setupCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }
    }

    /**
     * 统一相机初始化逻辑（合并预览+图像分析，解决重复绑定问题）
     */
    private fun setupCamera() {
        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(
                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                )
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            isFrontCamera = true

            cameraProvider.unbindAll()

            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )

            // 关键：不要传分辨率
            overlayView.setCameraParams(
                isFrontCamera = isFrontCamera
            )
        }, ContextCompat.getMainExecutor(this))
    }

//    private fun getScreenRotationDegrees(): Int {
//        return when (windowManager.defaultDisplay.rotation) {
//            Surface.ROTATION_0 -> 0
//            Surface.ROTATION_90 -> 90
//            Surface.ROTATION_180 -> 180
//            Surface.ROTATION_270 -> 270
//            else -> 0
//        }
//    }
//
//    /**
//     * 监听屏幕旋转，更新相机参数
//     */
//    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
//        super.onConfigurationChanged(newConfig)
//        // 重新获取旋转角度并更新OverlayView
//        rotationDegrees = getScreenRotationDegrees()
//        isFrontCamera = false
//        overlayView.setCameraParams(isFrontCamera)
//    }

    /**
     * 初始化MediaPipe模型（逻辑不变）
     */
    private fun initMediaPipeModels() {
        CoroutineScope(Dispatchers.IO).launch {
            // 初始化面部检测器
            val faceOptions = FaceLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("face_landmarker.task")
                        .build()
                )
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                .setResultListener { result, inputImage ->
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
                        .setModelAssetPath("hand_landmarker.task")
                        .build()
                )
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(2)
                .setResultListener { result, inputImage ->
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

    /**
     * 处理相机帧（逻辑不变，依赖YuvToArgbConverter工具类）
     */
    private fun processImage(imageProxy: ImageProxy) {
        val frameTime = SystemClock.uptimeMillis()
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        try {
            // 1. YUV_420_888 转 ARGB_8888（前置摄像头同时做镜像）
            val argbBitmap = YuvToArgbConverter.convert(mediaImage, applyMirror = isFrontCamera)

            // 2. 根据旋转角度调整图像方向
            rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val rotatedBitmap = when (rotationDegrees) {
                90 -> YuvToArgbConverter.rotateBitmap(argbBitmap, 90)
                180 -> YuvToArgbConverter.rotateBitmap(argbBitmap, 180)
                270 -> YuvToArgbConverter.rotateBitmap(argbBitmap, 270)
                else -> argbBitmap
            }

            val mpImage: MPImage = BitmapImageBuilder(rotatedBitmap).build()

            // 5. 提交帧到检测器
            faceLandmarker?.detectAsync(mpImage, frameTime)
            handLandmarker?.detectAsync(mpImage, frameTime)

        } catch (e: Exception) {
            Log.e("ImageConversion", "图像转换失败: ${e.message}", e)
        } finally {
            imageProxy.close()
        }
    }

    /**
     * 检查权限（逻辑不变）
     */
    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this,
        android.Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * 权限请求回调（逻辑不变）
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (allPermissionsGranted()) {
                initMediaPipeModels()
                setupCamera()
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

    /**
     * 释放资源（逻辑不变）
     */
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceLandmarker?.close()
        handLandmarker?.close()
        cameraProvider?.unbindAll()
    }
}