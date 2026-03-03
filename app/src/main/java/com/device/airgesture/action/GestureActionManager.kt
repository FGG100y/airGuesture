package com.device.airgesture.action

import android.content.Context
import android.content.SharedPreferences
import com.device.airgesture.utils.LogUtil

class GestureActionManager private constructor() {

    private var executor: ActionExecutor? = null
    private var preferences: SharedPreferences? = null
    
    // 默认手势映射
    private val defaultGestureToAction = mapOf(
        Gesture.SWIPE_LEFT to Action.PREV_PAGE,
        Gesture.SWIPE_RIGHT to Action.NEXT_PAGE,
        Gesture.SWIPE_UP to Action.SCROLL_UP,
        Gesture.SWIPE_DOWN to Action.SCROLL_DOWN,
        Gesture.OK_SIGN to Action.SCREENSHOT,
        Gesture.FIST to Action.SCROLL_STOP,
        Gesture.CIRCLE_CW to Action.RECENT_APPS,
        Gesture.L_SHAPE to Action.BACK,
        Gesture.PEACE_SIGN to Action.HOME,
        Gesture.PALM_OPEN to Action.PLAY_PAUSE
    )
    
    // 当前手势映射（可从配置加载）
    private var gestureToAction = defaultGestureToAction.toMutableMap()

    fun init(context: Context) {
        preferences = context.getSharedPreferences("gesture_config", Context.MODE_PRIVATE)
        loadConfiguration()
    }

    fun setExecutor(executor: ActionExecutor) {
        this.executor = executor
    }

    fun onGestureDetected(gesture: Gesture) {
        if (gesture == Gesture.NONE) return
        
        val action = gestureToAction[gesture]
        if (action == null || action == Action.NONE) {
            LogUtil.d(TAG, "No action mapped for gesture: $gesture")
            return
        }

        executor?.let { exec ->
            if (exec.isAvailable()) {
                val success = exec.execute(action)
                LogUtil.d(TAG, "Execute $action for $gesture, success: $success")
            } else {
                LogUtil.w(TAG, "Executor not available")
            }
        } ?: LogUtil.w(TAG, "Executor not set")
    }

    fun setGestureAction(gesture: Gesture, action: Action) {
        gestureToAction[gesture] = action
        saveConfiguration(gesture, action)
        LogUtil.d(TAG, "Mapped $gesture to $action")
    }

    fun getGestureAction(gesture: Gesture): Action? {
        return gestureToAction[gesture]
    }

    fun getAllMappings(): Map<Gesture, Action> {
        return gestureToAction.toMap()
    }

    fun resetToDefaults() {
        gestureToAction.clear()
        gestureToAction.putAll(defaultGestureToAction)
        preferences?.edit()?.clear()?.apply()
        LogUtil.d(TAG, "Reset to default gesture mappings")
    }

    private fun loadConfiguration() {
        preferences?.let { prefs ->
            for (gesture in Gesture.values()) {
                if (gesture == Gesture.NONE) continue
                
                val actionName = prefs.getString("gesture_${gesture.name}", null)
                if (actionName != null) {
                    try {
                        val action = Action.valueOf(actionName)
                        gestureToAction[gesture] = action
                        LogUtil.d(TAG, "Loaded config: $gesture -> $action")
                    } catch (e: IllegalArgumentException) {
                        LogUtil.e(TAG, "Invalid action in config: $actionName", e)
                    }
                }
            }
        }
    }

    private fun saveConfiguration(gesture: Gesture, action: Action) {
        preferences?.edit()?.putString("gesture_${gesture.name}", action.name)?.apply()
    }

    fun isReady(): Boolean = executor != null && executor?.isAvailable() == true

    companion object {
        private const val TAG = "GestureActionManager"

        @Volatile
        private var instance: GestureActionManager? = null

        fun getInstance(): GestureActionManager {
            return instance ?: synchronized(this) {
                instance ?: GestureActionManager().also { instance = it }
            }
        }
    }
}
