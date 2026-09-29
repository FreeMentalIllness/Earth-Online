package com.example.earthonline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.earthonline.ui.theme.AmberPrimary
import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.earthonline.ui.achievements.AchievementUnlockHost
import com.example.earthonline.ui.achievements.AchievementViewModel
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.home.AccountingDownloadDialog
import com.example.earthonline.ui.navigation.AppNavHost
import com.example.earthonline.ui.navigation.Screen
import com.example.earthonline.ui.navigation.bottomNavItems
import com.example.earthonline.ui.navigation.settingsSubRoutes
import com.example.earthonline.ui.settings.SettingsViewModel
import com.example.earthonline.util.AchievementSound
import android.app.Activity
import android.widget.Toast
import java.io.File

/**
 * 底部 Tab / 返回键统一的跳转实现。
 *
 * v1.2.0 修复「从主页点入其他页面后，再点主页 Tab 无反应」：
 * 之前用的是官方 sample 的 `popUpTo{saveState=true} + launchSingleTop + restoreState`
 * 三件套。这套组合在「目标 destination 已在返回栈中且带 SavedState」时会把 navigate
 * 判成 no-op —— 现象正是点 Tab 毫无反应（尤其是从主页进过二级页再点回主页时）。
 *
 * 改成两步、每步都可判定成败：
 *   ① 目标已在返回栈里 → popBackStack 直接弹回（有 Boolean 返回值）
 *   ② 不在栈里（首次进入该 Tab）→ 常规 navigate 压栈
 * 代价是不再记忆各 Tab 的滚动位置（saveState/restoreState 是它的实现前提），
 * 换来的是「点了一定动」—— 导航的确定性比滚动位置重要得多。
 */
private fun navigateToTab(navController: NavHostController, route: String) {
    if (navController.currentDestination?.route == route) return
    if (!navController.popBackStack(route, false)) {
        navController.navigate(route) { launchSingleTop = true }
    }
}

/**
 * 主框架：底部导航（4 个 Tab）+ 内容区 + 记账下载对话框。
 * 「更多」入口是各页面右上角的胶囊按钮，点击在其下方展开下拉菜单（不再占用底部 Tab）；
 * 菜单行为与记账对话框状态统一在此管理。
 * 系统返回键由 NavHost 自动处理（弹栈）；抽屉打开时先关抽屉。
 * 壁纸：不透明度可调（设置页滑杆），作为背景铺底。
 */
