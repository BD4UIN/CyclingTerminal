package com.example.cyclingterminal

/** 应用设置项（SharedPreferences 持久化，退出后不丢失） */
data class AppSettings(
    val brakeAlertEnabled: Boolean = true,
    val windEnabled: Boolean = true,
    val signalEnabled: Boolean = true,
    /** 实验性：纯加速度计测量（极其不稳定），开启后禁用卫星与移动数据 */
    val accelerometerMode: Boolean = false,
    /** 急刹车加速度阈值（m/s²） */
    val brakeThreshold: Float = 4.0f,
    /** 风速提醒阈值（km/h） */
    val windThreshold: Float = 16.0f,
    val darkMode: Boolean = false,
    /** 罗盘手动校准角度（°），正值顺时针，用于修正罗盘偏差 */
    val compassOffset: Float = 0f,
    /** 天气城市名；留空则自动用 GPS 定位反查城市 */
    val cityName: String = "",
)

/** 当前网络类型（用于真实反映“断网”状态，而不是只看信号格数） */
enum class NetworkKind { OFFLINE, WIFI, CELLULAR }
