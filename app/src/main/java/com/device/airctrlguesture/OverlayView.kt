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
<<<<<<< HEAD
import java.lang.Float.min
=======
import java.lang.Float.max
>>>>>>> fixScaleFactorIssue

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var faceResult: FaceLandmarkerResult? = null
    private var handResult: HandLandmarkerResult? = null

    private var imageWidth = 0
    private var imageHeight = 0

    private var isFrontCamera = true

    // ---------- Paint ----------
    private val facePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val rightEyePaint = Paint().apply {
        color = Color.RED
        strokeWidth = 3f
    }

    private val leftEyePaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 3f
    }

    private val handPaint = Paint().apply {
        color = Color.BLUE
        strokeWidth = 5f
    }

    private val handPointPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }

    // =====================================================
    // 更新检测结果（⭐ 只使用 MediaPipe 输入尺寸）
    // =====================================================
    fun updateResults(
        face: FaceLandmarkerResult?,
        hand: HandLandmarkerResult?,
        inputWidth: Int,
        inputHeight: Int
    ) {
        faceResult = face ?: faceResult
        handResult = hand ?: handResult

        imageWidth = inputWidth
        imageHeight = inputHeight

        invalidate()
    }

    fun setCameraParams(isFrontCamera: Boolean) {
        this.isFrontCamera = isFrontCamera
    }

    // =====================================================
    // 坐标转换
    // =====================================================
    private fun mapPoint(x: Float, y: Float): Pair<Float, Float> {

        if (imageWidth == 0 || imageHeight == 0)
            return 0f to 0f

<<<<<<< HEAD
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
=======
        // ===== CENTER_CROP 计算 =====
        val scale = max(
            width.toFloat() / imageWidth,
            height.toFloat() / imageHeight
        )

        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale

        val offsetX = (scaledWidth - width) / 2f
        val offsetY = (scaledHeight - height) / 2f

        // MediaPipe → scaled image
        var px = x * scaledWidth - offsetX
        var py = y * scaledHeight - offsetY

        // 竖屏上下颠倒; FIXME：只要旋转手机屏幕过快，就会出现面部绘制结果上下颠倒
        py = height - py

        // ⭐ 前置镜像
        if (isFrontCamera) {
            px = width - px
>>>>>>> fixScaleFactorIssue
        }

        return px to py
    }

    // =====================================================
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // ---------- Face ----------
        faceResult?.faceLandmarks()?.forEach { landmarks ->

            drawFace(
                canvas,
                landmarks,
                FaceLandmarker.FACE_LANDMARKS_FACE_OVAL,
                facePaint
            )

            drawFace(
                canvas,
                landmarks,
                FaceLandmarker.FACE_LANDMARKS_RIGHT_EYE,
                rightEyePaint
            )

            drawFace(
                canvas,
                landmarks,
                FaceLandmarker.FACE_LANDMARKS_LEFT_EYE,
                leftEyePaint
            )

            drawFace(
                canvas,
                landmarks,
                FaceLandmarker.FACE_LANDMARKS_LIPS,
                facePaint
            )
        }

        // ---------- Hands ----------
        handResult?.landmarks()?.forEach { hand ->

            drawConnections(
                canvas,
                hand,
                HandLandmarker.HAND_CONNECTIONS,
                handPaint
            )

            hand.forEach {
                val (px, py) = mapPoint(it.x(), it.y())
                canvas.drawCircle(
                    px,
                    py,
                    8f,
                    handPointPaint
                )
            }
        }
    }

<<<<<<< HEAD
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
=======
    // =====================================================
    private fun drawFace(
>>>>>>> fixScaleFactorIssue
        canvas: Canvas,
        landmarks: List<NormalizedLandmark>,
        connections: Set<Connection>,
        paint: Paint
    ) {
        for (c in connections) {
            val start = landmarks[c.start()]
            val end = landmarks[c.end()]
            val (x1,y1) = mapPoint(start.x(), start.y())
            val (x2,y2) = mapPoint(end.x(), end.y())
            canvas.drawLine(
                x1,
                y1,
                x2,
                y2,
                paint
            )
        }
    }

    private fun drawConnections(
        canvas: Canvas,
        landmarks: List<NormalizedLandmark>,
        connections: Set<Connection>,
        paint: Paint
    ) {
        for (c in connections) {
            val s = landmarks[c.start()]
            val e = landmarks[c.end()]

            val (x1,y1) = mapPoint(s.x(), s.y())
            val (x2,y2) = mapPoint(e.x(), e.y())
            canvas.drawLine(
                x1,
                y1,
                x2,
                y2,
                paint
            )
        }
    }
}