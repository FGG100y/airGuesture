package com.device.airgesture.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.device.airgesture.action.GestureActionManager
import com.device.airgesture.action.KeyActionExecutor
import com.device.airgesture.utils.LogUtil

class AirGestureAccessibilityService : AccessibilityService() {

    private lateinit var keyExecutor: KeyActionExecutor

    override fun onServiceConnected() {
        super.onServiceConnected()
        LogUtil.d(TAG, "Service connected")

        keyExecutor = KeyActionExecutor(this)
        GestureActionManager.getInstance().setExecutor(keyExecutor)

        LogUtil.d(TAG, "GestureActionManager initialized")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtil.d(TAG, "Service destroyed")
    }

    companion object {
        private const val TAG = "AirGestureA11yService"
    }
}
