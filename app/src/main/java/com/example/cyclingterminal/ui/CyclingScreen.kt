package com.example.cyclingterminal.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cyclingterminal.AccelData
import com.example.cyclingterminal.AppSettings
import com.example.cyclingterminal.GpsData
import com.example.cyclingterminal.NetworkKind
import com.example.cyclingterminal.TimeInfo
import com.example.cyclingterminal.WeatherAlert
import com.example.cyclingterminal.WindData
import java.util.Locale
import kotlin.math.min

/** 主界面：左侧时间面板 + 右侧方位/GPS 面板 */
@Composable
fun CyclingScreen(
    time: TimeInfo,
    gps: GpsData,
    heading: Float,
    rideSeconds: Long,
    wind: WindData,
    brakeAlert: Boolean,
    weatherAlert: WeatherAlert?,
    signalLevel: Int,
    networkKind: NetworkKind,
    accelData: AccelData,
    accelSpeedMps: Float,
    settings: AppSettings,
    onResetRideTimer: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onAcknowledgeWeatherAlert: () -> Unit,
) {
    val dark = settings.darkMode
    val bgColors = if (dark) {
        listOf(Color(0xFF241B16), Color(0xFF2E211A), Color(0xFF38291F))
    } else {
        listOf(Color(0xFFFFF6EC), Color(0xFFFFE3C2), Color(0xFFFFD7A8))
    }
    val panelColor = if (dark) Color(0xFF2A201B) else Color(0xFFFDF7F1)
    val panelBorder = if (dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f)

    var showSettings by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(bgColors))
            .padding(safeInsetsCapped())
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Panel(Modifier.weight(0.37f).fillMaxHeight(), panelColor, panelBorder) {
                TimePanel(time, rideSeconds, wind, settings, onResetRideTimer)
            }
            Spacer(Modifier.width(14.dp))
            Panel(Modifier.weight(0.63f).fillMaxHeight(), panelColor, panelBorder) {
                NavigationPanel(
                    gps, heading, wind, signalLevel, networkKind,
                    accelData, accelSpeedMps, settings,
                )
            }
        }

        // 右上角设置入口
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(18.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(panelBorder)
                .border(1.dp, panelBorder, CircleShape)
                .clickable { showSettings = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "⚙",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // 急刹车：占屏幕约 70% 面积的红色告警弹窗
        if (brakeAlert) {
            BrakeAlertOverlay()
        }

        // 天气预警弹窗（有预警时弹出，用户手动确认）
        if (weatherAlert != null) {
            WeatherAlertDialog(weatherAlert, onAcknowledgeWeatherAlert)
        }

        // 设置页
        if (showSettings) {
            SettingsPanel(settings, onUpdateSettings) { showSettings = false }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier, color: Color, borderColor: Color, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .border(width = 1.dp, color = borderColor, shape = shape)
    ) {
        content()
    }
}

/**
 * 系统安全区 inset 的「封顶」版本。
 * 部分机型横屏时会报一个非常大的 displayCutout / 系统栏 inset，
 * 直接 safeDrawingPadding 会把整页内容推到一边（表现为卡片错位）。
 * 这里每一侧最多只取 12dp，保证布局始终左右对称。
 */
@Composable
private fun safeInsetsCapped(maxPerSide: Dp = 12.dp): PaddingValues {
    val pv = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = pv.calculateStartPadding(layoutDirection).coerceAtMost(maxPerSide),
        top = pv.calculateTopPadding().coerceAtMost(maxPerSide),
        end = pv.calculateEndPadding(layoutDirection).coerceAtMost(maxPerSide),
        bottom = pv.calculateBottomPadding().coerceAtMost(maxPerSide),
    )
}

