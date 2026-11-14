import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 从 local.properties（已被 .gitignore 忽略）读取敏感凭据，避免泄露到公开仓库。
// 克隆者需复制 local.properties.example 为 local.properties 并填入自己的 key。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String, default: String = ""): String =
    localProps.getProperty(name, default).trim()

android {
    namespace = "com.example.cyclingterminal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.cyclingterminal"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.11"

        // 和风天气凭据：经 BuildConfig 注入（值存于 local.properties）
        buildConfigField("String", "QWEATHER_KEY", "\"${secret("QWEATHER_KEY")}\"")
        buildConfigField("String", "QWEATHER_HOST", "\"${secret("QWEATHER_HOST")}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../release.keystore")
            storePassword = secret("KEYSTORE_PASSWORD")
            keyAlias = "cyclingterminal"
            keyPassword = secret("KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
