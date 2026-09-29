package com.example.earthonline

import android.app.Application
import com.amap.api.maps.MapsInitializer
import androidx.work.Configuration
import com.example.earthonline.data.backup.AutoBackupManager
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.data.sync.AppForegroundTracker
import com.example.earthonline.data.sync.CloudSyncManager
import com.example.earthonline.reminder.ReminderScheduler
import com.example.earthonline.widget.WidgetUpdateDispatcher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EarthOnlineApp : Application(), Configuration.Provider {

    /** Hilt 注入的 Worker 工厂（WorkManager 据此构造 @HiltWorker） */
    @Inject lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    /** 注入的设置仓储（读取到期提醒开关） */
    @Inject lateinit var settings: SettingsDataStore

    /** WebDAV 双端同步：进入前台拉取、切到后台推送 */
    @Inject lateinit var cloudSync: CloudSyncManager

    /** v1.2.1：本地自动备份（表变更防抖后落盘，保留最近 3 份） */
    @Inject lateinit var autoBackup: AutoBackupManager

    /** v1.0.0：桌面小组件刷新调度（表变更防抖后推一次 RemoteViews） */
    @Inject lateinit var widgetUpdates: WidgetUpdateDispatcher

    /**
     * 用 Hilt 的 WorkerFactory 构造 WorkManager 配置。
     * 必须用 getter（首次访问时才求值），否则会在构造期早于 Hilt 注入时读到未初始化的 workerFactory。
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // 高德地图隐私合规：必须在任何 MapView 创建前同意隐私政策，否则新版 SDK 会抛异常导致地图闪退。
        // 放在 Application 启动处一次性同意，覆盖所有地图入口。
        // 参数必须为 (true, true)：表示隐私政策包含高德相关内容声明 + 调整说明；
        // 传 (false, false) 会导致 SDK 报 errorCode 555571「隐私合规校验失败」→ 瓦片拒绝加载（白屏）。
        runCatching { MapsInitializer.updatePrivacyAgree(this, true) }
        runCatching { MapsInitializer.updatePrivacyShow(this, true, true) }
        // 通知渠道随应用启动创建（权限未授予也不影响渠道存在）
        ReminderScheduler.createChannel(this)
        // 若用户此前已开启到期提醒，重启应用后重新调度每日闹钟
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val enabled = runCatching { settings.notify.first() }.getOrElse { false }
            if (enabled) ReminderScheduler.schedule(this@EarthOnlineApp)
        }
        // v1.2.1：本地自动备份 —— 注册 Room 表变更监听（幂等，内部有 started 标志）
        autoBackup.start()
        // v1.0.0：桌面小组件 —— 同一个表变更信号，防抖后刷新（幂等，未添加小组件时零开销）
        widgetUpdates.start()
        // WebDAV 自动同步：首个 Activity 启动（= 冷启动/回前台）→ 拉取；全部退到后台 → 推送
        registerActivityLifecycleCallbacks(AppForegroundTracker(cloudSync))
    }
}