/** 左侧：时间信息 */
@Composable
private fun TimePanel(
    time: TimeInfo,
    rideSeconds: Long,
    wind: WindData,
    settings: AppSettings,
    onResetRideTimer: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "骑行数据终端",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = primary,
            maxLines = 1,
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("当前时间", fontSize = 13.sp, color = onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            FitText(
                text = time.time,
                modifier = Modifier.fillMaxWidth(),
                maxSize = 70.sp,
                minSize = 24.sp,
                emRatio = 0.54f,
                color = onSurface,
                fontFamily = FontFamily.Default,
                fontFeatureSettings = "tnum",
            )
            Spacer(Modifier.height(6.dp))
            Text(
                time.weekDay,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = primary,
                maxLines = 1,
            )
            Text(
                time.date,
                fontSize = 15.sp,
                color = onSurfaceVariant,
                maxLines = 1,
            )
            // 日期下方：当前天气（气温/风向/风力/风速）
            if (settings.windEnabled) {
                Spacer(Modifier.height(8.dp))
                WeatherRow(wind)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("本次骑行时长", fontSize = 13.sp, color = onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.16f))
                        .clickable { onResetRideTimer() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("↺", fontSize = 13.sp, color = primary)
                }
            }
            Spacer(Modifier.height(2.dp))
            FitText(
                text = formatElapsed(rideSeconds),
                modifier = Modifier.fillMaxWidth(),
                maxSize = 30.sp,
                minSize = 14.sp,
                color = onSurface,
            )
        }
    }
}

