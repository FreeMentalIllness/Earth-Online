package com.example.earthonline.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.earthonline.MainActivity
import com.example.earthonline.R

/**
 * 通知栏常驻「快捷记录」按钮（v1.0.3）。
 *
 * 常驻一条 ongoing 通知：点通知主体直接落到主页世界日志输入区。
 * 记录摩擦越低，存档越厚 —— 这是留存的最大杠杆之一。
 * 开关持久化在 DataStore；Android 13+ 需要 POST_NOTIFICATIONS 运行时权限
 * （由设置页在打开开关时请求），无权限时静默不显示、绝不崩。
 */
object QuickAddNotification {

    private const val CHANNEL_ID = "quick_add"
    private const val NOTIFY_ID = 2001

    fun update(context: Context, enabled: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (!enabled || !hasPermission(context)) {
            nm.cancel(NOTIFY_ID)
            return
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID, "快捷记录", NotificationManager.IMPORTANCE_LOW
                    ).apply { setShowBadge(false) }
                )
            }
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("quick_add", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val pi = PendingIntent.getActivity(
                context, NOTIFY_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle("记录这一刻")
                .setContentText("点一下，把此刻写进你的世界日志")
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            nm.notify(NOTIFY_ID, notification)
        }
    }

    fun cancel(context: Context) {
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.cancel(NOTIFY_ID)
        }
    }

    private fun hasPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
}
