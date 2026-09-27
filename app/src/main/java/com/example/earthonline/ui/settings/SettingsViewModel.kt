package com.example.earthonline.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.backup.AutoBackupManager
import com.example.earthonline.data.backup.BackupRepository
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.data.network.AiConfig
import com.example.earthonline.data.network.WebDavConfig
import com.example.earthonline.data.network.UpdateRepository
import com.example.earthonline.data.network.WebDavService
import com.example.earthonline.BuildConfig
import com.example.earthonline.data.sync.CloudSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** 一次更新检查的展示结果 */
data class UpdateCheck(
    /** 远端最新版本号 */
    val latest: String,
    /** 是否比当前版本新 */
    val hasUpdate: Boolean,
    /** 更新说明（可能为空） */
    val notes: String,
    /** 打开地址：有 APK 直链用直链，否则 Release 页面 */
    val url: String
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val backupRepo: BackupRepository,
    private val webDav: WebDavService,
    private val json: Json,
    private val cloudSync: CloudSyncManager,
    private val autoBackup: AutoBackupManager,
    private val updateRepo: UpdateRepository
) : ViewModel() {

    val theme: Flow<String> = settings.theme
    val wallpaper: Flow<String> = settings.wallpaper
    val wallpaperAlpha: Flow<Float> = settings.wallpaperAlpha
    val notify: Flow<Boolean> = settings.notify
    val aiConfigJson: Flow<String> = settings.aiConfig
    val webdavConfigJson: Flow<String> = settings.webdavConfig
    /** 自动同步开关（启动拉取 / 切后台推送） */
    val autoSync: Flow<Boolean> = settings.autoSync
    /** 上次同步时间（空串 = 从未同步） */
    val lastSyncAt: Flow<String> = settings.lastSyncAt
    /** v1.2.0：字号档位（std / lg / xl） */
    val fontScale: Flow<String> = settings.fontScale
    /** v1.2.1：成就解锁音效开关 */
    val achSound: Flow<Boolean> = settings.achSound

    fun setTheme(v: String) = viewModelScope.launch { settings.setTheme(v) }
    fun setFontScale(v: String) = viewModelScope.launch { settings.setFontScale(v) }
    fun setAchSound(v: Boolean) = viewModelScope.launch { settings.setAchSound(v) }
    fun setAutoSync(v: Boolean) = viewModelScope.launch { settings.setAutoSync(v) }
    fun setNotify(v: Boolean) = viewModelScope.launch { settings.setNotify(v) }
    fun setWallpaper(path: String) = viewModelScope.launch { settings.setWallpaper(path) }

    private var alphaJob: Job? = null

    /**
     * 壁纸不透明度：拖动时高频回调，这里做 120ms 防抖再落盘，
     * 避免每帧一次 DataStore 写入 + 全局 Flow 发射引发重组风暴。
     */
    fun setWallpaperAlpha(v: Float) {
        val c = v.coerceIn(0.05f, 0.9f)
        alphaJob?.cancel()
        alphaJob = viewModelScope.launch {
            delay(120)
            settings.setWallpaperAlpha(c)
        }
    }

    fun saveAiConfig(c: AiConfig) = viewModelScope.launch { settings.setAiConfig(json.encodeToString(AiConfig.serializer(), c)) }
    fun saveWebDavConfig(c: WebDavConfig) = viewModelScope.launch { settings.setWebdavConfig(json.encodeToString(WebDavConfig.serializer(), c)) }

    fun parseAiConfig(s: String): AiConfig =
        runCatching { json.decodeFromString<AiConfig>(s) }.getOrDefault(AiConfig())
    fun parseWebdavConfig(s: String): WebDavConfig =
        runCatching { json.decodeFromString<WebDavConfig>(s) }.getOrDefault(WebDavConfig())

    suspend fun exportJson(): String = backupRepo.exportJson()
    suspend fun importJson(text: String) = backupRepo.importJson(text)

    /* ---------------- v1.2.1：本地自动备份（保留最近 3 份） ---------------- */

    /** 现有自动快照（新 -> 旧） */
    suspend fun autoSnapshots(): List<AutoBackupManager.Snapshot> = autoBackup.listSnapshots()

    /** 立即留一份（不受 60s 最小间隔限制） */
    suspend fun autoBackupNow(): Boolean = autoBackup.snapshot(reasonCheck = false) != null

    /** 从某份快照恢复（与导入备份同为「按主键合并」语义） */
    suspend fun restoreAutoSnapshot(s: AutoBackupManager.Snapshot): Boolean = autoBackup.restore(s)

    /* ---------------- v1.2.1：应用内检查更新 ---------------- */

    /**
     * 检查更新。成功返回 [UpdateCheck]（含是否有新版、更新说明、下载地址），
     * 失败返回 Result.failure —— 无外网 / 仓库不可达 / 被限流都走这里，由 UI 提示人话。
     */
    suspend fun checkUpdate(): Result<UpdateCheck> {
        val info = updateRepo.checkLatest(AppInfo.repoOwner, AppInfo.repoName)
            .getOrElse { return Result.failure(it) }
        val hasUpdate = info.version.isNotBlank() && updateRepo.isNewer(info.version, BuildConfig.VERSION_NAME)
        return Result.success(
            UpdateCheck(
                latest = info.version.ifBlank { AppInfo.version },
                hasUpdate = hasUpdate,
                notes = info.notes,
                url = info.apkUrl?.takeIf { it.isNotBlank() } ?: info.releaseUrl
            )
        )
    }

    suspend fun testDav(config: WebDavConfig): String =
        if (config.url.isBlank()) "请先填写服务器地址"
        else if (webDav.list(WebDavService.DavConfig(config.url, config.user, config.pass), "/").isSuccess)
            "连接成功" else "连接失败（检查地址/账号/密码）"

    /** 手动推送（force：忽略自动同步开关）。远端路径与网页端默认一致，双端互通。 */
    suspend fun syncToDav(config: WebDavConfig): String {
        if (config.url.isBlank()) return "请先填写服务器地址"
        return cloudSync.push(force = true).message
    }

    /** 手动拉取（force：忽略自动同步开关）。仅当云端 exportedAt 更新时才覆盖本地。 */
    suspend fun syncFromDav(config: WebDavConfig): String {
        if (config.url.isBlank()) return "请先填写服务器地址"
        return cloudSync.pullIfRemoteNewer(force = true).message
    }
}
