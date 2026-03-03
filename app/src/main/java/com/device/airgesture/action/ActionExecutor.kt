package com.device.airgesture.action

interface ActionExecutor {
    fun execute(action: Action): Boolean
    fun isAvailable(): Boolean
}
