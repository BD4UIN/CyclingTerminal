# 骑行终端（CyclingTerminal）

一款面向 **Android 15/16/17（API 36）** 的横屏骑行数据终端 App。骑行时将手机固定于车把，作实时数据仪表使用。暖色主题、沉浸式全屏、屏幕常亮，支持白天/黑夜模式。

## 功能

- **时间仪表**：当前时间（时:分:秒）、星期、日期、本次骑行时长（可一键归零）。
- **导航数据**：指南针方位（罗盘动、指针固定向上）、GPS 实测速度、行进方向、卫星数（含北斗）、定位精度。
- **定位融合**：GPS + 网络辅助定位（WiFi/基站）择优，速度只信 GPS，航向校验有效才显示。
- **天气面板**：城市、气温、天气现象、风向·风力·湿度、风速（和风天气，每分钟更新），风速超标时弹出黄色胶囊。
- **降雨提醒**：有雨时在黄色胶囊旁显示蓝色胶囊与雨量。
- **天气预警**：有气象预警时弹出居中弹窗（标题含类型+级别，正文为气象局原文，手动确认关闭）。
- **急刹车提醒**：加速度传感器识别急刹车，弹出红色告警 + 强烈震动 3 秒。
- **移动信号**：WiFi / 蜂窝 / 无网络 三态真实联网状态显示。
- **设置页**：功能开关 + 急刹车阈值 / 风速阈值滑块 + 罗盘手动校准 + 天气城市名（手动/自动定位），退出后持久化。
- **实验性功能**：纯加速度计测速模式（极其不稳定，设置内可开关；开启后禁用卫星与移动数据、速度占屏大部分并显示加速度计原始数据）。

## 技术栈

- Kotlin + Jetpack Compose（Material 3），`AndroidViewModel` + `StateFlow` 单向数据流
- 原生 `LocationManager` + `GnssStatus` + `SensorManager`（旋转矢量 / 线性加速度），无第三方定位库
- 和风天气 API（2026 新版，需专属 API Host）
- 沉浸式全屏 + 屏幕常亮，R8 混淆 + 资源压缩

## 构建

环境要求：JDK 17、Android SDK（`platforms;android-36`、`build-tools;36.0.0`）、Gradle 8.11.1。

```bash
# 1) 配置本地敏感信息（必做，否则天气功能为空）
cp local.properties.example local.properties   # 然后编辑填入 sdk.dir / QWEATHER_KEY / QWEATHER_HOST 等

# 2) 构建
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/Android/Sdk
gradle assembleDebug        # 调试包（无需签名）
gradle assembleRelease      # 发布包（需 release.keystore）
```

> `assembleRelease` 需要自行用 keytool 生成 `release.keystore`（别名 `cyclingterminal`），并在 `local.properties` 里填 `KEYSTORE_PASSWORD`。`release.keystore` 已被 `.gitignore` 忽略，勿提交。

## 敏感信息

和风天气 API Key / Host 与签名密码**不随仓库公开**，统一放在 `local.properties`（已 gitignore）。克隆后参照 `local.properties.example` 自填：

- `QWEATHER_KEY` / `QWEATHER_HOST`：和风天气凭据（[console.qweather.com](https://console.qweather.com) 注册获取；2026 新 API 的专属 Host 在 `console.qweather.com/setting` 查看）。
- 留空时天气/风速/预警功能静默失效，其余功能不受影响。

## 目录结构

```
CyclingTerminal/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/example/cyclingterminal/
│   │   ├── MainActivity.kt          # 权限申请 + 沉浸式全屏 + 组合 UI
│   │   ├── CyclingViewModel.kt      # 时间/GPS/指南针/天气/预警/加速度计数据流
│   │   ├── AppSettings.kt           # 设置数据类 + 持久化
│   │   └── ui/
│   │       ├── Theme.kt             # 暖色亮/暗主题
│   │       ├── CyclingScreen.kt     # 主界面 + 设置页 + 预警弹窗
│   │       └── CompassRose.kt       # 指南针罗盘组件
│   └── res/                         # 字符串/主题/自适应图标
├── app/build.gradle.kts
└── build.gradle.kts
```

## 许可

[MIT](LICENSE)