/** 天气：城市 / 气温·天气现象 / 风向·风力·湿度·风速（和风天气数据） */
@Composable
private fun WeatherRow(wind: WindData) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 城市 + 气温 + 天气现象
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (wind.cityName.isNotEmpty()) {
                Text(
                    wind.cityName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = primary,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                if (wind.tempC.isFinite()) "${wind.tempC.toInt()}°C" else "--",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface,
                maxLines = 1,
            )
            if (wind.condition.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    wind.condition,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = primary,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        // 风向 · 风力 · 湿度 · 风速（合并为一行，各字段已自带单位）
        Text(
            buildString {
                val parts = mutableListOf<String>()
                if (wind.directionText.isNotEmpty()) parts += wind.directionText
                if (wind.windLevel.isNotEmpty()) parts += wind.windLevel
                if (wind.humidity.isNotEmpty()) parts += wind.humidity
                if (wind.speedKmh.isFinite()) {
                    parts += String.format(Locale.US, "%.1f", wind.speedKmh) + " km/h"
                }
                append(if (parts.isEmpty()) "--" else parts.joinToString(" · "))
            },
            fontSize = 13.sp,
            color = onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 右侧导航面板：常规（罗盘+速度+朝向）或纯加速度计模式 */
@Composable
private fun NavigationPanel(
    gps: GpsData,
    heading: Float,
    wind: WindData,
    signalLevel: Int,
    networkKind: NetworkKind,
    accelData: AccelData,
    accelSpeedMps: Float,
    settings: AppSettings,
) {
    if (settings.accelerometerMode) {
        AccelNavigationLayout(accelSpeedMps, accelData)
    } else {
        NormalNavigationLayout(gps, heading, wind, signalLevel, networkKind, settings)
    }
}

/** 常规模式：罗盘（左） + 速度（中，更大） + 朝向（右） */
@Composable
private fun NormalNavigationLayout(
    gps: GpsData,
    heading: Float,
    wind: WindData,
    signalLevel: Int,
    networkKind: NetworkKind,
    settings: AppSettings,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val speedKmh = if (gps.hasFix) gps.speedMps * 3.6f else Float.NaN
    // GPS 有速度且有有效航向时，才是可信的「行进方向」
    val hasCourse = gps.hasFix && gps.speedMps >= 1.0f && gps.bearing.isFinite()
    val windWarning = settings.windEnabled && wind.speedKmh.isFinite() &&
        wind.speedKmh >= settings.windThreshold
    // 有雨时显示蓝色降雨胶囊（和风天气 precipType == "rain"）
    val isRaining = settings.windEnabled && wind.precipType == "rain"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val compassSize = min(maxWidth.value * 0.38f, maxHeight.value * 0.98f).dp
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(compassSize)) {
                    CompassRose(heading = heading, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (windWarning || isRaining) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (windWarning) WindWarningCapsule()
                            if (isRaining) RainCapsule(wind.precipAmount)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Text("速度", fontSize = 13.sp, color = onSurfaceVariant)
                    FitText(
                        text = if (speedKmh.isFinite()) {
                            String.format(Locale.US, "%.1f", speedKmh)
                        } else {
                            "--"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxSize = 96.sp,
                        minSize = 28.sp,
                        color = onSurface,
                    )
                    Text("km/h", fontSize = 14.sp, color = onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))

                Column(
                    modifier = Modifier
                        .weight(0.7f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("朝向", fontSize = 13.sp, color = onSurfaceVariant)
                    Text(
                        "${heading.toInt()}°",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = onSurface,
                    )
                    Text(
                        bearingToCardinal(heading),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            InfoCard(
                modifier = Modifier.weight(1f),
                label = "行进方向",
                value = if (hasCourse) "${gps.bearing.toInt()}°" else "—",
                extra = if (hasCourse) bearingToCardinal(gps.bearing) else "",
            )
            InfoCard(
                modifier = Modifier.weight(1f),
                label = "卫星",
                value = if (gps.satellites > 0) "${gps.satellites}" else "—",
                extra = if (gps.satellites > 0) {
                    if (gps.beidouSatellites > 0) "颗·北斗${gps.beidouSatellites}" else "颗"
                } else "",
            )
            InfoCard(
                modifier = Modifier.weight(1f),
                label = "精度",
                value = if (gps.hasFix) String.format(Locale.US, "%.0f", gps.accuracy) else "—",
                extra = if (gps.hasFix) "米" else "",
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GpsStatusBadge(hasFix = gps.hasFix)
            if (settings.signalEnabled) {
                SignalIndicator(level = signalLevel, networkKind = networkKind)
            }
        }
    }
}

/** 纯加速度计模式：速度占绝大部分，底部小行显示加速度计数据，卫星/信号灰化 */
@Composable
private fun AccelNavigationLayout(accelSpeedMps: Float, accelData: AccelData) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val speedKmh = accelSpeedMps * 3.6f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 红色胶囊提示
        Row(
            modifier = Modifier
                .background(Color(0xFFD32F2F), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⚠", fontSize = 14.sp, color = Color.White)
            Spacer(Modifier.width(5.dp))
            Text(
                "当前在纯加速度计模式！",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(8.dp))

        // 速度占据绝大部分
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("速度（加速度计估算）", fontSize = 13.sp, color = onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            FitText(
                text = if (speedKmh.isFinite()) {
                    String.format(Locale.US, "%.1f", speedKmh)
                } else "--",
                modifier = Modifier.fillMaxWidth(),
                maxSize = 130.sp,
                minSize = 40.sp,
                color = onSurface,
            )
            Text("km/h", fontSize = 15.sp, color = onSurfaceVariant)
        }

        Spacer(Modifier.height(8.dp))

        // 底部小行：各加速度计数据
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AccelValue("X", accelData.x)
            AccelValue("Y", accelData.y)
            AccelValue("Z", accelData.z)
            AccelValue("|a|", accelData.magnitude)
        }

        Spacer(Modifier.height(8.dp))

        // 灰化的卫星 / 移动数据（已禁用）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GpsStatusBadge(hasFix = false, disabled = true, label = "卫星定位已禁用")
            DisabledTag("移动数据已禁用")
        }
    }
}

@Composable
private fun AccelValue(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            String.format(Locale.US, "%.2f", value),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun DisabledTag(text: String) {
    Row(
        modifier = Modifier
            .background(Color(0xFFBDB3A8).copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 风速过大提醒：黄色胶囊 */
@Composable
private fun WindWarningCapsule() {
    Row(
        modifier = Modifier
            .background(Color(0xFFFFD54F), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚠", fontSize = 14.sp, color = Color(0xFF5D4037))
        Spacer(Modifier.width(4.dp))
        Text(
            "风速过大",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF5D4037),
            maxLines = 1,
        )
    }
}

/** 降雨提醒：蓝色胶囊（仅在有雨时显示，展示雨量） */
@Composable
private fun RainCapsule(amount: Float) {
    Row(
        modifier = Modifier
            .background(Color(0xFF4FA3E3), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("☔", fontSize = 14.sp, color = Color.White)
        Spacer(Modifier.width(4.dp))
        Text(
            if (amount.isFinite()) {
                String.format(Locale.US, "降雨 %.1f mm", amount)
            } else {
                "有雨"
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/** 移动数据信号强度 / 联网状态：真实反映断网 */
@Composable
private fun SignalIndicator(level: Int, networkKind: NetworkKind) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    when (networkKind) {
        NetworkKind.OFFLINE -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("无网络", fontSize = 12.sp, color = onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (i in 0 until 4) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .height((6 + i * 3).dp)
                                .background(Color(0xFFD7C9B8), RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        }

        NetworkKind.WIFI -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("WiFi", fontSize = 12.sp, color = onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("已连接", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF3A7D44))
            }
        }

        NetworkKind.CELLULAR -> {
            val active = when {
                level >= 3 -> Color(0xFF3A7D44)
                level >= 1 -> Color(0xFFC05621)
                else -> onSurfaceVariant
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("信号", fontSize = 12.sp, color = onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (i in 0 until 4) {
                        val filled = i < level
                        Box(
                            Modifier
                                .width(4.dp)
                                .height((6 + i * 3).dp)
                                .background(
                                    if (filled) active else Color(0xFFD7C9B8),
                                    RoundedCornerShape(1.dp),
                                )
                        )
                    }
                }
            }
        }
    }
}

/** 急刹车红色告警弹窗（占屏幕约 70% 面积） */
@Composable
private fun BrakeAlertOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .fillMaxHeight(0.84f)
                .background(Color(0xFFD32F2F), RoundedCornerShape(32.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("⚠", fontSize = 90.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "急刹车！",
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "请注意安全",
                fontSize = 26.sp,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
    }
}

/** 天气预警弹窗：标题=预警类型+级别，内容=气象局原文，用户手动确认 */
@Composable
private fun WeatherAlertDialog(alert: WeatherAlert, onAcknowledge: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(28.dp))
                .background(surface)
                .padding(24.dp),
        ) {
            // 标题：预警图标 + headline（含类型+级别）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠️", fontSize = 32.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        alert.headline.ifEmpty { "气象预警" },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = primary,
                        maxLines = 2,
                    )
                    val sub = buildString {
                        if (alert.severityLabel.isNotEmpty()) append(alert.severityLabel)
                        if (alert.senderName.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append(alert.senderName)
                        }
                    }
                    if (sub.isNotEmpty()) {
                        Text(sub, fontSize = 13.sp, color = onSurfaceVariant, maxLines = 1)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // 预警原文（可滚动）
            Text("预警信息", fontSize = 13.sp, color = onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    alert.description.ifEmpty { "（暂无详细内容）" },
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = onSurface,
                )
                if (alert.instruction.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "防御指引",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        alert.instruction,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 手动确认按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(primary)
                    .clickable { onAcknowledge() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "我知道了",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    extra: String = "",
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (extra.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(
                    extra,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun GpsStatusBadge(
    hasFix: Boolean,
    disabled: Boolean = false,
    label: String? = null,
) {
    val color = if (disabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else if (hasFix) {
        Color(0xFF3A7D44)
    } else {
        Color(0xFFC05621)
    }
    val transition = rememberInfiniteTransition()
    val scale by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = if (disabled) 1.0f else 1.5f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label ?: if (hasFix) "GPS 已定位" else "正在搜索卫星…",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * 单行自适应文字：根据可用宽度与系统字体缩放比例实时计算字号，
 * 保证「时间 / 速度」这类大数字永远完整显示，绝不换行或溢出裁切。
 */
@Composable
private fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    maxSize: TextUnit,
    minSize: TextUnit = 12.sp,
    emRatio: Float = 0.62f,
    color: Color = Color.Unspecified,
    fontFamily: FontFamily? = FontFamily.Monospace,
    fontFeatureSettings: String? = null,
) {
    BoxWithConstraints(modifier) {
        val fontScale = LocalDensity.current.fontScale.takeIf { it > 0f } ?: 1f
        val chars = text.length.coerceAtLeast(1)
        val ideal = maxWidth.value / (chars * emRatio) / fontScale
        val size = ideal.coerceIn(minSize.value, maxSize.value)
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds(),
            style = TextStyle(
                fontSize = size.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                fontFeatureSettings = fontFeatureSettings,
                color = color,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/** 设置面板（覆盖层） */
@Composable
private fun SettingsPanel(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onClose: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("设置", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = primary)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", fontSize = 20.sp, color = onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))

            SettingSwitch(
                "急刹车提醒",
                "触发时红色弹窗 + 强烈震动",
                settings.brakeAlertEnabled,
            ) { onUpdateSettings { it.copy(brakeAlertEnabled = !it.brakeAlertEnabled) } }

            SettingSwitch(
                "风速风向显示",
                "日期下方显示当前风速与风向",
                settings.windEnabled,
            ) { onUpdateSettings { it.copy(windEnabled = !it.windEnabled) } }

            SettingSwitch(
                "移动信号显示",
                "状态栏显示真实联网状态",
                settings.signalEnabled,
            ) { onUpdateSettings { it.copy(signalEnabled = !it.signalEnabled) } }

            SettingSwitch(
                "深色模式（黑夜）",
                "切换暖色深色主题",
                settings.darkMode,
            ) { onUpdateSettings { it.copy(darkMode = !it.darkMode) } }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SettingSlider(
                "急刹车加速度阈值",
                String.format(Locale.US, "%.1f m/s²", settings.brakeThreshold),
                settings.brakeThreshold,
                2f..8f,
            ) { v -> onUpdateSettings { it.copy(brakeThreshold = v) } }

            Spacer(Modifier.height(12.dp))

            SettingSlider(
                "风速提醒阈值",
                String.format(Locale.US, "%.0f km/h", settings.windThreshold),
                settings.windThreshold,
                5f..50f,
            ) { v -> onUpdateSettings { it.copy(windThreshold = v) } }

            Spacer(Modifier.height(12.dp))

            SettingSlider(
                "罗盘手动校准",
                String.format(Locale.US, "%+.0f°", settings.compassOffset),
                settings.compassOffset,
                -180f..180f,
            ) { v -> onUpdateSettings { it.copy(compassOffset = v) } }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // 天气城市设置
            Text(
                "天气城市",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "手动输入城市名，留空则用 GPS 自动定位城市。",
                fontSize = 12.sp,
                color = onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            CityNameField(settings.cityName) { name ->
                onUpdateSettings { it.copy(cityName = name) }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // 实验性功能
            Text(
                "实验性功能",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD32F2F),
            )
            Text(
                "以下功能极其不稳定，可能导致速度严重漂移，请谨慎使用。",
                fontSize = 12.sp,
                color = onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SettingSwitch(
                "纯加速度计测量",
                "开启后禁用卫星与移动数据，速度由加速度积分估算",
                settings.accelerometerMode,
            ) { onUpdateSettings { it.copy(accelerometerMode = !it.accelerometerMode) } }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingSlider(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                valueText,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
        )
    }
}

/** 城市名输入框 + 「自动定位」按钮 */
@Composable
private fun CityNameField(cityName: String, onCityName: (String) -> Unit) {
    var text by remember(cityName) { mutableStateOf(cityName) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onCityName(it)
            },
            modifier = Modifier.weight(1f),
            placeholder = { Text("如：北京", fontSize = 14.sp) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = {
            text = ""
            onCityName("")
        }) {
            Text("自动定位")
        }
    }
}

/** 度数 → 八方位中文 */
fun bearingToCardinal(deg: Float): String {
    val dirs = arrayOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")
    val normalized = ((deg % 360f) + 360f) % 360f
    val idx = ((normalized + 22.5f) / 45f).toInt() % 8
    return dirs[idx]
}

/** 秒数 → HH:mm:ss */
fun formatElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}
