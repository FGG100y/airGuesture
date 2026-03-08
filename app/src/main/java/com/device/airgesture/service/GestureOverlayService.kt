package com.device.airgesture.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import com.device.airgesture.R
import com.device.airgesture.utils.LogUtil

class GestureOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: ImageView? = null

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
        // Fix for Android 13+ BroadcastReceiver registration requirement
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, filter)
        }
        
        showOverlay()
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = ImageView(this).apply {
            setImageResource(R.drawable.ic_gesture_idle)  // 使用新的空闲状态图标
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

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
            windowManager.addView(overlayView, params)
            LogUtil.d(TAG, "Overlay view added")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to add overlay view", e)
        }
    }

    private fun updateGestureIcon(gesture: String?, direction: Int) {
        // 取消之前的重置任务
        resetDirectionRunnable?.let { handler.removeCallbacks(it) }

        // 根据手势类型选择图标
        val iconResource = when {
            gesture == "OK_SIGN" || gesture == "FIST" -> R.drawable.ic_gesture_screenshot
            direction == -1 || gesture == "SWIPE_LEFT" -> R.drawable.ic_gesture_left
            direction == 1 || gesture == "SWIPE_RIGHT" -> R.drawable.ic_gesture_right
            direction == 2 || gesture == "SWIPE_UP" -> R.drawable.ic_gesture_up
            direction == -2 || gesture == "SWIPE_DOWN" -> R.drawable.ic_gesture_down
            else -> R.drawable.ic_gesture_idle
        }

        // 更新图标并添加动画效果
        overlayView?.let { view ->
            // 如果是截图手势，添加特殊的脉冲效果
            if (iconResource == R.drawable.ic_gesture_screenshot) {
                view.animate()
                    .scaleX(0.7f)
                    .scaleY(0.7f)
                    .alpha(0.5f)
                    .setDuration(100)
                    .withEndAction {
                        view.setImageResource(iconResource)
                        view.animate()
                            .scaleX(1.2f)
                            .scaleY(1.2f)
                            .alpha(1f)
                            .setDuration(150)
                            .withEndAction {
                                view.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(100)
                                    .start()
                            }
                            .start()
                    }
                    .start()
            } else if (iconResource != R.drawable.ic_gesture_idle) {
                // 其他手势的动画效果
                view.animate()
                    .alpha(0.3f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(80)
                    .withEndAction {
                        view.setImageResource(iconResource)
                        view.animate()
                            .alpha(1f)
                            .scaleX(1.1f)
                            .scaleY(1.1f)
                            .setDuration(120)
                            .withEndAction {
                                view.animate()
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
                view.setImageResource(iconResource)
                view.animate()
                    .alpha(0.7f)
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(200)
                    .start()
            }
        }

        // 自动恢复到空闲状态
        if (iconResource != R.drawable.ic_gesture_idle) {
            resetDirectionRunnable = Runnable {
                updateGestureIcon(null, 0)  // 恢复到空闲状态
            }
            handler.postDelayed(resetDirectionRunnable!!, 800)  // 延长显示时间到800ms
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
        
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to remove overlay view", e)
            }
        }
        overlayView = null
        LogUtil.d(TAG, "Overlay service onDestroy")
    }

    companion object {
        private const val TAG = "GestureOverlaySvc"
    }
}
