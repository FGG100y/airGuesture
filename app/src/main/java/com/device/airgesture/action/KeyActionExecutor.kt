package com.device.airgesture.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_LEFT
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT

class KeyActionExecutor(private val service: AccessibilityService) : ActionExecutor {

    override fun execute(action: Action): Boolean {
        return when (action) {
            Action.NEXT_PAGE -> service.performGlobalAction(GLOBAL_ACTION_DPAD_RIGHT)
            Action.PREV_PAGE -> service.performGlobalAction(GLOBAL_ACTION_DPAD_LEFT)
            Action.SCREENSHOT -> false
        }
    }

    override fun isAvailable(): Boolean = true
}