@Composable
fun MainScaffold(
    /** 外部入口（桌面小组件）指定的落地页；默认主页 */
    startRoute: String = Screen.Home.route
) {
    val navController = rememberNavController()
    var showAccounting by remember { mutableStateOf(false) }
    val settingsVm: SettingsViewModel = hiltViewModel()
    /* 成就引擎提到框架层：解锁提示要在任意页面都能弹。
       ⚠️ 同一份 VM 必须往下传给成就页（AppNavHost），否则会存在两个引擎实例，
       各自写一遍「解锁成就」动态。 */
    val achVm: AchievementViewModel = hiltViewModel()
    /* v1.0.0：桌面小组件入口的落地页。
       冷启动时 startRoute 已经作为 NavHost 的 startDestination 生效（不会先闪一帧主页）；
       这里是给「Activity 已存在、onNewIntent 改了路由」的情况补一次导航。 */
    LaunchedEffect(startRoute) {
        if (startRoute != Screen.Home.route && navController.currentDestination?.route != startRoute) {
            navController.navigate(startRoute) { launchSingleTop = true }
        }
    }

    val wallpaper by settingsVm.wallpaper.collectAsStateWithLifecycle(initialValue = "")
    val wallpaperAlpha by settingsVm.wallpaperAlpha.collectAsStateWithLifecycle(initialValue = 0.35f)
    val achSound by settingsVm.achSound.collectAsStateWithLifecycle(initialValue = true)

    // 音效开关变化时同步给播放器（关闭时顺带释放 SoundPool，省一份常驻内存）
    LaunchedEffect(achSound) { AchievementSound.setEnabled(achSound) }
    DisposableEffect(Unit) { onDispose { AchievementSound.release() } }

    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    /* 性能：壁纸按屏幕尺寸解码。原实现直接用 File 作为 model，Coil 拿不到尺寸约束时
       会按原图解码（现在手机动辄 12MP，一张壁纸 ≈ 48MB 位图），既是内存尖峰也是掉帧源。 */
    val screenW = configuration.screenWidthDp
    val screenH = configuration.screenHeightDp
    val wallpaperRequest = remember(wallpaper, screenW, screenH) {
        if (wallpaper.isBlank()) {
            null
        } else {
            val w = with(density) { screenW.dp.roundToPx() }
            val h = with(density) { screenH.dp.roundToPx() }
            ImageRequest.Builder(context)
                .data(File(wallpaper))
                .size(w, h)
                .precision(Precision.INEXACT)
                .crossfade(false)
                .build()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    /* 返回层级（Android 端统一）：
       ① 有弹窗（AlertDialog / DropdownMenu）时，Compose 的弹窗自身返回回调优先级更高，
          会先关弹窗，本 BackHandler 不会抢先触发；
       ② 无弹窗且非主页：返回主页（如从 任务/背包/数据 按返回 -> 回主页）；
       ③ 已在主页：连按两次退出（2 秒内第二次按才退出，期间 Toast 提示）。 */
    var lastBackPress by remember { mutableStateOf(0L) }
    BackHandler(enabled = true) {
        val home = Screen.Home.route
        when {
            // v1.2.3：设置二级页（外观/通用/数据与备份/关于）按返回应弹回「设置」主页，
            // 而非直接跳回首页 —— 返回栈由 NavHost 维护，这里只负责触发一次 pop。
            currentRoute in settingsSubRoutes -> navController.popBackStack()
            currentRoute != null && currentRoute != home -> navigateToTab(navController, home)
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPress < 2000) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPress = now
                    Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    val selected = currentRoute == item.route ||
                            currentRoute in item.altRoutes ||
                            navBackStackEntry?.destination?.hierarchy?.any { it.route == item.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateToTab(navController, item.route) },
                        icon = { Icon(painterResource(item.iconRes), contentDescription = item.label) },
                        label = { Text(item.label) },
                        // v1.2.3：底部导航配色统一为「深棕灰未选 / 暖琥珀选中」，
                        // 与全局视觉基线（木质暖调）一致，不再随主题色随机漂移。
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AmberPrimary,
                            unselectedIconColor = Color(0xFF6B5B4F),
                            selectedTextColor = AmberPrimary,
                            unselectedTextColor = Color(0xFF6B5B4F),
                            indicatorColor = AmberPrimary.copy(alpha = 0.14f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            if (wallpaperRequest != null) {
                AsyncImage(
                    model = wallpaperRequest,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                /* 性能：用「背景色蒙层」表达不透明度，而不是给图片设 alpha。
                   alpha < 1 会为整张全屏图创建离屏图层（saveLayer），每帧多一次全屏合成；
                   蒙层只是一个半透明矩形，几乎零成本。 */
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.background.copy(
                                alpha = (1f - wallpaperAlpha).coerceIn(0f, 1f)
                            )
                        )
                )
            }
            AppNavHost(
                navController = navController,
                startDestination = startRoute,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                achVm = achVm,
                moreActions = MoreMenuActions(
                    onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } },
                    onAccounting = { showAccounting = true }
                )
            )
            // v1.2.1：成就解锁提示（右下角卡片 + 音效），浮在所有页面与导航栏之上
            AchievementUnlockHost(vm = achVm)
            if (showAccounting) {
                AccountingDownloadDialog(onDismiss = { showAccounting = false })
            }
        }
    }
}
