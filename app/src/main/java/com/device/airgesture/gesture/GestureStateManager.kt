package com.device.airgesture.gesture

import android.graphics.PointF
import android.os.SystemClock
import com.device.airgesture.action.Gesture
import com.device.airgesture.config.GestureConfig
import com.device.airgesture.utils.LogUtil
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 手势状态管理器
 * 负责管理手势的生命周期，防止误触发和重复触发
 */
class GestureStateManager {
    
    companion object {
        private const val TAG = "GestureStateManager"
    }
    
    private var currentState: GestureState = GestureState.Idle
    private var lastGestureTime: Long = 0
    private var lastStaticGesture: Gesture? = null
    private var staticGestureStartTime: Long = 0
    
    /**
     * 更新手势状态
     * @param handPresent 是否检测到手
     * @param palmCenter 手掌中心坐标（归一化）
     * @param currentTime 当前时间戳
     */
    fun updateState(
        handPresent: Boolean,
        palmCenter: PointF?,
        currentTime: Long = SystemClock.uptimeMillis()
    ): GestureState {
        
        // 如果没有检测到手，重置状态
        if (!handPresent || palmCenter == null) {
            if (currentState !is GestureState.Idle) {
                LogUtil.d(TAG, "No hand detected, resetting to Idle")
                currentState = GestureState.Idle
            }
            return currentState
        }
        
        // 根据当前状态进行状态转换
        val state = currentState  // 创建局部变量避免并发问题
        currentState = when (state) {
            is GestureState.Idle -> {
                handleIdleState(palmCenter, currentTime)
            }
            
            is GestureState.Tracking -> {
                handleTrackingState(state, palmCenter, currentTime)
            }
            
            is GestureState.Recognizing -> {
                handleRecognizingState(state, currentTime)
            }
            
            is GestureState.Completed -> {
                handleCompletedState(state, currentTime)
            }
            
            is GestureState.Cooldown -> {
                handleCooldownState(state, currentTime)
            }
        }
        
        return currentState
    }
    
    private fun handleIdleState(palmCenter: PointF, currentTime: Long): GestureState {
        // 开始追踪新手势
        return GestureState.Tracking(
            startPoint = palmCenter,
            startTime = currentTime,
            currentPoint = palmCenter,
            trackingPoints = mutableListOf(palmCenter)
        )
    }
    
    private fun handleTrackingState(
        state: GestureState.Tracking,
        palmCenter: PointF,
        currentTime: Long
    ): GestureState {
        
        // 检查追踪超时
        if (currentTime - state.startTime > GestureConfig.trackingTimeout) {
            LogUtil.d(TAG, "Tracking timeout, resetting to Idle")
            return GestureState.Idle
        }
        
        // 更新追踪点
        state.trackingPoints.add(palmCenter)
        
        // 计算移动距离
        val distance = calculateDistance(state.startPoint, palmCenter)
        val duration = currentTime - state.startTime
        
        // 检查是否达到手势识别条件
        if (distance >= GestureConfig.minSwipeDistance && duration <= GestureConfig.maxSwipeTime) {
            LogUtil.d(TAG, "Gesture detected, distance=$distance, duration=$duration")
            return GestureState.Recognizing(
                startPoint = state.startPoint,
                endPoint = palmCenter,
                duration = duration
            )
        }
        
        // 继续追踪
        return state.copy(currentPoint = palmCenter)
    }
    
    private fun handleRecognizingState(
        state: GestureState.Recognizing,
        currentTime: Long
    ): GestureState {
        
        // 识别手势类型
        val gesture = recognizeGesture(state.startPoint, state.endPoint, state.duration)
        
        return if (gesture != Gesture.NONE) {
            lastGestureTime = currentTime
            GestureState.Completed(
                gesture = gesture,
                confidence = calculateConfidence(state.startPoint, state.endPoint, state.duration),
                completedTime = currentTime
            )
        } else {
            GestureState.Idle
        }
    }
    
    private fun handleCompletedState(
        state: GestureState.Completed,
        currentTime: Long
    ): GestureState {
        // 进入冷却状态
        return GestureState.Cooldown(until = currentTime + GestureConfig.gestureCooldown)
    }
    
    private fun handleCooldownState(
        state: GestureState.Cooldown,
        currentTime: Long
    ): GestureState {
        // 检查冷却是否结束
        return if (currentTime >= state.until) {
            GestureState.Idle
        } else {
            state
        }
    }
    
    /**
     * 识别手势类型
     */
    private fun recognizeGesture(
        startPoint: PointF,
        endPoint: PointF,
        duration: Long
    ): Gesture {
        
        val dx = endPoint.x - startPoint.x
        val dy = endPoint.y - startPoint.y
        val distance = sqrt(dx * dx + dy * dy)
        val velocity = distance / (duration / 1000f)
        
        // 速度检查
        if (velocity < GestureConfig.minSwipeVelocity) {
            LogUtil.d(TAG, "Velocity too low: $velocity < ${GestureConfig.minSwipeVelocity}")
            return Gesture.NONE
        }
        
        // 判断主要方向
        return when {
            abs(dx) > abs(dy) -> {
                // 水平移动
                if (dx > 0) Gesture.SWIPE_RIGHT else Gesture.SWIPE_LEFT
            }
            abs(dy) > GestureConfig.minSwipeDistance -> {
                // 垂直移动
                if (dy > 0) Gesture.SWIPE_DOWN else Gesture.SWIPE_UP
            }
            else -> Gesture.NONE
        }
    }
    
    /**
     * 计算手势置信度
     */
    private fun calculateConfidence(
        startPoint: PointF,
        endPoint: PointF,
        duration: Long
    ): Float {
        val distance = calculateDistance(startPoint, endPoint)
        val velocity = distance / (duration / 1000f)
        
        // 基于距离和速度计算置信度
        val distanceScore = (distance / (GestureConfig.minSwipeDistance * 3)).coerceIn(0f, 1f)
        val velocityScore = (velocity / (GestureConfig.minSwipeVelocity * 3)).coerceIn(0f, 1f)
        
        return (distanceScore + velocityScore) / 2f
    }
    
    /**
     * 计算两点间距离
     */
    private fun calculateDistance(p1: PointF, p2: PointF): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * 处理静态手势
     */
    fun handleStaticGesture(
        gesture: Gesture,
        currentTime: Long = SystemClock.uptimeMillis()
    ): Boolean {
        
        // 检查是否是同一个静态手势
        if (gesture == lastStaticGesture) {
            // 检查是否保持足够时间
            if (currentTime - staticGestureStartTime >= GestureConfig.staticHoldTime) {
                // 避免重复触发
                if (currentTime - lastGestureTime >= GestureConfig.gestureCooldown) {
                    lastGestureTime = currentTime
                    lastStaticGesture = null  // 重置以允许再次触发
                    return true
                }
            }
        } else {
            // 新的静态手势
            lastStaticGesture = gesture
            staticGestureStartTime = currentTime
        }
        
        return false
    }
    
    /**
     * 重置状态
     */
    fun reset() {
        currentState = GestureState.Idle
        lastStaticGesture = null
        staticGestureStartTime = 0
    }
    
    /**
     * 获取当前状态
     */
    fun getCurrentState(): GestureState = currentState
    
    /**
     * 是否可以接受新手势
     */
    fun canAcceptGesture(): Boolean {
        return currentState is GestureState.Idle
    }
}