package com.device.airctrlguesture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.components.containers.Connection
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.lang.Float.max
import java.util.LinkedList

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

    private var drawCanvas: Canvas? = null
    private var drawBitmap: Bitmap? = null
    private var previousDrawPoint: Pair<Float, Float>? = null
    private var isDrawing = false

    private val DRAW_COLOR = Color.RED
    private val STROKE_WIDTH = 8f

    private val waveHistory = LinkedList<WaveSample>()
    private val WAVE_MIN_OSCILLATIONS = 2
    private val WAVE_TIME_WINDOW_MS = 1000L
    private val MIN_WAVE_DISPLACEMENT = 80f

    private var lastWaveDirection = 0
    private var oscillationCount = 0

    private var clearFlashTime = 0L
    private val FLASH_DURATION_MS = 200L

    private var isWaveGestureDetected = false
    private var waveGestureStartTime = 0L
    private val WAVE_GESTURE_LOCK_MS = 500L

    private data class WaveSample(
        val x: Float,
        val timestamp: Long
    )

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

    private val drawPaint = Paint().apply {
        color = DRAW_COLOR
        strokeWidth = STROKE_WIDTH
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val flashPaint = Paint().apply {
        color = Color.WHITE
        alpha = 100
        style = Paint.Style.FILL
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            val oldBitmap = drawBitmap
            drawBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawCanvas = Canvas(drawBitmap!!)
            
            if (oldBitmap != null && !oldBitmap.isRecycled) {
                val paint = Paint()
                val srcRect = android.graphics.Rect(0, 0, oldBitmap.width, oldBitmap.height)
                val dstRect = android.graphics.Rect(0, 0, w, h)
                drawCanvas?.drawBitmap(oldBitmap, srcRect, dstRect, paint)
                oldBitmap.recycle()
            }
        }
    }

    fun clearCanvas() {
        drawCanvas?.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        waveHistory.clear()
        oscillationCount = 0
        lastWaveDirection = 0
        previousDrawPoint = null
        isDrawing = false
        isWaveGestureDetected = false
        waveGestureStartTime = 0L
        invalidate()
    }

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

        processDrawingAndGestures()
        invalidate()
    }

    private fun isOpenPalm(hand: List<NormalizedLandmark>): Boolean {
        if (hand.size < 21) return false

        val thumbTip = hand[4]
        val thumbIp = hand[3]
        val indexTip = hand[8]
        val indexPip = hand[6]
        val middleTip = hand[12]
        val middlePip = hand[10]
        val ringTip = hand[16]
        val ringPip = hand[14]
        val pinkyTip = hand[20]
        val pinkyPip = hand[18]

        val thumbUp = thumbTip.y() < thumbIp.y()
        val indexUp = indexTip.y() < indexPip.y()
        val middleUp = middleTip.y() < middlePip.y()
        val ringUp = ringTip.y() < ringPip.y()
        val pinkyUp = pinkyTip.y() < pinkyPip.y()

        val extendedFingers = listOf(indexUp, middleUp, ringUp, pinkyUp).count { it }
        
        return extendedFingers >= 4
    }

    private fun processDrawingAndGestures() {
        val hands = handResult?.landmarks() ?: return
        if (hands.isEmpty()) {
            previousDrawPoint = null
            isDrawing = false
            isWaveGestureDetected = false
            waveHistory.clear()
            return
        }

        if (isWaveGestureDetected && System.currentTimeMillis() - waveGestureStartTime < WAVE_GESTURE_LOCK_MS) {
            previousDrawPoint = null
            isDrawing = false
            return
        }
        isWaveGestureDetected = false

        var handProcessed = false

        for (hand in hands) {
            if (hand.size < 21) continue

            handProcessed = true

            if (isOpenPalm(hand)) {
                previousDrawPoint = null
                isDrawing = false
                processWaveGesture(hand[8].x())
                return
            }

            val indexTip = hand[8]
            val indexPip = hand[6]
            val currentPoint = mapPoint(indexTip.x(), indexTip.y())

            val isIndexRaised = indexTip.y() < indexPip.y()

            if (isIndexRaised) {
                if (previousDrawPoint != null && isDrawing) {
                    drawCanvas?.drawLine(
                        previousDrawPoint!!.first,
                        previousDrawPoint!!.second,
                        currentPoint.first,
                        currentPoint.second,
                        drawPaint
                    )
                }
                previousDrawPoint = currentPoint
                isDrawing = true
            } else {
                previousDrawPoint = null
                isDrawing = false
                processWaveGesture(currentPoint.first)
            }

            break
        }

        if (!handProcessed || hands.isEmpty()) {
            processWaveGesture(0f)
        }
    }

    private fun processWaveGesture(currentX: Float) {
        val now = System.currentTimeMillis()

        waveHistory.add(WaveSample(currentX, now))

        while (waveHistory.isNotEmpty() && now - waveHistory.peek().timestamp > WAVE_TIME_WINDOW_MS) {
            waveHistory.poll()
        }

        if (waveHistory.size < 3) return

        val samples = waveHistory.toList()
        if (samples.size < 2) return

        var directionChanges = 0
        var prevDirection = 0

        for (i in 1 until samples.size) {
            val prevSample = samples[i - 1] ?: continue
            val currSample = samples[i] ?: continue
            val displacement = currSample.x - prevSample.x

            if (kotlin.math.abs(displacement) < MIN_WAVE_DISPLACEMENT / 10) continue

            val currentDirection = if (displacement > 0) 1 else -1

            if (prevDirection != 0 && currentDirection != prevDirection) {
                directionChanges++
            }
            prevDirection = currentDirection
        }

        if (directionChanges >= WAVE_MIN_OSCILLATIONS) {
            clearCanvas()
            clearFlashTime = System.currentTimeMillis()
            isWaveGestureDetected = true
            waveGestureStartTime = System.currentTimeMillis()
        }
    }

    fun setCameraParams(isFrontCamera: Boolean) {
        this.isFrontCamera = isFrontCamera
    }

    private fun mapPoint(x: Float, y: Float): Pair<Float, Float> {
        if (imageWidth == 0 || imageHeight == 0)
            return 0f to 0f

        val scale = max(
            width.toFloat() / imageWidth,
            height.toFloat() / imageHeight
        )

        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale

        val offsetX = (scaledWidth - width) / 2f
        val offsetY = (scaledHeight - height) / 2f

        var px = x * scaledWidth - offsetX
        var py = y * scaledHeight - offsetY

        py = height - py

        if (isFrontCamera) {
            px = width - px
        }

        return px to py
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }

        if (System.currentTimeMillis() - clearFlashTime < FLASH_DURATION_MS) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flashPaint)
        }

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

    private fun drawFace(
        canvas: Canvas,
        landmarks: List<NormalizedLandmark>,
        connections: Set<Connection>,
        paint: Paint
    ) {
        for (c in connections) {
            val start = landmarks[c.start()]
            val end = landmarks[c.end()]
            val (x1, y1) = mapPoint(start.x(), start.y())
            val (x2, y2) = mapPoint(end.x(), end.y())
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

            val (x1, y1) = mapPoint(s.x(), s.y())
            val (x2, y2) = mapPoint(e.x(), e.y())
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
