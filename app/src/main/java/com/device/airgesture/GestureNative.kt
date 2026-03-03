package com.device.airgesture

object GestureNative {

    init {
        System.loadLibrary("gesture-lib")
    }

    external fun updateGesture(
        x: Float,
        y: Float,
        timestamp: Long,
        handPresent: Boolean
    ): Int

    external fun getDebugInfo(buffer: FloatArray)
}