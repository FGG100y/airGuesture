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
            updateHandDirection(direction)
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
            setImageResource(R.drawable.ic_hand_palm)
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

    private fun updateHandDirection(direction: Int) {
        // 取消之前的重置任务
        resetDirectionRunnable?.let { handler.removeCallbacks(it) }

        val rotation = when (direction) {
            -1 -> 90f   // 手势向左，图标指向左
            1  -> -90f  // 手势向右，图标指向右
            2  -> 0f    // 手势向上
            -2 -> 180f  // 手势向下
            else -> 0f  // 静止，居中
        }

        overlayView?.animate()?.rotation(rotation)?.setDuration(150)?.start()

        // 500ms 无新手势则恢复居中
        if (direction != 0) {
            resetDirectionRunnable = Runnable {
                overlayView?.animate()?.rotation(0f)?.setDuration(150)?.start()
            }
            handler.postDelayed(resetDirectionRunnable!!, 500)
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
