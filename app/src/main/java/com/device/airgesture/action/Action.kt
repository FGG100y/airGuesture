package com.device.airgesture.action

enum class Action {
    // 翻页控制
    NEXT_PAGE,
    PREV_PAGE,
    
    // 滚动控制
    SCROLL_UP,
    SCROLL_DOWN,
    SCROLL_STOP,
    
    // 系统功能
    SCREENSHOT,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    
    // 应用切换
    RECENT_APPS,
    BACK,
    HOME,
    
    // 媒体控制
    PLAY_PAUSE,
    NEXT_TRACK,
    PREV_TRACK,
    
    // 特殊动作
    NONE            // 无动作
}
