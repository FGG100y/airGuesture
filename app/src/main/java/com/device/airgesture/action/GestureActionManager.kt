package com.device.airgesture.action

import com.device.airgesture.utils.LogUtil

class GestureActionManager private constructor() {

    private var executor: ActionExecutor? = null
    private val gestureToAction = mapOf(
        Gesture.SWIPE_LEFT to Action.NEXT_PAGE,
        Gesture.SWIPE_RIGHT to Action.PREV_PAGE
    )

    fun setExecutor(executor: ActionExecutor) {
        this.executor = executor
    }

    fun onGestureDetected(gesture: Gesture) {
        val action = gestureToAction[gesture] ?: run {
            LogUtil.w(TAG, "Unknown gesture: $gesture")
            return
        }

        executor?.let { exec ->
            if (exec.isAvailable()) {
                val success = exec.execute(action)
                LogUtil.d(TAG, "Execute $action, success: $success")
            } else {
                LogUtil.w(TAG, "Executor not available")
            }
        } ?: LogUtil.w(TAG, "Executor not set")
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
