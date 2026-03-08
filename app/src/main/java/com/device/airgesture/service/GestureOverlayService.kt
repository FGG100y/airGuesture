package com.device.airgesture.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.device.airgesture.R
import com.device.airgesture.utils.LogUtil

class GestureOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayContainer: LinearLayout? = null
    private var overlayIcon: ImageView? = null
    private var overlayLabel: TextView? = null

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val direction = intent?.getIntExtra(GestureRecognitionService.EXTRA_DIRECTION, 0) ?: 0
            val gesture = intent?.getStringExtra(GestureRecognitionService.EXTRA_GESTURE)
            updateGestureIcon(gesture, direction)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var resetDirectionRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        LogUtil.d(TAG, "Overlay service onCreate")

        val filter = IntentFilter(GestureRecognitionService.ACTION_GESTURE_DIRECTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, filter)
        }

        showOverlay()
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val density = resources.displayMetrics.density
        val iconSizePx = (ICON_SIZE_DP * density).toInt()
        val paddingPx = (8 * density).toInt()
        val labelSizeSp = 11f

        // 使用 LinearLayout 容器包含图标和文字标签
        overlayContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        }

        overlayIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_gesture_idle)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val lp = LinearLayout.LayoutParams(iconSizePx, iconSizePx)
            layoutParams = lp
        }

        overlayLabel = TextView(this).apply {
            text = ""
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, labelSizeSp)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (2 * density).toInt()
            layoutParams = lp
        }

        overlayContainer!!.addView(overlayIcon)
        overlayContainer!!.addView(overlayLabel)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = 100

        try {
            windowManager.addView(overlayContainer, params)
            LogUtil.d(TAG, "Overlay view added")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to add overlay view", e)
        }
    }

    /**
     * 根据手势类型获取图标资源和显示标签
     *
     * 每种手势有独立的图标和中文标签，让用户能清楚知道识别到了什么手势、
     * 将会执行什么操作。
     */
    private fun getGestureVisual(gesture: String?, direction: Int): Pair<Int, String> {
        return when {
            gesture == "OK_SIGN" -> Pair(R.drawable.ic_gesture_ok, "截屏")
            gesture == "FIST" -> Pair(R.drawable.ic_gesture_fist, "停止")
            gesture == "PALM_OPEN" -> Pair(R.drawable.ic_gesture_palm, "播放/暂停")
            gesture == "PEACE_SIGN" -> Pair(R.drawable.ic_gesture_peace, "主页")

            direction == -1 || gesture == "SWIPE_LEFT" -> Pair(R.drawable.ic_gesture_left, "上一页")
            direction == 1 || gesture == "SWIPE_RIGHT" -> Pair(R.drawable.ic_gesture_right, "下一页")
            direction == 2 || gesture == "SWIPE_UP" -> Pair(R.drawable.ic_gesture_up, "向上滑")
            direction == -2 || gesture == "SWIPE_DOWN" -> Pair(R.drawable.ic_gesture_down, "向下滑")

            else -> Pair(R.drawable.ic_gesture_idle, "")
        }
    }

    /**
     * 判断是否为"动作触发"类手势（需要特殊脉冲动画）
     */
    private fun isActionGesture(gesture: String?): Boolean {
        return gesture in setOf("OK_SIGN", "FIST", "PALM_OPEN", "PEACE_SIGN")
    }

    private fun updateGestureIcon(gesture: String?, direction: Int) {
        // 取消之前的重置任务
        resetDirectionRunnable?.let { handler.removeCallbacks(it) }

        val (iconResource, label) = getGestureVisual(gesture, direction)
        val isIdle = iconResource == R.drawable.ic_gesture_idle

        // 更新图标和标签，添加动画效果
        val iconView = overlayIcon ?: return
        val labelView = overlayLabel ?: return

        if (isActionGesture(gesture)) {
            // 静态手势（OK、握拳、张开手掌、剪刀手）的脉冲动画
            iconView.animate()
                .scaleX(0.7f)
                .scaleY(0.7f)
                .alpha(0.5f)
                .setDuration(100)
                .withEndAction {
                    iconView.setImageResource(iconResource)
                    labelView.text = label
                    labelView.alpha = 1f
                    iconView.animate()
                        .scaleX(1.2f)
                        .scaleY(1.2f)
                        .alpha(1f)
                        .setDuration(150)
                        .withEndAction {
                            iconView.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .start()
                        }
                        .start()
                }
                .start()
        } else if (!isIdle) {
            // 方向手势的弹跳动画
            iconView.animate()
                .alpha(0.3f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(80)
                .withEndAction {
                    iconView.setImageResource(iconResource)
                    labelView.text = label
                    labelView.alpha = 1f
                    iconView.animate()
                        .alpha(1f)
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(120)
                        .withEndAction {
                            iconView.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(80)
                                .start()
                        }
                        .start()
                }
                .start()
        } else {
            // 空闲状态，直接切换
            iconView.setImageResource(iconResource)
            labelView.text = ""
            iconView.animate()
                .alpha(0.7f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(200)
                .start()
            labelView.animate()
                .alpha(0f)
                .setDuration(200)
                .start()
        }

        // 自动恢复到空闲状态
        if (!isIdle) {
            resetDirectionRunnable = Runnable {
                updateGestureIcon(null, 0)  // 恢复到空闲状态
            }
            handler.postDelayed(resetDirectionRunnable!!, FEEDBACK_DISPLAY_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            unregisterReceiver(broadcastReceiver)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to unregister receiver", e)
        }

        resetDirectionRunnable?.let { handler.removeCallbacks(it) }

        overlayContainer?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to remove overlay view", e)
            }
        }
        overlayContainer = null
        overlayIcon = null
        overlayLabel = null
        LogUtil.d(TAG, "Overlay service onDestroy")
    }

    companion object {
        private const val TAG = "GestureOverlaySvc"
        private const val ICON_SIZE_DP = 56
        private const val FEEDBACK_DISPLAY_MS = 1000L  // 反馈显示时间，比之前的 800ms 略长
    }
}
