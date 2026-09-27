package com.example.earthonline.ui.navigation

/** 路由表（对应 HTML 的 9 个 PAGES key + 引导页） */
sealed class Screen(val route: String, val label: String) {
    object Home : Screen("home", "主页")
    object Profile : Screen("profile", "个人资料")
    object Tasks : Screen("tasks", "任务")
    object Backpack : Screen("backpack", "背包")
    object Achievements : Screen("achievements", "成就")
    object Data : Screen("data", "数据")
    object Map : Screen("map", "足迹")
    object Ai : Screen("ai", "系统")
    object Settings : Screen("settings", "设置")

    /** v1.0.0：周期报告（日报 / 周报 / 年报）。从数据看板页进入，不是底部 Tab。 */
    object Report : Screen("report", "周期报告")

    /**
     * 「收藏」与「背包」复用同一个界面（背包页的收藏 Tab），但必须是**独立路由**：
     * 若用同一路由加查询参数（backpack?tab=1），两者会共享 destination id 与 SavedState，
     * 导致「点底部背包却停在收藏 Tab」。
     */
    object Collections : Screen("collections", "收藏")

    /**
     * 主页「新建任务」入口：复用任务页并直接弹出新增对话框。
     * 同样必须是独立路由（理由同 Collections），否则与底部「任务」Tab 共享 SavedState，
     * 会出现「从主页进过一次新增后，再点底部任务 Tab 又弹出对话框」。
     */
    object NewTask : Screen("new_task", "新建任务")
}
