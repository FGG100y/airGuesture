package com.device.airgesture.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.device.airgesture.GestureNative
import com.device.airgesture.MainActivity
import com.device.airgesture.R
import com.device.airgesture.YuvToArgbConverter
import android.graphics.PointF
import com.device.airgesture.action.Gesture
import com.device.airgesture.action.GestureActionManager
import com.device.airgesture.analyzer.StaticGestureAnalyzer
import com.device.airgesture.config.GestureConfig
import com.device.airgesture.gesture.GestureState
import com.device.airgesture.gesture.GestureStateManager
import com.device.airgesture.utils.LogUtil
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class GestureRecognitionService : LifecycleService() {

    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var handLandmarker: HandLandmarker? = null
    private lateinit var cameraExecutor: ExecutorService
    private val staticGestureAnalyzer = StaticGestureAnalyzer()
    private val gestureStateManager = GestureStateManager()

    private var cachedBitmap: Bitmap? = null
    private var cachedRotatedBitmap: Bitmap? = null
    private var cachedPixels: IntArray? = null
    private var cachedMirrorPixels: IntArray? = null

    private var lastImageWidth = 0
    private var lastImageHeight = 0
    private var lastRotationDegrees = 0
    private val isFrontCamera = true

    override fun onCreate() {
        super.onCreate()
        LogUtil.d(TAG, "========== GestureRecognitionService onCreate ==========")

        LogUtil.d(TAG, "初始化手势配置...")
        GestureConfig.init(this)
        
        LogUtil.d(TAG, "创建通知渠道...")
        createNotificationChannel()
        
        LogUtil.d(TAG, "启动前台服务...")
        startForegroundService()

        LogUtil.d(TAG, "初始化相机线程池...")
        cameraExecutor = Executors.newSingleThreadExecutor()

        LogUtil.d(TAG, "启动悬浮窗服务...")
        startOverlayService()

        LogUtil.d(TAG, "初始化 MediaPipe 和相机...")
        initMediaPipeAndCamera()
        
        LogUtil.d(TAG, "========== Service 初始化完成 ==========")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "手势识别服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "手势识别服务运行中"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundService() {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AirGesture")
            .setContentText("手势识别服务运行中")
            .setSmallIcon(R.drawable.ic_hand_palm)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun initMediaPipeAndCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture?.addListener({
            cameraProvider = cameraProviderFuture?.get()
            setupCamera()
        }, ContextCompat.getMainExecutor(this))

        lifecycleScope.launch(Dispatchers.IO) {
            initHandLandmarker()
        }
    }

    private fun setupCamera() {
        val cameraProvider = cameraProvider ?: return

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImage(imageProxy)
                }
            }

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        cameraProvider.unbindAll()

        cameraProvider.bindToLifecycle(
            this,
            cameraSelector,
            imageAnalysis
        )

        LogUtil.d(TAG, "Camera bound to service")
    }

    private fun initHandLandmarker() {
        val handOptions = HandLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(2)
            .setResultListener { result, inputImage ->
                val timestamp = SystemClock.uptimeMillis()
                val handPresent = result.landmarks().isNotEmpty()

                if (handPresent) {
                    val landmarks = result.landmarks()[0]
                    
                    // 计算手掌中心（使用手腕和中指根部的中点）
                    val wrist = landmarks[0]  // 手腕
                    val middleMcp = landmarks[9]  // 中指根部
                    val palmCenterX = (wrist.x() + middleMcp.x()) / 2f
                    val palmCenterY = (wrist.y() + middleMcp.y()) / 2f
                    val palmCenter = PointF(palmCenterX, palmCenterY)
                    
                    // 更新手势状态
                    val gestureState = gestureStateManager.updateState(
                        handPresent = true,
                        palmCenter = palmCenter,
                        currentTime = timestamp
                    )
                    
                    // 根据状态处理手势
                    when (gestureState) {
                        is GestureState.Completed -> {
                            // 动态手势完成
                            LogUtil.d(TAG, "Gesture completed: ${gestureState.gesture} with confidence ${gestureState.confidence}")
                            GestureActionManager.getInstance().onGestureDetected(gestureState.gesture)
                            
                            // 更新方向指示
                            val direction = when (gestureState.gesture) {
                                Gesture.SWIPE_LEFT -> -1
                                Gesture.SWIPE_RIGHT -> 1
                                Gesture.SWIPE_UP -> 2
                                Gesture.SWIPE_DOWN -> -2
                                else -> 0
                            }
                            sendGestureDirection(direction, gestureState.gesture)
                        }
                        
                        is GestureState.Tracking -> {
                            // 正在追踪手势，同时检查静态手势
                            val staticGesture = staticGestureAnalyzer.analyzeStaticGesture(result)
                            if (staticGesture != Gesture.NONE) {
                                // 使用状态管理器处理静态手势（内部会阻止 Tracking 超时）
                                if (gestureStateManager.handleStaticGesture(staticGesture, timestamp)) {
                                    LogUtil.d(TAG, "Static gesture triggered: $staticGesture")
                                    GestureActionManager.getInstance().onGestureDetected(staticGesture)
                                    sendGestureDirection(0, staticGesture)
                                }
                            } else {
                                // 没有检测到静态手势，清除保持标志，恢复正常超时
                                gestureStateManager.clearStaticGestureHold()
                                sendGestureDirection(0)
                            }
                        }
                        
                        else -> {
                            sendGestureDirection(0)
                        }
                    }
                } else {
                    // 没有检测到手
                    gestureStateManager.updateState(
                        handPresent = false,
                        palmCenter = null,
                        currentTime = timestamp
                    )
                    sendGestureDirection(0)
                }
            }
            .setErrorListener { error ->
                LogUtil.e(TAG, "HandLandmarker error: ${error.message}")
            }
            .build()

        handLandmarker = HandLandmarker.createFromOptions(this, handOptions)
        LogUtil.d(TAG, "HandLandmarker initialized")
    }

    private fun processImage(imageProxy: ImageProxy) {
        val frameTime = SystemClock.uptimeMillis()
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        try {
            val width = mediaImage.width
            val height = mediaImage.height
            val rotation = imageProxy.imageInfo.rotationDegrees

            if (width != lastImageWidth || height != lastImageHeight || rotation != lastRotationDegrees) {
                cachedBitmap?.recycle()
                cachedRotatedBitmap?.recycle()
                cachedBitmap = null
                cachedRotatedBitmap = null
                cachedPixels = IntArray(width * height)
                cachedMirrorPixels = if (isFrontCamera) IntArray(width * height) else null
                lastImageWidth = width
                lastImageHeight = height
                lastRotationDegrees = rotation
            }

            val argbBitmap = YuvToArgbConverter.convertWithReuse(
                mediaImage,
                applyMirror = isFrontCamera,
                reuseBitmap = cachedBitmap,
                reusePixels = cachedPixels,
                reuseMirrorPixels = cachedMirrorPixels
            )
            cachedBitmap = argbBitmap

            val rotatedBitmap = when (rotation) {
                90 -> YuvToArgbConverter.rotateBitmapWithReuse(argbBitmap, 90, cachedRotatedBitmap)
                180 -> YuvToArgbConverter.rotateBitmapWithReuse(argbBitmap, 180, cachedRotatedBitmap)
                270 -> YuvToArgbConverter.rotateBitmapWithReuse(argbBitmap, 270, cachedRotatedBitmap)
                else -> argbBitmap
            }
            if (rotation != 0 && rotatedBitmap !== argbBitmap) {
                cachedRotatedBitmap = rotatedBitmap
            }

            val mpImage: MPImage = BitmapImageBuilder(rotatedBitmap).build()

            handLandmarker?.detectAsync(mpImage, frameTime)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Image processing error: ${e.message}", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun startOverlayService() {
        LogUtil.d(TAG, "========== 启动悬浮窗服务 ==========")
        try {
            val intent = Intent(this, GestureOverlayService::class.java)
            startService(intent)
            LogUtil.d(TAG, "startService() 已调用")
        } catch (e: Exception) {
            LogUtil.e(TAG, "启动悬浮窗服务失败: ${e.message}", e)
        }
    }

    private fun stopOverlayService() {
        val intent = Intent(this, GestureOverlayService::class.java)
        stopService(intent)
        LogUtil.d(TAG, "Overlay service stopped")
    }

    private fun sendGestureDirection(direction: Int, gesture: Gesture? = null) {
        val intent = Intent(ACTION_GESTURE_DIRECTION).apply {
            putExtra(EXTRA_DIRECTION, direction)
            gesture?.let { putExtra(EXTRA_GESTURE, it.name) }
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        LogUtil.d(TAG, "Service onDestroy")

        stopOverlayService()

        cameraExecutor.shutdown()
        handLandmarker?.close()
        cameraProvider?.unbindAll()

        cachedBitmap?.recycle()
        cachedRotatedBitmap?.recycle()

        super.onDestroy()
    }

    companion object {
        private const val TAG = "GestureRecognition"
        private const val CHANNEL_ID = "gesture_service_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_GESTURE_DIRECTION = "com.airgesture.GESTURE_DIRECTION"
        const val EXTRA_DIRECTION = "direction"
        const val EXTRA_GESTURE = "gesture"
    }
}
