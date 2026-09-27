package com.example.earthonline.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 桌面总览小组件。
 *
 * 刷新策略（省电是硬要求，所以**没有**用 updatePeriodMillis 定时轮询）：
 *  ① 应用内数据变更 —— WidgetUpdateDispatcher 监听 Room 表，防抖 3s 后刷新；
 *  ② 每天一次 —— WidgetRefreshWorker（WorkManager 周期任务，只在已添加小组件时存在）；
 *  ③ 系统事件 —— 添加小组件 / 开机 / 应用升级会发 APPWIDGET_UPDATE，走 onReceive 兜底。
 * 三条路径最终都汇到同一个 WidgetRenderer.updateAll()，不会重复渲染。
 *
 * 为什么不用 Glance：项目 minSdk 24 且离线构建，Glance 要额外引入一整套 Compose runtime 依赖；
 * RemoteViews 是 framework 自带能力，零新增依赖、内存占用也更小，对本小组件的静态布局完全够用。
 */
class OverviewWidget : AppWidgetProvider() {

    /**
     * 统一在这里分发，而不是各自覆盖 onUpdate/onEnabled：
     * goAsync() 一次广播只能调一次，分散到多个回调里容易重复调用或漏掉 finish()。
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // 内部会分发 onUpdate / onEnabled / onDeleted 等
        val action = intent.action
        val isRefresh = action == ACTION_REFRESH ||
            action == AppWidgetManager.ACTION_APPWIDGET_UPDATE ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_BOOT_COMPLETED
        if (!isRefresh) return

        // BroadCastReceiver 的默认生命周期只有 onReceive 那几毫秒，
        // 查 Room 必须延长它，否则进程随时被回收、视图更新到一半。
        val pending = goAsync()
        scope.launch {
            try {
                WidgetRenderer.updateAll(context)
            } catch (t: Throwable) {
                Log.w(TAG, "小组件刷新失败", t)
            } finally {
                pending.finish()
            }
        }
    }

    /** 第一个小组件被添加：注册每日一次的兜底更新 */
    override fun onEnabled(context: Context) {
        WidgetSchedule.enable(context)
    }

    /** 最后一个小组件被移除：取消周期任务，别再每天唤醒 */
    override fun onDisabled(context: Context) {
        WidgetSchedule.disable(context)
    }

    companion object {
        const val TAG = "OverviewWidget"
        /** 显式刷新广播（外部/测试用；应用内直接调 refresh()） */
        const val ACTION_REFRESH = "com.example.earthonline.widget.REFRESH"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * 触发一次刷新（异步、不阻塞调用方）。
         * 内部先判断「有没有已添加的小组件」，没有就直接返回 —— 未添加时零开销。
         */
        fun refresh(context: Context) {
            scope.launch {
                runCatching { WidgetRenderer.updateAll(context) }
                    .onFailure { Log.w(TAG, "小组件刷新失败", it) }
            }
        }
    }
}
