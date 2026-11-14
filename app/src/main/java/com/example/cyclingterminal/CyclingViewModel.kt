package com.example.cyclingterminal

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 时间信息 */
data class TimeInfo(
    val time: String = "",
    val date: String = "",
    val weekDay: String = "",
)

/** GPS 定位数据；bearing 在无有效航向时为 NaN，避免把 0° 当成真实方向 */
data class GpsData(
    val hasFix: Boolean = false,
    val speedMps: Float = 0f,
    val bearing: Float = Float.NaN,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val satellites: Int = 0,
    val beidouSatellites: Int = 0,
    val altitude: Double = 0.0,
)

/** 风速/风向/天气数据；无数据时为 NaN 或空串 */
data class WindData(
    val speedKmh: Float = Float.NaN,
    val directionDeg: Float = Float.NaN,
    /** 风向中文（中国天气网，如「东南风」） */
    val directionText: String = "",
    /** 风力等级（如「1级」） */
    val windLevel: String = "",
    /** 气温（℃） */
    val tempC: Float = Float.NaN,
    /** 湿度（如「17%」） */
    val humidity: String = "",
    /** 城市名 */
    val cityName: String = "",
    /** 天气现象（和风天气，如「晴」「多云」） */
    val condition: String = "",
    /** 降水类型（和风天气：none/rain/snow/hail），用于蓝色降雨胶囊 */
    val precipType: String = "",
    /** 降水量（mm） */
    val precipAmount: Float = Float.NaN,
)

/** 天气预警信息（和风天气 /weatheralert，仅在有预警时非空） */
data class WeatherAlert(
    val id: String = "",
    /** 官方标题（已含预警类型+级别，如「大风橙色预警」） */
    val headline: String = "",
    /** 预警类型（和风 eventType.name） */
    val eventType: String = "",
    /** 预警级别中文（如「橙色预警」） */
    val severityLabel: String = "",
    /** 气象局发布原文 */
    val description: String = "",
    /** 防御指引 */
    val instruction: String = "",
    /** 发布机构 */
    val senderName: String = "",
)

/** 线性加速度计原始数据（实验性模式底部展示） */
data class AccelData(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val magnitude: Float = 0f,
)

