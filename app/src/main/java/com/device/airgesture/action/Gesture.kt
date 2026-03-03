package com.device.airgesture.action

enum class Gesture {
    // 滑动手势
    SWIPE_LEFT,
    SWIPE_RIGHT,
    SWIPE_UP,
    SWIPE_DOWN,
    
    // 静态手势
    FIST,           // 握拳
    PALM_OPEN,      // 张开手掌
    OK_SIGN,        // OK手势
    PEACE_SIGN,     // 剪刀手/V字手势
    
    // 特殊手势
    CIRCLE_CW,      // 顺时针画圈
    CIRCLE_CCW,     // 逆时针画圈
    L_SHAPE,        // L形手势
    
    // 状态手势
    NONE            // 无手势
}
