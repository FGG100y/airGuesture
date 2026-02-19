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

    // ===================== 坐标转换函数（修复核心） =====================
    /**
     * 归一化 X 坐标 → View 实际像素 X 坐标
     * @param x MediaPipe 返回的归一化 X 坐标（0~1）
     */
    private fun translateX(x: Float): Float = x * previewWidth * scaleX

    /**
     * 归一化 Y 坐标 → View 实际像素 Y 坐标
     * @param y MediaPipe 返回的归一化 Y 坐标（0~1）
     */
    private fun translateY(y: Float): Float = y * previewHeight * scaleY

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        scaleX = width.toFloat() / previewWidth.toFloat()
        scaleY = height.toFloat() / previewHeight.toFloat()

        // 绘制面部关键点（传入 List<Int> 类型常量）
        faceResult?.faceLandmarks()?.forEach { faceLandmarks ->
            drawConnectors(canvas, faceLandmarks, FaceLandmarker.FACE_LANDMARKS_RIGHT_EYE, rightEyePaint)
            drawConnectors(canvas, faceLandmarks, FaceLandmarker.FACE_LANDMARKS_LEFT_EYE, leftEyePaint)
            drawConnectors(canvas, faceLandmarks, FaceLandmarker.FACE_LANDMARKS_FACE_OVAL, facePaint)
            drawConnectors(canvas, faceLandmarks, FaceLandmarker.FACE_LANDMARKS_LIPS, facePaint)
//            drawConnectors(canvas, faceLandmarks, FaceLandmarker.FACE_LANDMARKS_RIGHT_EYEBROW, rightEyePaint)
//            drawConnectors(canvas, faceLandmarks, FaceLandmarker.FACE_LANDMARKS_LEFT_EYEBROW, leftEyePaint)
            drawConnectors(canvas, faceLandmarks, FaceLandmarker.FACE_LANDMARKS_RIGHT_IRIS, rightEyePaint)
        }

        // 绘制手部关键点（传入 Set<Connection> 类型常量）
        handResult?.landmarks()?.forEach { handLandmarks ->
            // 调用通用绘制函数，传入手部的 Set<Connection> 常量
            drawConnectors(canvas, handLandmarks, HandLandmarker.HAND_CONNECTIONS, handPaint)

            // 绘制手部关键点（保留原有逻辑）
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

    // ===================== 通用绘制函数（兼容两种类型） =====================
    /**
     * 通用绘制连接点函数
     * 适配：
     * 1. 面部：List<Int> 类型连接索引
     * 2. 手部：Set<Connection> 类型连接对象
     */
    // 重载1：适配面部的 List<Int> 类型
    private fun drawConnectors(
        canvas: Canvas,
        landmarks: List<NormalizedLandmark>,
        connections: List<Int>,
        paint: Paint
    ) {
        for (i in 0 until connections.size step 2) {
            if (i + 1 >= connections.size) break
            val startIdx = connections[i]
            val endIdx = connections[i + 1]
            if (startIdx >= landmarks.size || endIdx >= landmarks.size) continue

            val start = landmarks[startIdx]
            val end = landmarks[endIdx]
            drawLine(canvas, start, end, paint)
        }
    }

    // 重载2：适配手部的 Set<Connection> 类型
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

    // 抽取公共画线逻辑（减少冗余）
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