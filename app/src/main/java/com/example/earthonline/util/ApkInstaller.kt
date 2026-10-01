package com.example.earthonline.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用内更新下载 + 引导安装（应用级单例，供「关于」页与启动更新弹窗共用）。
 *
 * 职责：
 *  - DownloadManager 下载 Release 直链 APK 到应用私有外部目录（无需存储权限）；
 *  - 应用级广播接收器监听下载完成 → 校验状态 → FileProvider 包 content:// URI →
 *    调起系统安装器（v1.0.5 修复：此前直接把 DownloadManager 返回的 file:// URI
 *    传给安装器，Android 7.0+ 会抛 FileUriExposedException，且私有目录文件
 *    安装器侧无读权限，安装必然失败）；
 *  - 用户消息统一走 [events] 流（StateFlow），由 UI 层收集显示为 Snackbar；
 *    UI 消费后调 [consumeEvent] 清空，避免重复弹出。
 *
 * 「安装未知应用」权限（Android 8.0+）的检查与跳系统设置页留在 UI 层 ——
 * ActivityResult 回调必须在 Composable 里注册，单例层拿不到回执。
 * 清单已声明 REQUEST_INSTALL_PACKAGES。
 */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** 一次用户可见的消息（Snackbar 文案），UI 消费后置 null */
    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events

    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /** 最近一次 enqueue 的下载 id（广播按它过滤，避免误响应历史下载） */
    private var lastDownloadId = -1L

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == lastDownloadId) onDownloadComplete(id)
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    /** 用系统下载器开始下载 APK 直链（应用私有外部目录，卸载即清，不占公共下载） */
    fun download(apkUrl: String) {
        val req = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("地球Online 更新")
            setDescription("正在下载安装包…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "earth-online-update.apk")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        lastDownloadId = downloadManager.enqueue(req)
        _events.value = "开始下载更新，完成后自动提示安装"
    }

    /** UI 消费完消息后清空，防止重组重复弹出 */
    fun consumeEvent() {
        _events.value = null
    }

    private fun onDownloadComplete(id: Long) {
        downloadManager.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return@use
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val uriStr = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            if (status != DownloadManager.STATUS_SUCCESSFUL || uriStr.isNullOrBlank()) {
                _events.value = "下载失败（网络中断或空间不足），请稍后重试"
                return@use
            }
            // 修复核心：file:// → FileProvider content://，杜绝 FileUriExposedException
            val path = Uri.parse(uriStr).path ?: run {
                _events.value = "安装包定位失败，请到 Release 页面手动下载"
                return@use
            }
            val contentUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", File(path)
            )
            val install = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(install) }
                .onFailure { _events.value = "无法启动安装，请到 Release 页面手动下载" }
        }
    }
}
