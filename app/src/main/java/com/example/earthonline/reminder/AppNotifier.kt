package com.example.earthonline.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.earthonline.R

/**
 * v1.0.5 应用内通知统一出口：任务到期（原有）之外的
 * 成就解锁 / 同步完成 / 跨端灵感接力 三类本地通知。
 *
 * 渠道：
 *  - earth_reminder：任务到期（ReminderWorker 原有，此处仅复用常量）
 *  - earth_ach：成就解锁（跟随「通用 → 到期提醒通知」总开关，关闭则静默）
 *  - earth_sync：同步与灵感（同上开关）
 *
 * 全部 runCatching 包裹 —— 通知失败绝不影响业务流程（解锁/同步本身已成功）。
 */
object AppNotifier {
    const val CHANNEL_ACH = "earth_ach"
    const val CHANNEL_SYNC = "earth_sync"

    const val ID_ACH = 1002
    const val ID_SYNC = 1003
    const val ID_INSPIRE_BASE = 1100   // 灵感接力多条通知的起始 id

    fun createChannel(context: Context, id: String, name: String, desc: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            nm.getNotificationChannel(id) == null
        ) {
            nm.createNotificationChannel(
                NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = desc }
            )
        }
    }

    fun post(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        text: String,
        /** 点击通知打开主界面时附带的 extra（如灵感接力标记） */
        contentIntent: Intent? = null
    ) {
        runCatching {
            createChannel(context, channelId, channelName(channelId), channelDesc(channelId))
            val pi = contentIntent?.let { intent ->
                android.app.PendingIntent.getActivity(
                    context,
                    notificationId,
                    intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP) },
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
            }
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
            pi?.let { builder.setContentIntent(it) }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(notificationId, builder.build())
        }
    }

    private fun channelName(id: String) = when (id) {
        CHANNEL_ACH -> "成就解锁"
        else -> "同步与灵感"
    }

    private fun channelDesc(id: String) = when (id) {
        CHANNEL_ACH -> "成就解锁时的本地提醒"
        else -> "云端同步完成与跨端灵感接力的本地提醒"
    }
}
