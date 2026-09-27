package com.example.earthonline.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar

/**
 * 到期提醒调度器（AlarmManager + WorkManager 双管齐下）。
 * - AlarmManager：每日 09:00 精确闹钟（setExactAndAllowWhileIdle），触发 ReminderAlarmReceiver；
 * - WorkManager：受控的后台 Worker，真正去查库并弹通知（也用于立即跑一次 / 开机后重排）。
 */
object ReminderScheduler {

    private const val ALARM_REQ = 2001
    private const val WORK_NAME = "earth_reminder_work"

    /** 创建通知渠道（Android 8+ 必需；幂等） */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(ReminderWorker.CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        ReminderWorker.CHANNEL_ID, "到期提醒",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply { description = "任务到期与逾期提醒" }
                )
            }
        }
    }

    /** 启用提醒：建渠道 + 立即跑一次 + 排每日 09:00 精确闹钟 */
    fun schedule(context: Context) {
        createChannel(context)
        enqueueNow(context)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, ALARM_REQ, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val now = Calendar.getInstance()
        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DATE, 1)
        }
        try {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pi)
        } catch (e: SecurityException) {
            // 无 SCHEDULE_EXACT_ALARM 权限（Android 12+ 受限）时退化为非精确唤醒
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pi)
        }
    }

    /** 关闭提醒：取消闹钟 + 取消待执行任务 */
    fun cancel(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, ALARM_REQ, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarm.cancel(pi)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** 立即入队执行一次（唯一工作，避免堆叠） */
    fun enqueueNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().build()
        )
    }
}
