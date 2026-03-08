package com.device.airgesture.gesture

import android.graphics.PointF

/**
 * 手势状态定义
 */
sealed class GestureState {
    /**
     * 空闲状态 - 等待手势开始
     */
    object Idle : GestureState()
    
    /**
     * 追踪状态 - 正在追踪手势轨迹
     */
    data class Tracking(
        val startPoint: PointF,
        val startTime: Long,
        val currentPoint: PointF,
        val trackingPoints: MutableList<PointF> = mutableListOf()
    ) : GestureState()
    
    /**
     * 手势识别中 - 正在分析手势
     */
    data class Recognizing(
        val startPoint: PointF,
        val endPoint: PointF,
        val duration: Long
    ) : GestureState()
    
    /**
     * 手势完成 - 已识别出手势
     */
    data class Completed(
        val gesture: com.device.airgesture.action.Gesture,
        val confidence: Float,
        val completedTime: Long
    ) : GestureState()
    
    /**
     * 冷却状态 - 防止连续触发
     */
    data class Cooldown(
        val until: Long
    ) : GestureState()
}