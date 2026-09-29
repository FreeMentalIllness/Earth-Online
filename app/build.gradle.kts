plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
}

import java.util.Properties

android {
    namespace = "com.example.earthonline"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.earthonline"
        minSdk = 24
        targetSdk = 35
        // v1.0.0：版本序列修正为「正式版自 1.0.0 起」；与设置页 AppInfo.version、Web 端
        // core.js 的 APP_INFO.version 三处必须一致（改一处要改三处）。
        // versionCode 只增不减（5=批次C，6=批次D，7=本轮修正：冷启动纯白起屏 + 原图导入，8=1.0.1 修复批：记账跳转容错 + 任务状态排版，9=v1.0.0 正式版重发布，10=v1.0.1，11=v1.0.2 主线：首页重构+地图闪退修复+头像壁纸裁剪+数据空状态骨架，12=v1.0.2 地图生命周期深度修复+全部动态页），
        // 否则已装机用户无法覆盖安装。versionName 回落 1.0.0 属「版本序列修正」，靠 versionCode 递增保证覆盖安装。
        versionCode = 12
        versionName = "1.0.2"
        vectorDrawables { useSupportLibrary = true }
        // 高德 SDK 需要原生 so 库，按需裁剪 ABI（阶段3 接入地图前可放行全部）
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
    }

    // 签名配置：从 local.properties 读取 EO_STORE_FILE 等字段；未配置时自动跳过，
    // 回退为 Android Studio 默认调试签名，不影响 assembleRelease 编译。
    // 注意：project.findProperty 只读 gradle.properties，不会读 local.properties，
    // 所以这里显式加载 local.properties（与 Android 模板处理 sdk.dir 的方式一致）。
    signingConfigs {
        val localProps = Properties().apply {
            val localFile = rootProject.file("local.properties")
            if (localFile.exists()) localFile.inputStream().use { load(it) }
        }
        val storeFilePath = localProps.getProperty("EO_STORE_FILE")
        val storePwd = localProps.getProperty("EO_STORE_PASSWORD")
        val keyAliasStr = localProps.getProperty("EO_KEY_ALIAS")
        val keyPwd = localProps.getProperty("EO_KEY_PASSWORD")
        if (!listOf(storeFilePath, storePwd, keyAliasStr, keyPwd).any { it.isNullOrBlank() }) {
            create("release") {
                storeFile = file(storeFilePath!!)
                storePassword = storePwd!!
                keyAlias = keyAliasStr!!
                keyPassword = keyPwd!!
            }
        }
    }

    buildTypes {
        /* 流畅度关键：debug 包的 android:debuggable=true 会让 ART 不做 AOT 编译（几乎全程解释/JIT 执行），
           同一套 Compose 代码在 debug 与 release 上的帧率差距可以到 2~3 倍。
           所以「体感卡顿」首先要排除 debug 包因素，测流畅度请用 staging / release。 */
        release {
            // 关闭 R8 压缩/优化：R8 8.5.35 的 tree-shaking 在合并 WorkManager/Hilt 生成代码时
            // 会触发内部 ConcurrentModificationException（shaking.M），且无可靠 workaround。
            // 关闭后 APK 不混淆/不瘦身（体积更大），但可正常构建安装，功能不受影响。
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            // 未配置正式签名时回退调试签名：保证 assembleRelease 产物可直接装机，
            // 且 SHA1 与已在高德登记的调试版一致（地图 Key 不会失效）
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }

        /* 体验包：不可调试 + 不混淆。拿到「非 debuggable」的 ART 编译收益，
           又完全规避 R8 误删导致的运行时崩溃，用作 release 出问题时的回退选项。 */
        create("staging") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    // View 版 Material 库：提供 Android 资源主题 Theme.Material3.DayNight.NoActionBar（themes.xml 主主题父级）
    implementation(libs.material)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.datastore)
    implementation(libs.coil)

    // 到期提醒：WorkManager（后台检查）+ Hilt Worker 注入
    implementation(libs.workmanager)
    implementation(libs.hiltWork)

    // 网络（AI 对话 + WebDAV 同步，阶段2 接入）
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation("com.google.android.material:material:1.11.0")
    // v1.0.0：读取图片 EXIF 方向（相册里的照片普遍带旋转标记，不转正头像/壁纸会躺倒）
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // 高德地图（阶段3 接入；AndroidManifest.xml 预留 key 占位，拿到 Key 后替换）
    implementation(libs.amap)
}
