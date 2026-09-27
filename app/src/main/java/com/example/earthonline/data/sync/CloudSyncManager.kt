package com.example.earthonline.data.sync

import com.example.earthonline.data.backup.BackupRepository
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.data.network.WebDavConfig
import com.example.earthonline.data.network.WebDavService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** 同步结果：ok=是否成功，changed=数据是否真的变动过（用于决定是否提示/刷新 UI） */
data class SyncResult(val ok: Boolean, val message: String, val changed: Boolean = false)

/**
 * WebDAV 双端同步（对应 Web 端 modules/webdav.js 的自动同步）。
 *
 * 冲突策略：**按修改时间** —— 云端备份的 `exportedAt` 与本地记录的「上次同步时间」比对，
 * 只有云端更新才拉取覆盖本地；推送时把本地导出时间写回，作为下次比对的基准。
 *
 * 触发时机（与 Web 端一致）：
 *  - 进入前台 / 冷启动 → `syncOnForeground()` → 拉取（云端更新才写库）
 *  - 切到后台 → `syncOnBackground()` → 推送本地最新存档
 *
 * 远端文件名与 Web 端默认路径保持一致（`/earth_online_backup.json`），双端才能真正互通。
 */
@Singleton
class CloudSyncManager @Inject constructor(
    private val settings: SettingsDataStore,
    private val backupRepo: BackupRepository,
    private val webDav: WebDavService,
    private val json: Json
) {

    companion object {
        /** 与 Web 端 WEBDAV_DEFAULT_PATH 一致（下划线），保证两端读写同一个文件 */
        const val DEFAULT_REMOTE_PATH = "/earth_online_backup.json"

        /** 用户可在 Android 端自定义路径；留空则用上面的默认值 */
        fun remotePathOf(cfg: WebDavConfig): String {
            val p = cfg.path.trim()
            if (p.isEmpty()) return DEFAULT_REMOTE_PATH
            return if (p.startsWith("/")) p else "/$p"
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** 串行化：避免拉/推并发互相覆盖 */
    private val mutex = Mutex()

    /** 冷启动 / 回前台：云端更新才拉取覆盖本地 */
    fun syncOnForeground() {
        scope.launch { pullIfRemoteNewer() }
    }

    /** 切后台：把本地最新存档推上去 */
    fun syncOnBackground() {
        scope.launch { push() }
    }

    /**
     * 拉取：云端 exportedAt 新于本地上次同步时间时才导入。
     * @param force true 时忽略自动同步开关（设置页手动点「从云端恢复」用）
     */
    suspend fun pullIfRemoteNewer(force: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cfg = readConfig()
                ?: return@withLock SyncResult(false, "未配置 WebDAV")
            if (!force && !settings.autoSync.first()) {
                return@withLock SyncResult(false, "自动同步已关闭")
            }
            val bytes = webDav.download(davOf(cfg), remotePathOf(cfg))
                .getOrElse { return@withLock SyncResult(false, "下载失败：${it.message}") }
            val text = bytes.toString(Charsets.UTF_8)
            val remote = runCatching { json.decodeFromString<BackupRepository.Payload>(text) }
                .getOrElse { return@withLock SyncResult(false, "云端文件不是有效的备份") }

            val remoteAt = parseMillis(remote.exportedAt)
            val localAt = parseMillis(settings.lastSyncAt.first())
            if (remoteAt <= localAt) {
                return@withLock SyncResult(true, "云端无更新", changed = false)
            }
            runCatching { backupRepo.importJson(text) }
                .getOrElse { return@withLock SyncResult(false, "本地写入失败：${it.message}") }
            settings.setLastSyncAt(remote.exportedAt)
            SyncResult(true, "已拉取云端最新数据", changed = true)
        }
    }

    /**
     * 推送：导出本地全量 → PUT → 记录同步时间。
     * @param force true 时忽略自动同步开关（设置页手动点「立即同步」用）
     */
    suspend fun push(force: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cfg = readConfig()
                ?: return@withLock SyncResult(false, "未配置 WebDAV")
            if (!force && !settings.autoSync.first()) {
                return@withLock SyncResult(false, "自动同步已关闭")
            }
            val text = backupRepo.exportJson()
            val at = runCatching { json.decodeFromString<BackupRepository.Payload>(text).exportedAt }
                .getOrDefault(LocalDateTime.now().toString())
            webDav.upload(davOf(cfg), remotePathOf(cfg), text.toByteArray(Charsets.UTF_8))
                .getOrElse { return@withLock SyncResult(false, "上传失败：${it.message}") }
            settings.setLastSyncAt(at)
            SyncResult(true, "已推送到云端", changed = true)
        }
    }

    private suspend fun readConfig(): WebDavConfig? {
        val raw = settings.webdavConfig.first()
        if (raw.isBlank()) return null
        val cfg = runCatching { json.decodeFromString<WebDavConfig>(raw) }.getOrNull() ?: return null
        return if (cfg.url.isBlank()) null else cfg
    }

    private fun davOf(c: WebDavConfig) = WebDavService.DavConfig(c.url, c.user, c.pass)

    /**
     * 时间字符串 → 毫秒。
     * 兼容三种来源：Web 端 `toISOString()`（带 Z）、Android `LocalDateTime.now()`（无时区）、空串。
     */
    private fun parseMillis(s: String): Long {
        val v = s.trim()
        if (v.isEmpty()) return 0L
        return runCatching { OffsetDateTime.parse(v).toInstant().toEpochMilli() }
            .getOrElse {
                runCatching { LocalDateTime.parse(v).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
                    .getOrDefault(0L)
            }
    }
}
