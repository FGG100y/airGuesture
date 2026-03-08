package com.device.airgesture.analyzer

import com.device.airgesture.action.Gesture
import com.device.airgesture.utils.LogUtil
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.sqrt

/**
 * 静态手势分析器
 * 识别静态手势如OK、握拳、张开手掌等
 *
 * MediaPipe Hand Landmarks 索引：
 *   0=WRIST
 *   1=THUMB_CMC, 2=THUMB_MCP, 3=THUMB_IP, 4=THUMB_TIP
 *   5=INDEX_MCP, 6=INDEX_PIP, 7=INDEX_DIP, 8=INDEX_TIP
 *   9=MIDDLE_MCP, 10=MIDDLE_PIP, 11=MIDDLE_DIP, 12=MIDDLE_TIP
 *   13=RING_MCP, 14=RING_PIP, 15=RING_DIP, 16=RING_TIP
 *   17=PINKY_MCP, 18=PINKY_PIP, 19=PINKY_DIP, 20=PINKY_TIP
 */
class StaticGestureAnalyzer {

    companion object {
        private const val TAG = "StaticGestureAnalyzer"

        /**
         * OK手势中拇指尖与食指尖的最大距离（归一化坐标）
         * 拇指尖和食指尖接触形成圆圈是OK手势的核心特征
         */
        private const val OK_THUMB_INDEX_TIP_MAX_DIST = 0.08f

        /**
         * 手指伸直判断的比值阈值
         * tip-to-MCP 距离 / PIP-to-base 距离 > 此值 视为伸直
         */
        private const val FINGER_EXTENDED_RATIO = 0.9f
    }

    /**
     * 缓存上一帧的 landmarks 用于跨帧平滑（保留用于未来扩展）
     */
    private var lastLandmarks: List<NormalizedLandmark>? = null

    fun analyzeStaticGesture(result: HandLandmarkerResult): Gesture {
        if (result.landmarks().isEmpty()) {
            lastLandmarks = null
            return Gesture.NONE
        }

        val landmarks = result.landmarks()[0]
        lastLandmarks = landmarks

        // 分析手指状态
        val fingerStates = analyzeFingerStates(landmarks)

        // 根据手指状态和特征判断手势
        // 注意：OK 手势需要特殊的拇指-食指接触检测，优先于通用的 bent/straight 判断
        return when {
            isOkSign(landmarks, fingerStates) -> Gesture.OK_SIGN
            isFist(fingerStates) -> Gesture.FIST
            isPalmOpen(fingerStates) -> Gesture.PALM_OPEN
            isPeaceSign(fingerStates) -> Gesture.PEACE_SIGN
            else -> Gesture.NONE
        }
    }

    // ==================== 手指状态分析 ====================

    private fun analyzeFingerStates(landmarks: List<NormalizedLandmark>): BooleanArray {
        // 5个手指的状态：true 表示伸直，false 表示弯曲
        val states = BooleanArray(5)

        // 拇指（特殊处理）
        states[0] = isThumbExtended(landmarks)

        // 其他四指
        for (i in 1..4) {
            states[i] = isFingerExtended(landmarks, i)
        }

        return states
    }

    private fun isThumbExtended(landmarks: List<NormalizedLandmark>): Boolean {
        // 拇指关节点：1(CMC), 2(MCP), 3(IP), 4(TIP)
        val tip = landmarks[4]
        val ip = landmarks[3]
        val mcp = landmarks[2]
        val wrist = landmarks[0]

        // 使用拇指尖到手腕的距离 vs MCP 到手腕的距离判断
        val tipToWrist = calculateDistance(tip, wrist)
        val mcpToWrist = calculateDistance(mcp, wrist)

        // 同时检查 TIP 是否在 IP 之外（沿伸展方向）
        val tipToIp = calculateDistance(tip, ip)
        val ipToMcp = calculateDistance(ip, mcp)

        // 两个条件满足其一即视为伸直：
        // 1) 拇指尖到手腕足够远（经典判断，但阈值放宽）
        // 2) 拇指尖到 IP 足够长（拇指展开）
        return tipToWrist > mcpToWrist * 1.1f || tipToIp > ipToMcp * 0.9f
    }