class CyclingViewModel(application: Application) : AndroidViewModel(application) {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
    private val weekFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)

    private val _time = MutableStateFlow(currentTime())
    val time: StateFlow<TimeInfo> = _time

    private val _gps = MutableStateFlow(GpsData())
    val gps: StateFlow<GpsData> = _gps

    /** 屏幕朝向角（0°=正北，顺时针增加，已含横屏 +90° 补偿） */
    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading

    private val _rideSeconds = MutableStateFlow(0L)
    val rideSeconds: StateFlow<Long> = _rideSeconds

    /** 当前风速/风向（每分钟从网络更新一次） */
    private val _wind = MutableStateFlow(WindData())
    val wind: StateFlow<WindData> = _wind

    /** 当前天气预警（无预警时为 null；用户确认后清空） */
    private val _weatherAlert = MutableStateFlow<WeatherAlert?>(null)
    val weatherAlert: StateFlow<WeatherAlert?> = _weatherAlert

    /** 急刹车弹窗是否显示（触发后 3 秒自动关闭） */
    private val _brakeAlert = MutableStateFlow(false)
    val brakeAlert: StateFlow<Boolean> = _brakeAlert

    /** 移动数据信号强度等级（0~4） */
    private val _signalLevel = MutableStateFlow(0)
    val signalLevel: StateFlow<Int> = _signalLevel

    /** 当前网络类型（断网时为 OFFLINE，用于真实反映联网状态） */
    private val _networkKind = MutableStateFlow(NetworkKind.OFFLINE)
    val networkKind: StateFlow<NetworkKind> = _networkKind

    /** 纯加速度计模式：原始加速度数据 */
    private val _accelData = MutableStateFlow(AccelData())
    val accelData: StateFlow<AccelData> = _accelData

    /** 纯加速度计模式：积分得到的速度（m/s，实验性，漂移严重） */
    private val _accelSpeedMps = MutableStateFlow(0f)
    val accelSpeedMps: StateFlow<Float> = _accelSpeedMps

    /** 应用设置（持久化） */
    private val prefs = getApplication<Application>()
        .getSharedPreferences("cycling_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings

    private val locationManager =
        getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val sensorManager =
        getApplication<Application>().getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val connectivityManager =
        getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var started = false

    /** 纯加速度计模式是否当前激活 */
    private var accelModeActive = false

    /** 定位监听是否已注册（幂等开关，防止重复注册/注销） */
    private var locationActive = false

    private val rotationMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)
    private var sensorAzimuth = 0f

    /** 最后一次定位更新的开机时刻，用于判断信号是否已经丢失 */
    private var lastFixElapsed = 0L
    private var smoothedSpeed = 0f

    /** GPS 与网络定位各自的最近一次结果，用于择优融合 */
    private var lastGpsFix: Location? = null
    private var lastNetworkFix: Location? = null

    /** 最近一次已知坐标（用于风速拉取，即使短暂失去定位也能用） */
    private var lastKnownLat = 0.0
    private var lastKnownLon = 0.0

    // ---- 急刹车 / 静止识别状态 ----
    private var consecutiveBrakeSamples = 0
    private var lastBrakeAt = 0L
    private var stillSince: Long? = null

    // ---- 天气网络更新（中国天气网） ----
    private var lastWindFetchAt = 0L
    /** 已解析的城市编号（缓存，避免每分钟重复搜索） */
    private var resolvedCityId = ""
    private var resolvedCityName = ""
    /** 已确认过的预警 id，避免同一条预警重复弹窗 */
    private var acknowledgedAlertId = ""

    /** 和风天气 API Host（由 local.properties 经 BuildConfig 注入） */
    private val qweatherHost: String get() = BuildConfig.QWEATHER_HOST

    /** 和风天气 API Key（由 local.properties 经 BuildConfig 注入） */
    private val qweatherKey: String get() = BuildConfig.QWEATHER_KEY

    // ---- 纯加速度计速度积分状态 ----
    private var accelSpeedRaw = 0f
    private var lastAccelTime = 0L

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastFixElapsed = SystemClock.elapsedRealtime()
            if (location.provider == LocationManager.GPS_PROVIDER) {
                lastGpsFix = location
            } else {
                lastNetworkFix = location
            }
            mergeAndPublish()
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var total = 0
            var beidou = 0
            for (i in 0 until status.satelliteCount) {
                total++
                if (status.getConstellationType(i) == GnssStatus.CONSTELLATION_BEIDOU) {
                    beidou++
                }
            }
            _gps.value = _gps.value.copy(satellites = total, beidouSatellites = beidou)
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationValues)
                    val azimuth = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                    // 手机横屏向前固定安装，传感器的“自然顶部”与车头方向相差 90°，
                    // 故顺时针补偿 90° 才是真实朝向；再叠加用户手动校准角（罗盘偏差）
                    sensorAzimuth = (azimuth + LANDSCAPE_OFFSET_DEGREES +
                        _settings.value.compassOffset + 360f) % 360f
                    // GPS 无有效航向时，由传感器驱动朝向
                    val course = _gps.value.bearing
                    if (!course.isFinite() || _gps.value.speedMps < MIN_COURSE_SPEED) {
                        _heading.value = sensorAzimuth
                    }
                }

                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val mag = sqrt(x * x + y * y + z * z)
                    val now = SystemClock.elapsedRealtime()

                    // 始终记录原始加速度数据（供实验性模式展示）
                    _accelData.value = AccelData(x = x, y = y, z = z, magnitude = mag)

                    // 纯加速度计模式：对“前进轴”做积分估算速度（极其不稳定，会漂移）
                    if (accelModeActive) {
                        integrateAccelSpeed(x, now)
                    }

                    // 急刹车：持续的高线性加速度（减速度）脉冲 + 当时确实在骑行
                    val threshold = _settings.value.brakeThreshold
                    if (mag > threshold) {
                        consecutiveBrakeSamples++
                    } else {
                        consecutiveBrakeSamples = 0
                    }
                    if (_settings.value.brakeAlertEnabled &&
                        consecutiveBrakeSamples >= BRAKE_SAMPLES_REQUIRED &&
                        smoothedSpeed >= BRAKE_MIN_SPEED &&
                        now - lastBrakeAt > BRAKE_COOLDOWN_MS
                    ) {
                        lastBrakeAt = now
                        consecutiveBrakeSamples = 0
                        triggerBrakeAlert()
                    }

                    // 静止：加速度近乎为零，用于后续修正 GPS 低速漂移
                    if (mag < STILL_ACCEL_THRESHOLD) {
                        if (stillSince == null) stillSince = now
                    } else {
                        stillSince = null
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /** 纯加速度计模式：对前进轴（横屏时 X 轴≈车头方向）积分估算速度（实验性，会漂移） */
    private fun integrateAccelSpeed(forward: Float, now: Long) {
        if (lastAccelTime == 0L) {
            lastAccelTime = now
            return
        }
        val dt = (now - lastAccelTime) / 1000f
        lastAccelTime = now
        if (dt <= 0f || dt > 1f) return

        // 匀速骑行时前进轴线性加速度接近 0，此时应“保持”当前速度而不是衰减；
        // 只有出现明显加减速（|forward| 超过死区）才做积分，否则速度保持恒定。
        if (abs(forward) >= ACCEL_DEAD_ZONE) {
            accelSpeedRaw += forward * dt
        }

        // 负值归零；速度很小且无有效加速度时判定为静止，直接归零（不再指数衰减）
        if (accelSpeedRaw < 0f) accelSpeedRaw = 0f
        if (accelSpeedRaw < ACCEL_STOP_SPEED && abs(forward) < ACCEL_DEAD_ZONE) {
            accelSpeedRaw = 0f
        }
        _accelSpeedMps.value = accelSpeedRaw
    }

    // ---- 移动数据信号强度监听 ----
    private val legacySignalListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            _signalLevel.value = signalStrength.level
        }
    }

    private val telephonyCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                var bestLevel = 0
                for (cell in signalStrength.cellSignalStrengths) {
                    if (cell.level > bestLevel) bestLevel = cell.level
                }
                _signalLevel.value = bestLevel
            }
        }
    } else {
        null
    }

    // ---- 网络连通性监听（用于真实反映“断网”） ----
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            _networkKind.value = kindOf(caps)
        }

        override fun onLost(network: Network) {
            _networkKind.value = NetworkKind.OFFLINE
        }

        override fun onUnavailable() {
            _networkKind.value = NetworkKind.OFFLINE
        }
    }

    private fun kindOf(caps: NetworkCapabilities): NetworkKind {
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkKind.OFFLINE
        }
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.CELLULAR
            else -> NetworkKind.OFFLINE
        }
    }

    init {
        // 每秒刷新时钟与骑行计时，检测定位是否失联，做静止速度修正，并按需拉风速
        viewModelScope.launch {
            while (true) {
                _time.value = currentTime()
                _rideSeconds.value += 1
                checkFixFreshness()
                applyStillCorrection()
                maybeFetchWeather()
                delay(1000L)
            }
        }
    }

    /** 融合 GPS + 网络定位：位置择优，速度/航向以 GPS 为准 */
    private fun mergeAndPublish() {
        val gps = lastGpsFix
        val net = lastNetworkFix

        // 位置：GPS 与网络定位中精度更好者；GPS 缺失时用网络定位快速获得粗定位
        val best: Location? = when {
            gps == null -> net
            net == null -> gps
            gps.accuracy <= net.accuracy -> gps
            else -> net
        }
        if (best == null) return

        lastKnownLat = best.latitude
        lastKnownLon = best.longitude

        // 速度：只信 GPS（网络定位通常不含有效速度），轻度平滑 + 更快响应
        val rawSpeed = gps?.takeIf {
            it.hasSpeed() && it.speed.isFinite() && it.speed >= 0f
        }?.speed
        if (rawSpeed != null) {
            smoothedSpeed = smoothedSpeed * SPEED_SMOOTH_OLD + rawSpeed * SPEED_SMOOTH_NEW
            if (rawSpeed < 0.05f) smoothedSpeed = 0f
        } else if (gps == null) {
            smoothedSpeed = 0f
        }

        // 航向：只有真正在移动且带航向时，GPS 航向才可信
        val gpsCourse = if (gps != null && gps.hasBearing() && smoothedSpeed >= MIN_COURSE_SPEED) {
            ((gps.bearing % 360f) + 360f) % 360f
        } else {
            Float.NaN
        }

        _gps.value = GpsData(
            hasFix = true,
            speedMps = smoothedSpeed,
            bearing = gpsCourse,
            latitude = best.latitude,
            longitude = best.longitude,
            accuracy = if (best.hasAccuracy()) best.accuracy else 0f,
            satellites = _gps.value.satellites,
            beidouSatellites = _gps.value.beidouSatellites,
            altitude = if (best.hasAltitude()) best.altitude else 0.0,
        )

        if (gpsCourse.isFinite()) {
            _heading.value = gpsCourse
        } else {
            _heading.value = sensorAzimuth
        }
    }

    /** 超过 8 秒没有新的定位更新，视为暂时丢失信号 */
    private fun checkFixFreshness() {
        val current = _gps.value
        if (!current.hasFix) return
        val silentMs = SystemClock.elapsedRealtime() - lastFixElapsed
        if (silentMs > FIX_TIMEOUT_MS) {
            smoothedSpeed = 0f
            lastGpsFix = null
            lastNetworkFix = null
            _gps.value = current.copy(
                hasFix = false,
                speedMps = 0f,
                bearing = Float.NaN,
                satellites = 0,
                beidouSatellites = 0,
            )
            _heading.value = sensorAzimuth
        }
    }

    /** 静止一段时间时，把 GPS 低速漂移修正为 0 */
    private fun applyStillCorrection() {
        val since = stillSince ?: return
        if (SystemClock.elapsedRealtime() - since > STILL_CONFIRM_MS &&
            smoothedSpeed > 0.01f &&
            smoothedSpeed < STILL_SPEED_MAX
        ) {
            smoothedSpeed = 0f
            _gps.value = _gps.value.copy(speedMps = 0f)
        }
    }

    /** 触发急刹车告警：红色弹窗 + 强烈震动，3 秒后自动关闭 */
    private fun triggerBrakeAlert() {
        _brakeAlert.value = true
        vibrateStrongly()
        viewModelScope.launch {
            delay(BRAKE_ALERT_DURATION_MS)
            _brakeAlert.value = false
        }
    }

    private fun vibrateStrongly() {
        val vibrator = getApplication<Application>()
            .getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vibrator.hasVibrator()) return
        val pattern = longArrayOf(0, 400, 150, 400, 150, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    /** 拉取当前天气（和风天气 QWeather 2026 新 API，含天气现象/气温/风向/风力/风速/湿度） */
    private suspend fun fetchWeather() = withContext(Dispatchers.IO) {
        val s = _settings.value
        if (!s.windEnabled) return@withContext

        // 1. 解析城市 → 经纬度（手动城市名用名字查；留空则用坐标反查）
        val cityName = s.cityName.trim()
        val (lat, lon, resolvedName) = resolveQWeatherCity(cityName, lastKnownLat, lastKnownLon)
        if (lat == 0.0 && lon == 0.0) return@withContext

        // 2. 拉实况天气（新 API：/weather/v1/current/{lat}/{lon}）
        val url = "https://$qweatherHost/weather/v1/current/$lat/$lon?key=$qweatherKey"
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 CyclingTerminal/1.9")
            val code = conn.responseCode
            if (code in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                val condition = root.optJSONObject("condition")?.optString("text", "") ?: ""
                val temp = root.optJSONObject("temperature")?.optDouble("value", Double.NaN)?.toFloat()
                    ?: Float.NaN
                // humidity 为 0~1 小数（0.2 表示 20%）
                val humidityFrac = root.optDouble("humidity", Double.NaN)
                val windObj = root.optJSONObject("wind")
                val windDirDeg = windObj?.optJSONObject("direction")?.optDouble("degree", Double.NaN)?.toFloat()
                    ?: Float.NaN
                // speed.value 单位 m/s，需 ×3.6 转 km/h
                val windSpeedMs = windObj?.optJSONObject("speed")?.optDouble("value", Double.NaN)?.toFloat()
                    ?: Float.NaN
                val windScale = windObj?.optInt("scale", -1) ?: -1
                // 降水：type(none/rain/snow/hail) + amount.value(mm)
                val precipObj = root.optJSONObject("precipitation")
                val precipType = precipObj?.optString("type", "") ?: ""
                val precipAmount = precipObj?.optJSONObject("amount")
                    ?.optDouble("value", Double.NaN)?.toFloat() ?: Float.NaN
                _wind.value = WindData(
                    speedKmh = if (windSpeedMs.isFinite()) windSpeedMs * 3.6f else Float.NaN,
                    directionDeg = if (windDirDeg.isFinite()) windDirDeg else Float.NaN,
                    directionText = if (windDirDeg.isFinite()) degreeToCompassText(windDirDeg) else "",
                    windLevel = if (windScale >= 0) "${windScale}级" else "",
                    tempC = if (temp.isFinite()) temp else Float.NaN,
                    humidity = if (humidityFrac.isFinite()) "${(humidityFrac * 100).roundToInt()}%" else "",
                    cityName = resolvedName.ifEmpty { cityName },
                    condition = condition,
                    precipType = precipType,
                    precipAmount = if (precipAmount.isFinite()) precipAmount else Float.NaN,
                )
                // 天气预警（同经纬度，独立接口）
                fetchWeatherAlert(lat, lon)
            }
        } catch (_: Exception) {
            // 网络失败则保留上一次数据
        }
    }

    /** 拉取天气预警（和风 /weatheralert/v1/current/{lat}/{lon}），有预警则发布、无则清空 */
    private fun fetchWeatherAlert(lat: Double, lon: Double) {
        try {
            val url = "https://$qweatherHost/weatheralert/v1/current/$lat/$lon?key=$qweatherKey&lang=zh"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 CyclingTerminal/1.9")
            val code = conn.responseCode
            if (code !in 200..299) return
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val alerts = root.optJSONArray("alerts")
            if (alerts == null || alerts.length() == 0) {
                _weatherAlert.value = null
                return
            }
            val a = alerts.optJSONObject(0) ?: return
            val id = a.optString("id", "")
            // 同一条预警已确认过，不再重复弹
            if (id.isNotEmpty() && id == acknowledgedAlertId) {
                _weatherAlert.value = null
                return
            }
            _weatherAlert.value = WeatherAlert(
                id = id,
                headline = a.optString("headline", ""),
                eventType = a.optJSONObject("eventType")?.optString("name", "") ?: "",
                severityLabel = severityLabel(
                    a.optJSONObject("color")?.optString("code", "") ?: "",
                    a.optString("severity", ""),
                ),
                description = a.optString("description", ""),
                instruction = a.optString("instruction", ""),
                senderName = a.optString("senderName", ""),
            )
        } catch (_: Exception) {
        }
    }

    /** 预警颜色/级别码 → 中文级别（如「橙色预警」） */
    private fun severityLabel(colorCode: String, severity: String): String {
        val level = when (colorCode.lowercase()) {
            "blue" -> "蓝色"
            "yellow", "amber" -> "黄色"
            "orange" -> "橙色"
            "red" -> "红色"
            else -> when (severity.lowercase()) {
                "minor" -> "蓝色"
                "moderate" -> "黄色"
                "severe" -> "橙色"
                "extreme" -> "红色"
                else -> ""
            }
        }
        return if (level.isEmpty()) "" else "${level}预警"
    }

    /** 城市名或经纬度 → 和风天气经纬度 + 显示城市名（新 GeoAPI /geo/v2/city/lookup） */
    private fun resolveQWeatherCity(cityName: String, lat: Double, lon: Double): Triple<Double, Double, String> {
        val isManual = cityName.isNotEmpty()
        val query = when {
            isManual -> URLEncoder.encode(cityName, "UTF-8")
            lat != 0.0 || lon != 0.0 -> "$lon,$lat"
            else -> return Triple(0.0, 0.0, "")
        }
        return try {
            val url = URL("https://$qweatherHost/geo/v2/city/lookup?location=$query&range=cn&key=$qweatherKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            if (root.optString("code") != "200") return Triple(0.0, 0.0, "")
            val obj = root.optJSONArray("location")?.optJSONObject(0) ?: return Triple(0.0, 0.0, "")
            val rLat = obj.optDouble("lat", 0.0)
            val rLon = obj.optDouble("lon", 0.0)
            // 手动查城市用 name（市/区名）；自动定位用 adm2（市级）更友好
            val name = if (isManual) obj.optString("name")
                       else obj.optString("adm2").ifEmpty { obj.optString("adm1").ifEmpty { obj.optString("name") } }
            Triple(rLat, rLon, name)
        } catch (_: Exception) {
            Triple(0.0, 0.0, "")
        }
    }

    /** 用最近已知坐标反查城市名（Android 内置 Geocoder，无需额外 key） */
    private fun reverseGeocodeCity(lat: Double, lon: Double): String {
        if (lat == 0.0 && lon == 0.0) return ""
        return try {
            val geocoder = Geocoder(getApplication(), Locale.CHINA)
            val addr = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
            addr?.locality?.takeIf { it.isNotEmpty() }
                ?: addr?.subAdminArea?.takeIf { it.isNotEmpty() }
                ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    /** 城市名 → 中国天气网城市编号（toy1 搜索接口） */
    private fun searchCityId(name: String): String {
        return try {
            val encoded = URLEncoder.encode(name, "UTF-8")
            val url = URL("https://toy1.weather.com.cn/search?cityname=$encoded")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            // 形如 ([{"ref":"101010100~beijing~北京~Beijing~..."}])
            val idx = text.indexOf("\"ref\":\"")
            if (idx < 0) return ""
            val start = idx + "\"ref\":\"".length
            val end = text.indexOf('~', start)
            if (end <= start) return ""
            text.substring(start, end)
        } catch (_: Exception) {
            ""
        }
    }

    /** 风向角度 → 八方位中文（如 313° → 西北风） */
    private fun degreeToCompassText(deg: Float): String {
        val d = ((deg % 360f) + 360f) % 360f
        val idx = ((d + 22.5f) / 45f).toInt() % 8
        return arrayOf("北风", "东北风", "东风", "东南风", "南风", "西南风", "西风", "西北风")[idx]
    }

    private fun maybeFetchWeather() {
        val s = _settings.value
        if (!s.windEnabled) return
        // 有手动城市名时无需坐标；否则需要定位坐标来反查城市
        val hasCity = s.cityName.isNotBlank()
        if (!hasCity && lastKnownLat == 0.0 && lastKnownLon == 0.0) return
        if (SystemClock.elapsedRealtime() - lastWindFetchAt >= WIND_INTERVAL_MS) {
            lastWindFetchAt = SystemClock.elapsedRealtime()
            viewModelScope.launch { fetchWeather() }
        }
    }

    /** 幂等启动：注册传感器与定位监听 */
    fun start() {
        if (started) return
        started = true
        startSensors()
        startConnectivityMonitoring()
        applyMode(_settings.value)
    }

    /** 重置本次骑行时间 */
    fun resetRideTimer() {
        _rideSeconds.value = 0L
    }

    /** 用户确认天气预警弹窗：记录已确认 id 并关闭弹窗 */
    fun acknowledgeWeatherAlert() {
        _weatherAlert.value?.let { acknowledgedAlertId = it.id }
        _weatherAlert.value = null
    }

    /** 更新设置（自动持久化并应用模式切换） */
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val old = _settings.value
        val new = transform(old)
        _settings.value = new
        saveSettings(new)
        applyMode(new)
        // 城市名或天气开关变化时，立即重置拉取计时，让下次 tick 立刻重新拉天气
        if (new.cityName != old.cityName || new.windEnabled != old.windEnabled) {
            lastWindFetchAt = 0L
        }
    }

    /** 应用设置：主要处理纯加速度计模式的开关（禁用/恢复卫星与移动数据） */
    private fun applyMode(s: AppSettings) {
        val accelOn = s.accelerometerMode
        if (accelOn && !accelModeActive) {
            accelModeActive = true
            stopLocation()
            stopSignalMonitoring()
            accelSpeedRaw = 0f
            lastAccelTime = 0L
            _accelSpeedMps.value = 0f
            smoothedSpeed = 0f
            _gps.value = GpsData()
        } else if (!accelOn && accelModeActive) {
            accelModeActive = false
            if (started) {
                startLocation()
                if (s.signalEnabled) startSignalMonitoring()
            }
        } else if (!accelOn) {
            // 常规模式：确保定位已启动（修复重启后因 applyMode 漏掉 startLocation 而搜不到星的 bug）
            startLocation()
            if (s.signalEnabled) {
                startSignalMonitoring()
            } else {
                stopSignalMonitoring()
                _signalLevel.value = 0
            }
        }
    }

    private fun startSensors() {
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun startLocation() {
        if (locationActive) return
        locationActive = true
        val app = getApplication<Application>()
        val granted = ContextCompat.checkSelfPermission(
            app, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    app, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            locationActive = false
            return
        }

        // GPS：更快回报率，提升速度/航向刷新（系统会自动融合北斗等星座）
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                GPS_INTERVAL_MS,
                0f,
                locationListener,
            )
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }

        // 网络定位（WiFi/基站辅助定位，加快冷启动与室内精度）
        try {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                NETWORK_INTERVAL_MS,
                0f,
                locationListener,
            )
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locationManager.registerGnssStatusCallback(
                    app.mainExecutor,
                    gnssCallback,
                )
            } else {
                @Suppress("DEPRECATION")
                locationManager.registerGnssStatusCallback(gnssCallback)
            }
        } catch (_: Exception) {
        }
    }

    private fun stopLocation() {
        if (!locationActive) return
        locationActive = false
        locationManager.removeUpdates(locationListener)
        try {
            locationManager.unregisterGnssStatusCallback(gnssCallback)
        } catch (_: Exception) {
        }
    }

    private fun startSignalMonitoring() {
        val app = getApplication<Application>()
        val tm = app.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { tm.registerTelephonyCallback(app.mainExecutor, it) }
            } else {
                @Suppress("DEPRECATION")
                tm.listen(legacySignalListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
            }
        } catch (_: Exception) {
        }
    }

    private fun stopSignalMonitoring() {
        val app = getApplication<Application>()
        val tm = app.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { tm?.unregisterTelephonyCallback(it) }
            } else {
                @Suppress("DEPRECATION")
                tm?.listen(legacySignalListener, PhoneStateListener.LISTEN_NONE)
            }
        } catch (_: Exception) {
        }
    }

    private fun startConnectivityMonitoring() {
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
    }

    // ---- 设置持久化 ----
    private fun loadSettings(): AppSettings = AppSettings(
        brakeAlertEnabled = prefs.getBoolean("brakeAlertEnabled", true),
        windEnabled = prefs.getBoolean("windEnabled", true),
        signalEnabled = prefs.getBoolean("signalEnabled", true),
        accelerometerMode = prefs.getBoolean("accelerometerMode", false),
        brakeThreshold = prefs.getFloat("brakeThreshold", 4.0f),
        windThreshold = prefs.getFloat("windThreshold", 16.0f),
        darkMode = prefs.getBoolean("darkMode", false),
        compassOffset = prefs.getFloat("compassOffset", 0f),
        cityName = prefs.getString("cityName", "") ?: "",
    )

    private fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putBoolean("brakeAlertEnabled", s.brakeAlertEnabled)
            .putBoolean("windEnabled", s.windEnabled)
            .putBoolean("signalEnabled", s.signalEnabled)
            .putBoolean("accelerometerMode", s.accelerometerMode)
            .putFloat("brakeThreshold", s.brakeThreshold)
            .putFloat("windThreshold", s.windThreshold)
            .putBoolean("darkMode", s.darkMode)
            .putFloat("compassOffset", s.compassOffset)
            .putString("cityName", s.cityName)
            .apply()
    }

    override fun onCleared() {
        super.onCleared()
        stopLocation()
        sensorManager.unregisterListener(sensorListener)
        stopSignalMonitoring()
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
    }

    private fun currentTime(): TimeInfo {
        val now = LocalDateTime.now()
        return TimeInfo(
            time = now.format(timeFormatter),
            date = now.format(dateFormatter),
            weekDay = now.format(weekFormatter),
        )
    }

    private companion object {
        /** 手机横屏向前安装：传感器自然顶部相对车头方向顺时针偏移 90° */
        const val LANDSCAPE_OFFSET_DEGREES = 90f

        /** GPS 回报间隔（毫秒），更小值 = 速度/航向刷新更快 */
        const val GPS_INTERVAL_MS = 500L

        /** 网络定位回报间隔（毫秒） */
        const val NETWORK_INTERVAL_MS = 1000L

        /** 速度平滑系数：新值权重更高，响应更快 */
        const val SPEED_SMOOTH_OLD = 0.35f
        const val SPEED_SMOOTH_NEW = 0.65f

        /** 速度低于此值（m/s）视为静止，不使用 GPS 航向 */
        const val MIN_COURSE_SPEED = 1.0f

        /** 超过此时长（毫秒）无新定位即判定掉线 */
        const val FIX_TIMEOUT_MS = 8_000L

        // ---- 急刹车检测 ----
        /** 连续多少次超过阈值才算急刹车（过滤单次颠簸） */
        const val BRAKE_SAMPLES_REQUIRED = 2

        /** 触发前速度需达到此值（m/s），避免静止放手机时误触发 */
        const val BRAKE_MIN_SPEED = 1.5f

        /** 两次急刹车告警的最小间隔（毫秒） */
        const val BRAKE_COOLDOWN_MS = 8_000L

        /** 急刹车弹窗展示时长（毫秒） */
        const val BRAKE_ALERT_DURATION_MS = 3_000L

        // ---- 静止速度修正 ----
        const val STILL_ACCEL_THRESHOLD = 0.2f
        const val STILL_SPEED_MAX = 1.2f
        const val STILL_CONFIRM_MS = 2_000L

        // ---- 纯加速度计积分 ----
        /** 低于此线性加速度（m/s²）视为匀速/静止，保持速度不衰减 */
        const val ACCEL_DEAD_ZONE = 0.15f

        /** 速度低于此值（m/s）且无有效加速度时归零 */
        const val ACCEL_STOP_SPEED = 1.0f

        // ---- 风速/天气 ----
        /** 天气网络更新间隔（毫秒） */
        const val WIND_INTERVAL_MS = 60_000L

        // 注：QWEATHER_HOST / QWEATHER_KEY 已从源码移除，改由 local.properties 经
        // BuildConfig 注入（见 app/build.gradle.kts 与 local.properties.example）。
    }
}
