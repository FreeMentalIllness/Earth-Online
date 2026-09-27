package com.example.earthonline.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.earthonline.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机广播：精确闹钟在重启后失效，故开机后重新调度（仅当用户开启了到期提醒）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val enabled = runCatching { SettingsDataStore(context).notify.first() }.getOrElse { false }
            if (enabled) ReminderScheduler.schedule(context)
        }
    }
}
