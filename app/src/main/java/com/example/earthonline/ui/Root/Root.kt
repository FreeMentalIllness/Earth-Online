package com.example.earthonline.ui.Root

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.ui.MainScaffold
import com.example.earthonline.ui.navigation.Screen
import com.example.earthonline.ui.onboarding.OnboardingRoute
import com.example.earthonline.ui.theme.EarthOnlineTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * 应用根：先决引导页 / 主框架分流 + 主题偏好应用。
 * 对应 HTML 的 resolveBootFlow()：onboarding_done=false -> 引导页；否则进主程序。
 *
 * 性能/体验要点：偏好是异步读的，这里用 null 表示「还没读出来」，
 * 读出前只铺一层纯背景色并把系统启动图摁住（MainActivity 的 keepOnScreenCondition），
 * 不会出现「老用户先闪一帧引导页再跳主页」的错帧。
 */
@Composable
fun EarthOnlineAppRoot(
    rootVm: RootViewModel = hiltViewModel(),
    onReady: () -> Unit = {},
    /** 外部入口指定的落地页（桌面小组件的「任务 / 背包 / 成就 / 数据」）；默认主页 */
    startRoute: String = Screen.Home.route
) {
    val onboardingDone by rootVm.onboardingDone.collectAsStateWithLifecycle(initialValue = null)
    val themePref by rootVm.theme.collectAsStateWithLifecycle(initialValue = null)
    // v1.0.4：字号档位已移除，不再读取 fontScale —— 排版直接跟随系统字体缩放
    //（Compose 的 fontScale 由系统无障碍设置注入，应用内不再二次乘算）。

    LaunchedEffect(onboardingDone) { if (onboardingDone != null) onReady() }
    // 兜底：极端情况下（DataStore 异常慢）2s 后放行，不把用户永远摁在启动图上
    LaunchedEffect(Unit) {
        delay(2_000)
        onReady()
    }

    // v1.2.1：自绘开屏 —— 内容就绪后再叠 900ms 的出场动画，然后淡出
    var splashGone by remember { mutableStateOf(false) }

    val darkTheme = when (themePref) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    // fontScaleKey 使用默认 "std"（缩放系数 1.0）：应用内不再覆盖字号
    EarthOnlineTheme(darkTheme = darkTheme) {
        Box(Modifier.fillMaxSize()) {
            when (onboardingDone) {
                null -> Box(
                    /* v1.2.2：偏好还没读出来时铺的这层，必须是开屏底色。
                       原来是 colorScheme.background —— 深色模式下是近黑，
                       而系统启动图是暖米色，交接那一下就成了「米色 → 黑 → 开屏页」的闪跳。
                       三段（系统启动图 / 这层 / SplashOverlay）统一用 SplashBackground。 */
                    Modifier.fillMaxSize().background(SplashBackground)
                )
                false -> OnboardingRoute()
                true -> MainScaffold(startRoute = startRoute)
            }
            if (!splashGone) {
                SplashOverlay(
                    contentReady = onboardingDone != null,
                    onFinished = { splashGone = true }
                )
            }
        }
    }
}

@HiltViewModel
class RootViewModel @Inject constructor(
    settings: SettingsDataStore
) : ViewModel() {
    val onboardingDone = settings.onboardingDone
    val theme = settings.theme
    // v1.0.4：fontScale 暴露已移除（字号设置下线，跟随系统）
}
