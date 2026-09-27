package com.example.earthonline

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.earthonline.ui.Root.EarthOnlineAppRoot
import com.example.earthonline.ui.navigation.Screen
import com.example.earthonline.widget.OverviewWidget
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * 启动后要落到哪个页面。
     * 桌面小组件的四个入口带这个 extra 直达对应 Tab；默认主页。
     * ⚠️ 用 Compose 的 state 持有：Activity 已存在时（点小组件属于 SINGLE_TOP + CLEAR_TOP）
     * 走的是 onNewIntent，改这个 state 就能让下面的 content 重组并导航过去。
     */
    private var startRoute by mutableStateOf(Screen.Home.route)

    /** 首帧内容是否已就绪（偏好读完）。由 EarthOnlineAppRoot 回调置位。 */
    @Volatile
    private var contentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12+ SplashScreen API：清单已设 Theme.EarthOnline.Splash（postSplashScreenTheme 指回主主题）
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        /* v1.2.2：撤掉系统默认的退出动画，直接移除启动图层。
           默认行为是让启动图上的图标再「缩放 + 淡出」一次，
           而此刻 Compose 开屏页的图标已经在同一个位置淡入了 ——
           两套动画叠在一起就是两个图标重影着动，正是割裂感的来源之一。
           直接 remove 之后：底色一致（windowSplashScreenBackground = SplashBackground）、
           图标同位置同尺寸（splash_logo 84dp = SplashOverlay 84dp），画面就是原地接力。
           万一 Compose 首帧还没画完，露出的也是主主题的 windowBackground（同样是米色），不会白闪。 */
        splash.setOnExitAnimationListener { splashScreenView -> splashScreenView.remove() }

        // 首帧绘制前读偏好只需几十毫秒；这段时间保持启动图，
        // 避免"老用户先看到引导页再跳主页"的错帧（也是冷启动观感的一部分）
        splash.setKeepOnScreenCondition { !contentReady }

        startRoute = intent?.getStringExtra(EXTRA_ROUTE) ?: Screen.Home.route

        setContent {
            EarthOnlineAppRoot(onReady = { contentReady = true }, startRoute = startRoute)
        }
    }

    /** 已经在前台时点小组件入口：Activity 复用，只更新目标路由 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startRoute = intent.getStringExtra(EXTRA_ROUTE) ?: Screen.Home.route
    }

    override fun onPause() {
        super.onPause()
        /* v1.0.0：切到后台时顺手刷一次桌面小组件。
           放在这里而不是每次数据变更都刷：onPause 频率低、时机固定，
           且 Renderer 内部会先判断"有没有已添加的小组件"，没装就是一次空转判断。 */
        OverviewWidget.refresh(this)
    }

    companion object {
        /** 小组件 / 通知等外部入口指定落地页面的 key（值 = Screen 的 route） */
        const val EXTRA_ROUTE = "route"
    }
}
