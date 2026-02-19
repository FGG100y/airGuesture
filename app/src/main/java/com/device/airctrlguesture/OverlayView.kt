package com.device.airctrlguesture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.components.containers.Connection
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.lang.Float.min

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var faceResult: FaceLandmarkerResult? = null
    private var handResult: HandLandmarkerResult? = null
    private var previewWidth: Int = 0
    private var previewHeight: Int = 0
    private var scaleX: Float = 1f
    private var scaleY: Float = 1f

    // 画笔配置
    private val facePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val rightEyePaint = Paint().apply {
        color = Color.RED
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val leftEyePaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val handPaint = Paint().apply {
        color = Color.BLUE
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }
    private val handLandmarkPaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 8f
        style = Paint.Style.FILL
    }

    // 更新检测结果
    fun updateResults(
        faceLandmarkerResult: FaceLandmarkerResult?,
        handLandmarkerResult: HandLandmarkerResult?,
        previewWidth: Int,
        previewHeight: Int
    ) {
        this.faceResult = faceLandmarkerResult
        this.handResult = handLandmarkerResult
        this.previewWidth = previewWidth
        this.previewHeight = previewHeight
        invalidate() // 触发重绘
    }

    // ===================== 坐标转换函数（适配镜像/旋转） =====================
    /**
     * 归一化 X 坐标 → View 实际像素 X 坐标（适配前置摄像头镜像）
     * @param x MediaPipe 返回的归一化 X 坐标（0~1）
     */
    private fun translateX(x: Float): Float {
        // 前置摄像头镜像后，X 坐标需要反转（1 - x）再缩放
        val mirroredX = 1 - x // 适配前置摄像头的水平镜像
        return mirroredX * previewWidth * scaleX
    }

    /**
     * 归一化 Y 坐标 → View 实际像素 Y 坐标
     * @param y MediaPipe 返回的归一化 Y 坐标（0~1）
     */
//    private fun translateY(y: Float): Float = y * previewHeight * scaleY
    private fun translateY(y: Float): Float {
        val mirroredY = 1 - y
        return mirroredY * previewHeight * scaleY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 计算缩放比例：OverlayView 实际尺寸 / 预览图像尺寸
        scaleX = width.toFloat() / previewWidth.toFloat()
        scaleY = height.toFloat() / previewHeight.toFloat()

        // 绘制面部关键点（传入 List<Int> 类型常量）
        faceResult?.faceLandmarks()?.forEach { faceLandmarks ->
            // 1. 计算等比缩放因子（参考示例，取宽高缩放的最小值保证完整显示）
            val scaleFactor = min(scaleX, scaleY)
            // 2. 计算缩放后的图像尺寸
            val scaledImageWidth = previewWidth * scaleFactor
            val scaledImageHeight = previewHeight * scaleFactor
            // 3. 计算居中偏移量（让面部关键点在View中居中）
            val offsetX = (width - scaledImageWidth) / 2f
            val offsetY = (height - scaledImageHeight) / 2f
            // 绘制面部各区域连接点（使用重构后的面部专用绘制函数）
            drawFaceConnectors(canvas, faceLandmarks, FaceLandmarker.FACE_LANDMARKS_RIGHT_EYE, rightEyePaint, scaleFactor, offsetX, offsetY)
            drawFaceConnectors(canvas, faceLandmarks, FaceLandmarker.FACE_LANDMARKS_LEFT_EYE, leftEyePaint, scaleFactor, offsetX, offsetY)
            drawFaceConnectors(canvas, faceLandmarks, FaceLandmarker.FACE_LANDMARKS_FACE_OVAL, facePaint, scaleFactor, offsetX, offsetY)
            drawFaceConnectors(canvas, faceLandmarks, FaceLandmarker.FACE_LANDMARKS_LIPS, facePaint, scaleFactor, offsetX, offsetY)

//            drawConnectors(canvas, faceLandmarks, FaceLandmarker.FACE_LANDMARKS_RIGHT_EYE, rightEyePaint)
//            drawConnectors(canvas, faceLandmarks, FaceLandmarker.FACE_LANDMARKS_LEFT_EYE, leftEyePaint)
//            drawConnectors(canvas, faceLandmarks, FaceLandmarker.FACE_LANDMARKS_FACE_OVAL, facePaint)
//            drawConnectors(canvas, faceLandmarks, FaceLandmarker.FACE_LANDMARKS_LIPS, facePaint)
//            drawConnectors(canvas, faceLandmarks, FaceLandmarker.FACE_LANDMARKS_RIGHT_IRIS, rightEyePaint)
//            drawConnectors(canvas, faceLandmarks, FaceLandmarker.FACE_LANDMARKS_LEFT_IRIS, leftEyePaint)
        }

        // 绘制手部关键点（传入 Set<Connection> 类型常量）
        handResult?.landmarks()?.forEach { handLandmarks ->
            // 调用通用绘制函数，传入手部的 Set<Connection> 常量
            drawConnectors(canvas, handLandmarks, HandLandmarker.HAND_CONNECTIONS, handPaint)

            // 绘制手部关键点
            handLandmarks.forEach { landmark ->
                canvas.drawCircle(
                    translateX(landmark.x()),
                    translateY(landmark.y()),
                    8f,
                    handLandmarkPaint
                )
            }
        }
    }

    private fun drawFaceConnectors(
        canvas: Canvas,
        landmarks: List<NormalizedLandmark>,
        connections: Set<Connection>,
        paint: Paint,
        scaleFactor: Float,
        offsetX: Float,
        offsetY: Float
    ) {
        // 遍历每个Connection对象（包含start/end索引）
        for (connection in connections) {
            // 关键修复：调用start()/end()获取Int类型索引（而非直接用Connection对象）
            val startIdx = connection.start()
            val endIdx = connection.end()

            // 严谨的边界检查（Int索引的合法性）
            if (startIdx < 0 || endIdx < 0 || startIdx >= landmarks.size || endIdx >= landmarks.size) {
                continue
            }

            // 获取起始/结束关键点（此时下标是Int，无类型错误）
            val startLandmark = landmarks[startIdx]
            val endLandmark = landmarks[endIdx]

            // 坐标转换（镜像+等比缩放+居中偏移）
            val startX = (1 - startLandmark.x()) * previewWidth * scaleFactor + offsetX
            val startY = (1 - startLandmark.y()) * previewHeight * scaleFactor + offsetY
            val endX = (1 - endLandmark.x()) * previewWidth * scaleFactor + offsetX
            val endY = (1 - endLandmark.y()) * previewHeight * scaleFactor + offsetY

            // 绘制连接线段
            canvas.drawLine(startX, startY, endX, endY, paint)
        }
    }

    /**
     * 通用绘制连接点函数（适配手部 Set<Connection> 类型）
     */
    private fun drawConnectors(
        canvas: Canvas,
        landmarks: List<NormalizedLandmark>,
        connections: Set<Connection>,
        paint: Paint
    ) {
        connections.forEach { connection ->
            val startIdx = connection.start()
            val endIdx = connection.end()
            if (startIdx >= landmarks.size || endIdx >= landmarks.size) return@forEach

            val start = landmarks[startIdx]
            val end = landmarks[endIdx]
            drawLine(canvas, start, end, paint)
        }
    }

    /**
     * 公共画线逻辑
     */
    private fun drawLine(
        canvas: Canvas,
        start: NormalizedLandmark,
        end: NormalizedLandmark,
        paint: Paint
    ) {
        canvas.drawLine(
            translateX(start.x()),
            translateY(start.y()),
            translateX(end.x()),
            translateY(end.y()),
            paint
        )
    }
}