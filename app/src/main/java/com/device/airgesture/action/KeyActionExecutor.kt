package com.device.airgesture.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.*
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.device.airgesture.utils.LogUtil

class KeyActionExecutor(private val service: AccessibilityService) : ActionExecutor {

    override fun execute(action: Action): Boolean {
        return when (action) {
            Action.NEXT_PAGE -> service.performGlobalAction(GLOBAL_ACTION_DPAD_RIGHT)
            Action.PREV_PAGE -> service.performGlobalAction(GLOBAL_ACTION_DPAD_LEFT)
            Action.SCREENSHOT -> false
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
