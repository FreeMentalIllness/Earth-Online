package com.example.earthonline.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.earthonline.R
import com.example.earthonline.data.repository.TaskRepository
import com.example.earthonline.util.todayStr
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * 到期提醒后台任务（WorkManager）。
 * 查询所有 To Do，筛选出「今天到期或已逾期且未完成」的任务，合并为一条通知推送。
 * 通过 @HiltWorker 由 HiltWorkerFactory 注入 TaskRepository（见 EarthOnlineApp）。
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val params: WorkerParameters,
    private val taskRepo: TaskRepository
) : CoroutineWorker(appContext, params) {

    companion object {
        const val CHANNEL_ID = "earth_reminder"
        const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        val today = todayStr()
        val tasks = runCatching { taskRepo.observeAll().first() }.getOrElse { return Result.failure() }
        val due = tasks.filter {
            it.category == "todo" && it.status != "done" &&
                !it.dueDate.isNullOrBlank() && it.dueDate <= today
        }
        if (due.isEmpty()) return Result.success()

        val overdueCount = due.count { (it.dueDate ?: "") < today }
        val title = if (overdueCount > 0)
            "地球Online · ${due.size} 个任务到期/逾期" else "地球Online · ${due.size} 个任务今天到期"

        val style = NotificationCompat.InboxStyle()
        due.take(8).forEach { t ->
            val tag = if ((t.dueDate ?: "") < today) "逾期 ${t.dueDate}" else "今天 ${t.dueDate}"
            style.addLine("${t.title} · $tag")
        }
        if (due.size > 8) style.setSummaryText("等 ${due.size} 个任务待处理")

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 渠道在 Application 创建；此处兜底确保存在
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            nm.getNotificationChannel(CHANNEL_ID) == null
        ) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "到期提醒",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "任务到期与逾期提醒" }
            )
        }

        val notif = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("有 ${due.size} 个任务需要处理")
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notif)
        return Result.success()
    }
}
