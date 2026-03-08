package com.device.airgesture.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.*
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import com.device.airgesture.utils.LogUtil

class KeyActionExecutor(private val service: AccessibilityService) : ActionExecutor {

    companion object {
        private const val TAG = "KeyActionExecutor"
        private const val SCROLL_DISTANCE = 500    // 滚动距离（像素）
        private const val SCROLL_DURATION = 300L   // 滚动时长（ms）
        private const val SCROLL_COOLDOWN = 200L   // 滚动冷却时间（ms）

        /**
         * 翻页手势参数
         * 使用水平滑动模拟手指翻页，兼容各种 PPT/演示应用
         * - 滑动距离占屏幕宽度的比例
         * - 滑动持续时间要适中，太快有些应用不识别
         */
        private const val PAGE_SWIPE_RATIO = 0.4f    // 滑动距离占屏幕宽度的比例
        private const val PAGE_SWIPE_DURATION = 250L  // 翻页滑动时长（ms）
        private const val PAGE_SWIPE_COOLDOWN = 400L  // 翻页冷却时间（ms）

        private var lastScrollTime = 0L
        private var lastPageSwipeTime = 0L
        private var isScrolling = false
        private var isPageSwiping = false
    }

    override fun execute(action: Action): Boolean {
        return try {
            when (action) {
                // 翻页控制 — 优先使用屏幕滑动手势，兼容 PPT 全屏播放模式
                Action.NEXT_PAGE -> performPageSwipe(forward = true)
                Action.PREV_PAGE -> performPageSwipe(forward = false)

                // 滚动控制
                Action.SCROLL_UP -> performScroll(down = false)
                Action.SCROLL_DOWN -> performScroll(down = true)
                Action.SCROLL_STOP -> true // 停止滚动暂时不需要特殊操作

                // 系统功能
                Action.SCREENSHOT -> performScreenshot()
                Action.VOLUME_UP -> adjustVolume(increase = true)
                Action.VOLUME_DOWN -> adjustVolume(increase = false)
                Action.MUTE -> toggleMute()

                // 应用切换
                Action.RECENT_APPS -> service.performGlobalAction(GLOBAL_ACTION_RECENTS)
                Action.BACK -> service.performGlobalAction(GLOBAL_ACTION_BACK)
                Action.HOME -> service.performGlobalAction(GLOBAL_ACTION_HOME)

                // 媒体控制
                Action.PLAY_PAUSE -> performMediaAction(85)  // KEYCODE_HEADSETHOOK
                Action.NEXT_TRACK -> performMediaAction(87)  // KEYCODE_MEDIA_NEXT
                Action.PREV_TRACK -> performMediaAction(88)  // KEYCODE_MEDIA_PREVIOUS

                Action.NONE -> true
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to execute action: $action", e)
            false
        }
    }

    // ==================== 翻页 ====================

    /**
     * 通过模拟水平滑动手势实现翻页
     *
     * 之前使用 GLOBAL_ACTION_DPAD_RIGHT/LEFT 在 PPT 全屏播放模式下无效，
     * 因为大多数演示应用（WPS、Microsoft PowerPoint、Google Slides）在全屏模式下
     * 不处理 D-pad 事件，但都支持屏幕滑动手势翻页。
     *
     * 策略：
     * 1. API >= 24: 使用 dispatchGesture() 模拟水平滑动（最可靠）
     * 2. API < 24: 回退到 D-pad 全局动作
     *
     * @param forward true=下一页（从右向左滑），false=上一页（从左向右滑）
     */
    private fun performPageSwipe(forward: Boolean): Boolean {
        val currentTime = System.currentTimeMillis()

        // 冷却检查，防止翻页过于频繁
        if (currentTime - lastPageSwipeTime < PAGE_SWIPE_COOLDOWN) {
            LogUtil.d(TAG, "Page swipe cooldown, skipping")
            return false
        }

        // 防止并发冲突
        if (isPageSwiping) {
            LogUtil.d(TAG, "Already page swiping, skipping")
            return false
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // API < 24 回退到 D-pad
            lastPageSwipeTime = currentTime
            val action = if (forward) GLOBAL_ACTION_DPAD_RIGHT else GLOBAL_ACTION_DPAD_LEFT
            return service.performGlobalAction(action)
        }

        try {
            isPageSwiping = true
            val metrics = service.resources.displayMetrics
            val screenWidth = metrics.widthPixels.toFloat()
            val screenHeight = metrics.heightPixels.toFloat()
            val centerY = screenHeight / 2f
            val swipeDistance = screenWidth * PAGE_SWIPE_RATIO

            val path = Path()

            if (forward) {
                // 下一页：从屏幕右侧向左滑动（模拟手指从右往左划）
                val startX = screenWidth * 0.75f
                val endX = startX - swipeDistance
                path.moveTo(startX, centerY)
                path.lineTo(endX, centerY)
            } else {
                // 上一页：从屏幕左侧向右滑动（模拟手指从左往右划）
                val startX = screenWidth * 0.25f
                val endX = startX + swipeDistance
                path.moveTo(startX, centerY)
                path.lineTo(endX, centerY)
            }

            val gestureBuilder = GestureDescription.Builder()
            val stroke = GestureDescription.StrokeDescription(path, 0, PAGE_SWIPE_DURATION)
            gestureBuilder.addStroke(stroke)

            lastPageSwipeTime = currentTime

            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    isPageSwiping = false
                    LogUtil.d(TAG, "Page swipe completed: forward=$forward")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    isPageSwiping = false
                    LogUtil.w(TAG, "Page swipe cancelled: forward=$forward")
                }
            }

            val dispatched = service.dispatchGesture(gestureBuilder.build(), callback, null)
            if (!dispatched) {
                isPageSwiping = false
                // dispatchGesture 失败时回退到 D-pad
                LogUtil.w(TAG, "dispatchGesture failed for page swipe, falling back to D-pad")
                val action = if (forward) GLOBAL_ACTION_DPAD_RIGHT else GLOBAL_ACTION_DPAD_LEFT
                return service.performGlobalAction(action)
            }
            return true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to perform page swipe", e)
            isPageSwiping = false
            return false
        }
    }

    // ==================== 截屏 ====================

    private fun performScreenshot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            LogUtil.w(TAG, "Screenshot not supported on API < 28")
            false
        }
    }

    // ==================== 滚动 ====================

    private fun performScroll(down: Boolean): Boolean {
        // 防止滚动过于频繁
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScrollTime < SCROLL_COOLDOWN) {
            LogUtil.d(TAG, "Scroll cooldown, skipping")
            return false
        }

        // 防止滚动冲突
        if (isScrolling) {
            LogUtil.d(TAG, "Already scrolling, skipping")
            return false
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // Fallback to D-pad for older versions
            lastScrollTime = currentTime
            return service.performGlobalAction(
                if (down) GLOBAL_ACTION_DPAD_DOWN else GLOBAL_ACTION_DPAD_UP
            )
        }

        try {
            isScrolling = true
            val metrics = service.resources.displayMetrics
            val centerX = metrics.widthPixels / 2f
            val centerY = metrics.heightPixels / 2f

            val gestureBuilder = GestureDescription.Builder()
            val path = Path()

            // 调整滚动起点和终点，使滚动更自然
            val scrollStart = centerY
            val scrollEnd = if (down) {
                centerY + SCROLL_DISTANCE  // 向下滚动：手指向下移动
            } else {
                centerY - SCROLL_DISTANCE  // 向上滚动：手指向上移动
            }

            path.moveTo(centerX, scrollStart)
            path.lineTo(centerX, scrollEnd)

            val stroke = GestureDescription.StrokeDescription(path, 0, SCROLL_DURATION)
            gestureBuilder.addStroke(stroke)

            lastScrollTime = currentTime

            // 使用回调来重置滚动状态
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    isScrolling = false
                    LogUtil.d(TAG, "Scroll gesture completed")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    isScrolling = false
                    LogUtil.w(TAG, "Scroll gesture cancelled")
                }
            }

            return service.dispatchGesture(gestureBuilder.build(), callback, null)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to perform scroll", e)
            isScrolling = false
            return false
        }
    }

    // ==================== 音量 ====================

    private fun adjustVolume(increase: Boolean): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val path = Path()
                val metrics = service.resources.displayMetrics
                val x = metrics.widthPixels * 0.9f
                val y = metrics.heightPixels / 2f

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
        return false
    }

    private fun performMediaAction(action: Int): Boolean {
        return false
    }

    override fun isAvailable(): Boolean = true
}
