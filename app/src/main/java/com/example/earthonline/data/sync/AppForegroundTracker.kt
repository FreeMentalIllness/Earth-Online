package com.example.earthonline.data.sync

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * 前后台切换监听（自己实现，避免额外引入 lifecycle-process 依赖）。
 * 规则与 Web 端一致：回到前台 → 拉取云端；切到后台 → 推送本地。
 */
class AppForegroundTracker(private val manager: CloudSyncManager) : Application.ActivityLifecycleCallbacks {

    private var started = 0
    private var inBackground = true

    override fun onActivityStarted(activity: Activity) {
        if (++started == 1 && inBackground) {
            inBackground = false
            manager.syncOnForeground()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (--started <= 0) {
            started = 0
            inBackground = true
            manager.syncOnBackground()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
