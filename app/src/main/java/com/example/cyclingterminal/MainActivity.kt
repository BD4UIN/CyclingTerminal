package com.example.cyclingterminal

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.cyclingterminal.ui.CyclingScreen
import com.example.cyclingterminal.ui.CyclingTerminalTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CyclingViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // 无论授权结果如何都启动；无定位权限时指南针仍可用
        viewModel.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 固定横屏：与 Manifest 里的 screenOrientation 双保险，避免竖屏 bug
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        enableEdgeToEdge()

        // 内容铺满整个屏幕，是否避让系统栏由 Compose 的 safeDrawingPadding 决定
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 骑行途中保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 横屏时允许内容延伸进刘海/挖孔区域
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        hideSystemBars()

        setContent {
            val time by viewModel.time.collectAsState()
            val gps by viewModel.gps.collectAsState()
            val heading by viewModel.heading.collectAsState()
            val rideSeconds by viewModel.rideSeconds.collectAsState()
            val wind by viewModel.wind.collectAsState()
            val brakeAlert by viewModel.brakeAlert.collectAsState()
            val weatherAlert by viewModel.weatherAlert.collectAsState()
            val signalLevel by viewModel.signalLevel.collectAsState()
            val networkKind by viewModel.networkKind.collectAsState()
            val accelData by viewModel.accelData.collectAsState()
            val accelSpeedMps by viewModel.accelSpeedMps.collectAsState()
            val settings by viewModel.settings.collectAsState()

            CyclingTerminalTheme(darkTheme = settings.darkMode) {
                CyclingScreen(
                    time = time,
                    gps = gps,
                    heading = heading,
                    rideSeconds = rideSeconds,
                    wind = wind,
                    brakeAlert = brakeAlert,
                    weatherAlert = weatherAlert,
                    signalLevel = signalLevel,
                    networkKind = networkKind,
                    accelData = accelData,
                    accelSpeedMps = accelSpeedMps,
                    settings = settings,
                    onResetRideTimer = viewModel::resetRideTimer,
                    onUpdateSettings = viewModel::updateSettings,
                    onAcknowledgeWeatherAlert = viewModel::acknowledgeWeatherAlert,
                )
            }
        }

        requestLocationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        // 权限弹窗、通知栏等交互后系统会恢复显示状态栏，这里重新进入全屏
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** 沉浸式全屏：隐藏状态栏与导航栏，从屏幕边缘滑动可临时唤出 */
    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun requestLocationPermissionIfNeeded() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            viewModel.start()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }
}