    private fun isFingerExtended(landmarks: List<NormalizedLandmark>, fingerIndex: Int): Boolean {
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

        return tipToMcp > pipToBase * FINGER_EXTENDED_RATIO
    }

    // ==================== 辅助计算 ====================

    private fun calculateDistance(p1: NormalizedLandmark, p2: NormalizedLandmark): Float {
        val dx = p1.x() - p2.x()
        val dy = p1.y() - p2.y()
        val dz = p1.z() - p2.z()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * 计算两点之间的 2D 距离（忽略深度，用于屏幕上的位置判断）
     */
    private fun calculateDistance2D(p1: NormalizedLandmark, p2: NormalizedLandmark): Float {
        val dx = p1.x() - p2.x()
        val dy = p1.y() - p2.y()
        return sqrt(dx * dx + dy * dy)
    }

    // ==================== 手势判断 ====================

    private fun isFist(fingerStates: BooleanArray): Boolean {
        // 所有手指都弯曲
        return fingerStates.all { !it }
    }

    private fun isPalmOpen(fingerStates: BooleanArray): Boolean {
        // 所有手指都伸直
        return fingerStates.all { it }
    }

    /**
     * OK 手势判断
     *
     * OK 手势的核心特征：
     * 1. 拇指尖(4)与食指尖(8)接触或非常接近（形成圆圈）
     * 2. 中指(12)、无名指(16)、小指(20) 伸直
     *
     * 之前的实现仅检查 "拇指弯曲 + 食指弯曲 + 其余伸直"，
     * 但在实际使用中拇指的 extended/bent 判断极不稳定（因为 OK 手势中拇指
     * 既不是完全弯曲也不是完全伸直），导致 OK 手势几乎无法触发。
     *
     * 新实现以 "拇指尖-食指尖距离" 作为主判据，辅以其余三指伸直检查。
     */
    private fun isOkSign(landmarks: List<NormalizedLandmark>, fingerStates: BooleanArray): Boolean {
        val thumbTip = landmarks[4]   // THUMB_TIP
        val indexTip = landmarks[8]   // INDEX_TIP

        // 核心判据：拇指尖与食指尖接近
        val tipDistance = calculateDistance2D(thumbTip, indexTip)

        // 用手掌大小做归一化参考，提升对不同距离/手部大小的鲁棒性
        val wrist = landmarks[0]
        val middleMcp = landmarks[9]
        val palmSize = calculateDistance2D(wrist, middleMcp)

        // 归一化后的指尖距离
        val normalizedTipDist = if (palmSize > 0.001f) tipDistance / palmSize else tipDistance

        // 其余三指必须伸直
        val middleStraight = fingerStates[2]
        val ringStraight = fingerStates[3]
        val pinkyStraight = fingerStates[4]

        // 至少两根手指伸直即可（放宽小指条件，小指在 OK 手势中可能自然弯曲）
        val extendedCount = listOf(middleStraight, ringStraight, pinkyStraight).count { it }

        val isOk = normalizedTipDist < 0.35f && extendedCount >= 2

        if (isOk) {
            LogUtil.d(TAG, "OK gesture detected: tipDist=${"%.3f".format(normalizedTipDist)}, " +
                    "extendedFingers=$extendedCount (M=$middleStraight R=$ringStraight P=$pinkyStraight)")
        }

        return isOk
    }

    private fun isPeaceSign(fingerStates: BooleanArray): Boolean {
        // 食指和中指伸直，其他弯曲
        return !fingerStates[0] && fingerStates[1] && fingerStates[2] &&
                !fingerStates[3] && !fingerStates[4]
    }
}