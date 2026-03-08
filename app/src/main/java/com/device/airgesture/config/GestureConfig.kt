package com.device.airgesture.config

import android.content.Context
import android.content.SharedPreferences

/**
 * 手势识别配置管理
 * 提供可调节的参数，方便优化手势识别效果
 */
object GestureConfig {
    
    private const val PREF_NAME = "gesture_config"
    private var preferences: SharedPreferences? = null
    
    fun init(context: Context) {
        if (preferences == null) {
            preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
        loadConfig()
    }
    
    // ========== 动态手势参数 ==========
    
    /**
     * 最小滑动距离（归一化坐标系，0-1）
     * 增大此值可以减少误触发，但需要更大的手势幅度
     */
    var minSwipeDistance = 0.08f
        set(value) {
            field = value
            saveFloat("min_swipe_distance", value)
        }
    
    /**
     * 最大滑动时间（毫秒）
     * 手势必须在此时间内完成，否则不识别
     */
    var maxSwipeTime = 800L
        set(value) {
            field = value
            saveLong("max_swipe_time", value)
        }
    
    /**
     * 最小滑动速度（归一化坐标/秒）
     * 增大此值需要更快的手势速度
     */
    var minSwipeVelocity = 0.15f
        set(value) {
            field = value
            saveFloat("min_swipe_velocity", value)
        }
    
    /**
     * 手势冷却时间（毫秒）
     * 两次手势之间的最小间隔
     */
    var gestureCooldown = 300L
        set(value) {
            field = value
            saveLong("gesture_cooldown", value)
        }
    
    // ========== 静态手势参数 ==========
    
    /**
     * 静态手势保持时间（毫秒）
     * 静态手势需要保持的最短时间
     */
    var staticHoldTime = 500L
        set(value) {
            field = value
            saveLong("static_hold_time", value)
        }
    
    /**
     * 静态手势置信度阈值（0-1）
     * 增大此值可以减少误识别
     */
    var staticConfidenceThreshold = 0.7f
        set(value) {
            field = value
            saveFloat("static_confidence_threshold", value)
        }
    
    // ========== 滚动控制参数 ==========
    
    /**
     * 滚动距离（像素）
     */
    var scrollDistance = 500
        set(value) {
            field = value
            saveInt("scroll_distance", value)
        }
    
    /**
     * 滚动持续时间（毫秒）
     */
    var scrollDuration = 300L
        set(value) {
            field = value
            saveLong("scroll_duration", value)
        }
    
    /**
     * 滚动冷却时间（毫秒）
     */
    var scrollCooldown = 200L
        set(value) {
            field = value
            saveLong("scroll_cooldown", value)
        }
    
    // ========== 追踪参数 ==========
    
    /**
     * 追踪超时时间（毫秒）
     * 如果手势追踪超过此时间，则重置
     */
    var trackingTimeout = 1000L
        set(value) {
            field = value
            saveLong("tracking_timeout", value)
        }
    
    /**
     * 手掌追踪平滑度（0-1）
     * 值越大，追踪越平滑但响应越慢
     */
    var trackingSmoothness = 0.7f
        set(value) {
            field = value
            saveFloat("tracking_smoothness", value)
        }
    
    // ========== MediaPipe 参数 ==========
    
    /**
     * 最小手部检测置信度
     */
    var minHandDetectionConfidence = 0.5f
        set(value) {
            field = value
            saveFloat("min_hand_detection_confidence", value)
        }
    
    /**
     * 最小追踪置信度
     */
    var minTrackingConfidence = 0.5f
        set(value) {
            field = value
            saveFloat("min_tracking_confidence", value)
        }
    
    /**
     * 最小手部存在置信度
     */
    var minHandPresenceConfidence = 0.5f
        set(value) {
            field = value
            saveFloat("min_hand_presence_confidence", value)
        }
    
    // ========== 调试参数 ==========
    
    /**
     * 是否启用调试日志
     */
    var debugEnabled = true
        set(value) {
            field = value
            saveBoolean("debug_enabled", value)
        }
    
    /**
     * 是否显示手势轨迹
     */
    var showGestureTrail = false
        set(value) {
            field = value
            saveBoolean("show_gesture_trail", value)
        }
    
    // ========== 持久化方法 ==========
    
    private fun loadConfig() {
        preferences?.let { prefs ->
            minSwipeDistance = prefs.getFloat("min_swipe_distance", minSwipeDistance)
            maxSwipeTime = prefs.getLong("max_swipe_time", maxSwipeTime)
            minSwipeVelocity = prefs.getFloat("min_swipe_velocity", minSwipeVelocity)
            gestureCooldown = prefs.getLong("gesture_cooldown", gestureCooldown)
            
            staticHoldTime = prefs.getLong("static_hold_time", staticHoldTime)
            staticConfidenceThreshold = prefs.getFloat("static_confidence_threshold", staticConfidenceThreshold)
            
            scrollDistance = prefs.getInt("scroll_distance", scrollDistance)
            scrollDuration = prefs.getLong("scroll_duration", scrollDuration)
            scrollCooldown = prefs.getLong("scroll_cooldown", scrollCooldown)
            
            trackingTimeout = prefs.getLong("tracking_timeout", trackingTimeout)
            trackingSmoothness = prefs.getFloat("tracking_smoothness", trackingSmoothness)
            
            minHandDetectionConfidence = prefs.getFloat("min_hand_detection_confidence", minHandDetectionConfidence)
            minTrackingConfidence = prefs.getFloat("min_tracking_confidence", minTrackingConfidence)
            minHandPresenceConfidence = prefs.getFloat("min_hand_presence_confidence", minHandPresenceConfidence)
            
            debugEnabled = prefs.getBoolean("debug_enabled", debugEnabled)
            showGestureTrail = prefs.getBoolean("show_gesture_trail", showGestureTrail)
        }
    }
    
    private fun saveFloat(key: String, value: Float) {
        preferences?.edit()?.putFloat(key, value)?.apply()
    }
    
    private fun saveInt(key: String, value: Int) {
        preferences?.edit()?.putInt(key, value)?.apply()
    }
    
    private fun saveLong(key: String, value: Long) {
        preferences?.edit()?.putLong(key, value)?.apply()
    }
    
    private fun saveBoolean(key: String, value: Boolean) {
        preferences?.edit()?.putBoolean(key, value)?.apply()
    }
    
    /**
     * 重置为默认配置
     */
    fun resetToDefaults() {
        preferences?.edit()?.clear()?.apply()
        
        // 重置为默认值
        minSwipeDistance = 0.08f
        maxSwipeTime = 800L
        minSwipeVelocity = 0.15f
        gestureCooldown = 300L
        
        staticHoldTime = 500L
        staticConfidenceThreshold = 0.7f
        
        scrollDistance = 500
        scrollDuration = 300L
        scrollCooldown = 200L
        
        trackingTimeout = 1000L
        trackingSmoothness = 0.7f
        
        minHandDetectionConfidence = 0.5f
        minTrackingConfidence = 0.5f
        minHandPresenceConfidence = 0.5f
        
        debugEnabled = true
        showGestureTrail = false
    }
}