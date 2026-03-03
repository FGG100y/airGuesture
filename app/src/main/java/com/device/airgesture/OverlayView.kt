package com.device.airgesture

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
import java.lang.Float.max

// FIXME：只要旋转手机屏幕过快，就会出现面部绘制结果上下颠倒
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var faceResult: FaceLandmarkerResult? = null
    private var handResult: HandLandmarkerResult? = null
    private var isFaceTrackingEnabled = true

    private var imageWidth = 0
    private var imageHeight = 0

    private var isFrontCamera = true

    // Debug mode
    private var showDebugInfo = false
    private var debugDx = 0f
    private var debugVelocity = 0f
    private var debugPoints: List<Pair<Float, Float>> = emptyList()

    // Debug paints
    private val debugLinePaint = Paint().apply {
        color = Color.parseColor("#FFA500")
        strokeWidth = 6f
    }

    private val debugTextPaint = Paint().apply {
        color = Color.GREEN
        textSize = 42f
    }

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

    fun setFaceTrackingEnabled(enabled: Boolean) {
        this.isFaceTrackingEnabled = enabled
        if (!enabled) {
            faceResult = null
        }
        invalidate()
    }

    fun setDebugMode(enabled: Boolean) {
        showDebugInfo = enabled
        invalidate()
    }

    fun updateDebugInfo(dx: Float, velocity: Float, points: List<Pair<Float, Float>>) {
        debugDx = dx
        debugVelocity = velocity
        debugPoints = points
        invalidate()
    }

    // =====================================================
    // 坐标转换
    // =====================================================
    private fun mapPoint(x: Float, y: Float): Pair<Float, Float> {

        if (imageWidth == 0 || imageHeight == 0)
            return 0f to 0f

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

        // 竖屏上下颠倒;
        py = height - py

        // ⭐ 前置镜像
        if (isFrontCamera) {
            px = width - px
        }

        return px to py
    }

    // =====================================================
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isFaceTrackingEnabled) {
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

        // ---------- Debug Mode ----------
        if (showDebugInfo && debugPoints.isNotEmpty()) {
            for (i in 0 until debugPoints.size - 1) {
                val (x1, y1) = mapPoint(debugPoints[i].first, debugPoints[i].second)
                val (x2, y2) = mapPoint(debugPoints[i + 1].first, debugPoints[i + 1].second)
                canvas.drawLine(
                    x1,
                    y1,
                    x2,
                    y2,
                    debugLinePaint
                )
            }

            val density = context.resources.displayMetrics.density
            val marginTop = (48 * density).toFloat()
            val marginEnd = (16 * density).toFloat()
            val textX = width - marginEnd - debugTextPaint.measureText("dx=0.00")
            val textY1 = marginTop + debugTextPaint.textSize
            val textY2 = textY1 + debugTextPaint.textSize + (8 * density)

            canvas.drawText(
                "dx=%.2f".format(debugDx),
                textX,
                textY1,
                debugTextPaint
            )
            canvas.drawText(
                "vel=%.2f".format(debugVelocity),
                textX,
                textY2,
                debugTextPaint
            )
        }
    }

    private fun drawFace(
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