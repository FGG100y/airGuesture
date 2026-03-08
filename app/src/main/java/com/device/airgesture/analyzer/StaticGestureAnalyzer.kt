package com.device.airgesture.analyzer

import com.device.airgesture.action.Gesture
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 静态手势分析器
 * 识别静态手势如OK、握拳、张开手掌等
 */
class StaticGestureAnalyzer {

    companion object {
        // 手指弯曲阈值
        private const val FINGER_BENT_THRESHOLD = 0.3f
        private const val FINGER_STRAIGHT_THRESHOLD = 0.7f
        
        // 添加置信度阈值，避免误判
        private const val MIN_GESTURE_CONFIDENCE = 0.7f
        private const val ANGLE_THRESHOLD = 30f  // 角度阈值（度）
    }

    fun analyzeStaticGesture(result: HandLandmarkerResult): Gesture {
        if (result.landmarks().isEmpty()) {
            return Gesture.NONE
        }

        val landmarks = result.landmarks()[0]
        
        // 分析手指状态
        val fingerStates = analyzeFingerStates(landmarks)
        
        // 根据手指状态判断手势
        return when {
            isFist(fingerStates) -> Gesture.FIST
            isPalmOpen(fingerStates) -> Gesture.PALM_OPEN
            isOkSign(fingerStates) -> Gesture.OK_SIGN
            isPeaceSign(fingerStates) -> Gesture.PEACE_SIGN
            else -> Gesture.NONE
        }
    }

    private fun analyzeFingerStates(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): BooleanArray {
        // 5个手指的状态：true表示伸直，false表示弯曲
        val states = BooleanArray(5)
        
        // 拇指（特殊处理）
        states[0] = isThumbExtended(landmarks)
        
        // 其他四指
        for (i in 1..4) {
            states[i] = isFingerExtended(landmarks, i)
        }
        
        return states
    }

    private fun isThumbExtended(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Boolean {
        // 拇指关节点：1(CMC), 2(MCP), 3(IP), 4(TIP)
        val tip = landmarks[4]
        val mcp = landmarks[2]
        val wrist = landmarks[0]
        
        // 计算拇指尖到手腕的距离
        val distance = calculateDistance(tip, wrist)
        val referenceDistance = calculateDistance(mcp, wrist)
        
        return distance > referenceDistance * 1.3f
    }

    private fun isFingerExtended(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>, fingerIndex: Int): Boolean {
        // 手指索引映射到关节点
        // 1: 食指 (5-8), 2: 中指 (9-12), 3: 无名指 (13-16), 4: 小指 (17-20)
        val baseIndex = fingerIndex * 4 + 1
        
        val tip = landmarks[baseIndex + 3]
        val pip = landmarks[baseIndex + 2]
        val mcp = landmarks[baseIndex + 1]
        val base = landmarks[baseIndex]
        
        // 检查手指是否伸直
        val tipToMcp = calculateDistance(tip, mcp)
        val pipToBase = calculateDistance(pip, base)
        
        return tipToMcp > pipToBase * 0.9f
    }

    private fun calculateDistance(
        p1: com.google.mediapipe.tasks.components.containers.NormalizedLandmark,
        p2: com.google.mediapipe.tasks.components.containers.NormalizedLandmark
    ): Float {
        val dx = p1.x() - p2.x()
        val dy = p1.y() - p2.y()
        val dz = p1.z() - p2.z()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun isFist(fingerStates: BooleanArray): Boolean {
        // 所有手指都弯曲
        return fingerStates.all { !it }
    }

    private fun isPalmOpen(fingerStates: BooleanArray): Boolean {
        // 所有手指都伸直
        return fingerStates.all { it }
    }

    private fun isOkSign(fingerStates: BooleanArray): Boolean {
        // OK手势：拇指和食指形成圈，其他手指伸直
        // 更严格的判断条件，避免误判
        val thumbBent = !fingerStates[0]
        val indexBent = !fingerStates[1]
        val middleStraight = fingerStates[2]
        val ringStraight = fingerStates[3]
        val pinkyStraight = fingerStates[4]
        
        // 需要中指、无名指、小指都伸直，拇指和食指都弯曲
        return thumbBent && indexBent && middleStraight && ringStraight && pinkyStraight
    }

    private fun isPeaceSign(fingerStates: BooleanArray): Boolean {
        // 食指和中指伸直，其他弯曲
        return !fingerStates[0] && fingerStates[1] && fingerStates[2] && 
               !fingerStates[3] && !fingerStates[4]
    }
}