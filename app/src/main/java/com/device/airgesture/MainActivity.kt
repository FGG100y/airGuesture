package com.device.airgesture

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.device.airgesture.service.GestureRecognitionService
import com.device.airgesture.utils.LogUtil

class MainActivity : AppCompatActivity() {

    private lateinit var cameraStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var startServiceButton: View
    private lateinit var configButton: View
    private lateinit var hintText: TextView

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        refreshPermissionStatus()
        checkAndStartService()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        refreshPermissionStatus()
        checkAndStartService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        checkAndStartService()
    }

    private fun initViews() {
        cameraStatus = findViewById(R.id.cameraStatus)
        overlayStatus = findViewById(R.id.overlayStatus)
        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        startServiceButton = findViewById(R.id.startServiceButton)
        configButton = findViewById(R.id.configButton)
        hintText = findViewById(R.id.hintText)
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.permissionCamera).setOnClickListener {
            requestCameraPermission()
        }

        findViewById<View>(R.id.permissionOverlay).setOnClickListener {
            requestOverlayPermission()
        }

        findViewById<View>(R.id.permissionAccessibility).setOnClickListener {
            requestAccessibilityPermission()
        }

        startServiceButton.setOnClickListener {
            startGestureService()
        }
        
        configButton.setOnClickListener {
            openGestureConfig()
        }
    }

    private fun refreshPermissionStatus() {
        // 相机权限
        val hasCamera = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        cameraStatus.setTextColor(
            ContextCompat.getColor(this, if (hasCamera) R.color.status_granted else R.color.status_pending)
        )

        // 悬浮窗权限
        val hasOverlay = Settings.canDrawOverlays(this)
        overlayStatus.text = if (hasOverlay) "已授权" else "未授权"
        overlayStatus.setTextColor(
            ContextCompat.getColor(this, if (hasOverlay) R.color.status_granted else R.color.status_pending)
        )

        // 无障碍权限
        val hasAccessibility = isAccessibilityServiceEnabled()
        accessibilityStatus.text = if (hasAccessibility) "已授权" else "未授权"
        accessibilityStatus.setTextColor(
            ContextCompat.getColor(this, if (hasAccessibility) R.color.status_granted else R.color.status_pending)
        )

        // 全部授权后显示启动按钮和配置按钮
        val allGranted = hasCamera && hasOverlay && hasAccessibility
        startServiceButton.visibility = if (allGranted) View.VISIBLE else View.GONE
        configButton.visibility = if (allGranted) View.VISIBLE else View.GONE
        hintText.visibility = if (allGranted) View.GONE else View.VISIBLE
    }

    private fun hasAllPermissions(): Boolean {
        val hasCamera = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAccessibility = isAccessibilityServiceEnabled()
        return hasCamera && hasOverlay && hasAccessibility
    }

    private fun checkAndStartService() {
        // 只刷新状态，不自动启动服务，让用户手动点击启动按钮
        refreshPermissionStatus()
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                refreshPermissionStatus()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionRationale("相机权限", "相机权限用于捕捉您的手势动作") {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                LogUtil.i(TAG, "已跳转到悬浮窗权限设置页面")
            } catch (e: Exception) {
                LogUtil.e(TAG, "无法打开悬浮窗设置页面", e)
                Toast.makeText(this, "无法打开设置页面", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestAccessibilityPermission() {
        if (!isAccessibilityServiceEnabled()) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                LogUtil.i(TAG, "已跳转到无障碍服务设置页面")
            } catch (e: Exception) {
                LogUtil.e(TAG, "无法打开无障碍设置页面", e)
                Toast.makeText(this, "无法打开设置页面", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = ComponentName(
            this,
            com.device.airgesture.service.AirGestureAccessibilityService::class.java
        )
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(serviceName.flattenToString())
    }

    private fun startGestureService() {
        if (!hasAllPermissions()) {
            Toast.makeText(this, "请先获取全部权限", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        LogUtil.d(TAG, "========== 启动手势识别服务 ==========")
        
        val intent = Intent(this, GestureRecognitionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
            LogUtil.d(TAG, "使用 startForegroundService 启动")
        } else {
            startService(intent)
            LogUtil.d(TAG, "使用 startService 启动")
        }
        
        LogUtil.i(TAG, "手势识别服务启动命令已发送")
        
        Toast.makeText(this, "服务已启动，请在通知栏查看", Toast.LENGTH_SHORT).show()
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (GestureRecognitionService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun showPermissionRationale(title: String, message: String, onConfirm: () -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定") { _, _ -> onConfirm() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openGestureConfig() {
        val intent = Intent(this, GestureConfigActivity::class.java)
        startActivity(intent)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
