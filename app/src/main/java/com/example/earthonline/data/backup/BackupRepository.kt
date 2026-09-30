package com.example.earthonline.data.backup

import android.content.Context
import com.example.earthonline.data.local.dao.*
import com.example.earthonline.data.local.entity.*
import com.example.earthonline.data.repository.XpLedger
import com.example.earthonline.util.ImageStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全量备份：把 Room 各表导出为单个 JSON（earth_data 等价物），或把 JSON 导回。
 * 导入采用 REPLACE 语义（按主键合并），不清除既有数据。
 */
@Singleton
class BackupRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val taskDao: TaskDao,
    private val memoDao: MemoDao,
    private val itemDao: ItemDao,
    private val achievementDao: AchievementDao,
    private val collectionDao: CollectionDao,
    private val locationDao: LocationDao,
    private val activityDao: ActivityDao,
    private val json: Json,
    // v1.0.4：导出前对账 profile.xp，保证导出的 XP 与派生口径一致
    private val xpLedger: XpLedger,
    // v1.0.0：头像改成「私有目录里的原图文件」，备份要把图一起带走，需要读写私有目录
    @ApplicationContext private val appContext: Context
) {
    @Serializable
    data class Payload(
        val version: Int = 1,
        val exportedAt: String = "",
        val profile: ProfileEntity? = null,
        val tasks: List<TaskEntity> = emptyList(),
        val memos: List<MemoEntity> = emptyList(),
        val items: List<ItemEntity> = emptyList(),
        val achievements: List<AchievementEntity> = emptyList(),
        val collections: List<CollectionEntity> = emptyList(),
        val locations: List<LocationEntity> = emptyList(),
        val activities: List<ActivityEntity> = emptyList()
    )

    suspend fun exportJson(): String {
        // v1.0.4：先对账 XP（失败不阻塞导出，xp 列保持原值）
        runCatching { xpLedger.reconcile() }
        val payload = Payload(
            exportedAt = LocalDateTime.now().toString(),
            profile = profileDao.get()?.let { withAvatarBytes(it) },
            tasks = taskDao.observeAll().first(),
            memos = memoDao.observeAll().first(),
            items = itemDao.observeAll().first(),
            achievements = achievementDao.observeAll().first(),
            collections = collectionDao.observeAll().first(),
            locations = locationDao.observeAll().first(),
            activities = activityDao.observeAll().first()
        )
        return json.encodeToString(Payload.serializer(), payload)
    }

    suspend fun importJson(text: String) {
        val p = json.decodeFromString<Payload>(text)
        p.profile?.let { profileDao.upsert(materializeAvatar(it)) }
        p.tasks.forEach { taskDao.insert(it) }
        p.memos.forEach { memoDao.insert(it) }
        p.items.forEach { itemDao.insert(it) }
        p.achievements.forEach { achievementDao.insert(it) }
        p.collections.forEach { collectionDao.insert(it) }
        p.locations.forEach { locationDao.insert(it) }
        p.activities.forEach { activityDao.insert(it) }
    }

    /**
     * 导出：把头像原图**内联**进 JSON。
     *
     * v1.0.0 起头像存的是私有目录里的文件（filesDir/avatar/xxx.jpg），
     * 而 JSON 备份是要能搬到另一台机器上用的 —— 只记路径的话，换设备后头像就丢了。
     * 所以导出时把文件字节读出来塞回 `avatarData`（Base64），
     * 导入端再落成文件（见 [materializeAvatar]）。
     */
    private fun withAvatarBytes(p: ProfileEntity): ProfileEntity {
        if (p.avatarData != null && p.avatarData!!.isNotEmpty()) return p
        val file = p.avatarPath?.takeIf { it.isNotBlank() }
            ?.let(::File)?.takeIf { it.exists() && it.length() > 0L } ?: return p
        return runCatching { p.copy(avatarData = file.readBytes()) }.getOrDefault(p)
    }

    /** 导入：把内联的头像字节落成私有目录下的原图文件，并把路径写回资料行 */
    private fun materializeAvatar(p: ProfileEntity): ProfileEntity {
        val bytes = p.avatarData
        if (bytes == null || bytes.isEmpty()) return p
        val file = ImageStore.saveBytes(ImageStore.avatarDir(appContext), "avatar", bytes, "jpg")
            ?: return p
        return p.copy(avatarPath = file.absolutePath, avatarData = null)
    }
}
