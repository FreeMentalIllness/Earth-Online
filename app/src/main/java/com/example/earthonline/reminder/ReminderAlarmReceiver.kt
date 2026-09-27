package com.example.earthonline.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 每日 09:00 精确闹钟的广播接收器（AlarmManager → 此 Receiver → 入队 WorkManager 任务）。
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.enqueueNow(context)
    }
}
