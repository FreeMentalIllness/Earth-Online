package com.example.earthonline.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 小组件的每日兜底更新。
 *
 * 只在「已添加小组件」期间存在（onEnabled 注册 / onDisabled 取消），
 * 频率是 24 小时一次 —— 等级与「已存活天数」每天都变，一天一次足够，
 * 再把首次执行推迟 1 小时，避开开机那一波集中唤醒。
 * WorkManager 会把执行窗口对齐到系统维护窗口（Doze 下自动延后），比自己定闹钟省电得多。
 *
 * 不需要注入任何依赖（数据由 WidgetRenderer 走 Hilt EntryPoint 自取），
 * 所以保持 (Context, WorkerParameters) 的标准构造，交给默认 WorkerFactory 反射创建即可，
 * 不必挂 @HiltWorker —— 少一层 KSP 生成，也少一处装配出错的可能。
 */
class WidgetRefreshWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        WidgetRenderer.updateAll(appContext)
        Result.success()
    } catch (t: Throwable) {
        // 渲染失败不影响任何数据，重试即可（WorkManager 默认退避）
        Result.retry()
    }
}
