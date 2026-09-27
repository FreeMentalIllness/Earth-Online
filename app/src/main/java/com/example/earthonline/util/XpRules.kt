package com.example.earthonline.util

/**
 * 经验值（XP）换算规则 —— 全 App 唯一口径。
 *
 * v1.0.0 从 ui.report 提到 util：桌面小组件也要显示「累计经验」，
 * 让它去 import ui 层的常量会把「界面包」变成事实上的公共依赖，
 * 之后任何 UI 重构都会牵连小组件。规则本身是数据口径，不属于任何界面。
 *
 * 改动这里 = 改动报告页与桌面小组件两处显示，务必一起看。
 */
object XpRules {
    const val TASK_DONE = 10
    const val ACHIEVEMENT = 50
    const val MEMO = 5
    const val LOCATION = 8
}
