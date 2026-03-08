package com.device.airgesture.service

interface GestureEventListener {
    fun onGestureDirectionChanged(direction: Int)
}