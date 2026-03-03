package com.device.airgesture.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.*
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.device.airgesture.utils.LogUtil

class KeyActionExecutor(private val service: AccessibilityService) : ActionExecutor {

    companion object {
        private const val TAG = "KeyActionExecutor"
        private const val SCROLL_DISTANCE = 300
        private const val SCROLL_DURATION = 200L
    }

    override fun execute(action: Action): Boolean {
        return try {
            when (action) {
                // 翻页控制
                Action.NEXT_PAGE -> service.performGlobalAction(GLOBAL_ACTION_DPAD_RIGHT)
                Action.PREV_PAGE -> service.performGlobalAction(GLOBAL_ACTION_DPAD_LEFT)
                
                // 滚动控制
                Action.SCROLL_UP -> performScroll(false)
                Action.SCROLL_DOWN -> performScroll(true)
                Action.SCROLL_STOP -> true // 停止滚动暂时不需要特殊操作
                
                // 系统功能
                Action.SCREENSHOT -> performScreenshot()
                Action.VOLUME_UP -> adjustVolume(true)
                Action.VOLUME_DOWN -> adjustVolume(false)
                Action.MUTE -> toggleMute()
                
                // 应用切换
                Action.RECENT_APPS -> service.performGlobalAction(GLOBAL_ACTION_RECENTS)
                Action.BACK -> service.performGlobalAction(GLOBAL_ACTION_BACK)
                Action.HOME -> service.performGlobalAction(GLOBAL_ACTION_HOME)
                
                // 媒体控制
                Action.PLAY_PAUSE -> performMediaAction(85) // KEYCODE_HEADSETHOOK
                Action.NEXT_TRACK -> performMediaAction(87) // KEYCODE_MEDIA_NEXT  
                Action.PREV_TRACK -> performMediaAction(88) // KEYCODE_MEDIA_PREVIOUS
                
                Action.NONE -> true
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to execute action: $action", e)
            false
        }
    }

    private fun performScreenshot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            LogUtil.w(TAG, "Screenshot not supported on API < 28")
            false
        }
    }

    private fun performScroll(down: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // Fallback to D-pad for older versions
            return service.performGlobalAction(
                if (down) GLOBAL_ACTION_DPAD_DOWN else GLOBAL_ACTION_DPAD_UP
            )
        }

        try {
            val metrics = service.resources.displayMetrics
            val centerX = metrics.widthPixels / 2f
            val centerY = metrics.heightPixels / 2f

            val gestureBuilder = GestureDescription.Builder()
            val path = Path()

            if (down) {
                path.moveTo(centerX, centerY - SCROLL_DISTANCE)
                path.lineTo(centerX, centerY + SCROLL_DISTANCE)
            } else {
                path.moveTo(centerX, centerY + SCROLL_DISTANCE)
                path.lineTo(centerX, centerY - SCROLL_DISTANCE)
            }

            val stroke = GestureDescription.StrokeDescription(path, 0, SCROLL_DURATION)
            gestureBuilder.addStroke(stroke)

            return service.dispatchGesture(gestureBuilder.build(), null, null)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to perform scroll", e)
            return false
        }
    }

    private fun adjustVolume(increase: Boolean): Boolean {
        // Volume control through key events
        // Note: AccessibilityService doesn't have direct volume control global actions
        // We need to use a different approach
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Use gesture to simulate volume key press
                val path = Path()
                val metrics = service.resources.displayMetrics
                val x = metrics.widthPixels * 0.9f
                val y = metrics.heightPixels / 2f
                
                // Quick tap on the edge to trigger volume
                path.moveTo(x, y)
                val gestureBuilder = GestureDescription.Builder()
                val stroke = GestureDescription.StrokeDescription(path, 0, 50)
                gestureBuilder.addStroke(stroke)
                service.dispatchGesture(gestureBuilder.build(), null, null)
            } else {
                false
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to adjust volume", e)
            false
        }
    }

    private fun toggleMute(): Boolean {
        // Mute is not directly available in AccessibilityService
        return false
    }

    private fun performMediaAction(action: Int): Boolean {
        // Media actions are not directly available as global actions
        // These would need to be implemented differently, possibly through intents
        return false
    }

    override fun isAvailable(): Boolean = true
}
