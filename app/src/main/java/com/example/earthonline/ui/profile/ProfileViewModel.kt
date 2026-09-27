package com.example.earthonline.ui.profile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.entity.ProfileEntity
import com.example.earthonline.data.model.CustomField
import com.example.earthonline.data.repository.AchievementRepository
import com.example.earthonline.data.repository.ProfileRepository
import com.example.earthonline.data.repository.TaskRepository
import com.example.earthonline.util.ImageStore
import com.example.earthonline.util.ShareCardData
import com.example.earthonline.util.lifeStatsOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * 个人资料页 ViewModel（对应 HTML 的 profile.js 编辑表单）。
 * 编辑态用 Compose State 持有，保存时整行 upsert 回 ProfileEntity（id 固定 1）。
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repo: ProfileRepository,
    // v1.0.0：分享卡片要显示「成就数 / 完成任务数」，这两个数不在 profile 表里，
    // 由各自的仓储提供（Hilt 已注入，不用在 UI 层再开一个 VM）
    private val achRepo: AchievementRepository,
    private val taskRepo: TaskRepository,
    // v1.0.0：头像改成「原图文件」后，VM 需要读写私有目录（旧字节迁移 / 分享卡片取图）
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val profile = repo.observe()

    /* ⚠️ 这里必须用 Eagerly 而不是 WhileSubscribed：
       分享卡片是「点一下按钮」时才读这三个值，UI 平时并不 collect 它们。
       用 WhileSubscribed 的话没有任何订阅者，flow 根本没启动，.value 会永远停在初始值 0
       —— 卡片上就成了「成就 0/0、完成任务 0」，看起来像数据丢了。 */
    val unlockedAch: StateFlow<Int> =
        achRepo.unlockedCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val totalAch: StateFlow<Int> =
        achRepo.totalCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val doneTasks: StateFlow<Int> =
        taskRepo.doneCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // 当前库内行（用于 copy 保留未编辑字段）
    private var loaded: ProfileEntity? = null

    var name by mutableStateOf("")
    var avatarKey by mutableStateOf("")
    var gender by mutableStateOf("")
    var country by mutableStateOf("中国")
    var province by mutableStateOf("")
    var signature by mutableStateOf("")
    var birthDate by mutableStateOf("")
    var customFields by mutableStateOf(listOf<CustomField>())
    /**
     * v1.0.0：头像原图文件路径（filesDir/avatar 下）。原图完整保存，不压缩。
     * [avatarData] 只作为兼容字段保留（旧库里的字节），新头像一律只写路径。
     */
    var avatarPath: String? by mutableStateOf(null)
    var avatarData: ByteArray? = null

    val canSave: Boolean
        get() = name.isNotBlank()

    /** 用库内行初始化编辑态；仅在库数据变化时调用，避免覆盖用户输入 */
    fun load(p: ProfileEntity) {
        loaded = p
        name = p.name
        avatarKey = p.avatarKey
        gender = p.gender
        country = p.country
        province = p.province
        signature = p.signature
        birthDate = p.birthDate
        avatarPath = p.avatarPath
        avatarData = p.avatarData
        customFields = runCatching {
            Json.decodeFromString<List<CustomField>>(p.customFieldsJson)
        }.getOrDefault(emptyList())

        /* v1.0.0 旧数据迁移：库里只有字节、没有文件路径时（v1.0.0 之前存的头像），
           把那份字节原样落成文件并回填 avatarPath。
           字节本身保留不动 —— 备份导出的兼容逻辑还要用它。 */
        if (p.avatarPath.isNullOrBlank() && p.avatarData != null && p.avatarData!!.isNotEmpty()) {
            val legacy = p.avatarData ?: return
            viewModelScope.launch(Dispatchers.IO) {
                val file = ImageStore.saveBytes(
                    ImageStore.avatarDir(appContext), "avatar", legacy, "jpg"
                ) ?: return@launch
                repo.upsert(p.copy(avatarPath = file.absolutePath))
            }
        }
    }

    /**
     * v1.0.0：组装分享卡片数据。
     * 用**当前编辑态**的值（用户可能刚改完名字还没保存就点了分享），
     * 编辑态为空时回落到库内已保存的值。
     */
    fun buildCardData(): ShareCardData {
        val birth = birthDate.takeIf { it.isNotBlank() } ?: loaded?.birthDate.orEmpty()
        val life = lifeStatsOf(birth)
        return ShareCardData(
            name = name.takeIf { it.isNotBlank() } ?: loaded?.name.orEmpty(),
            level = life.age,
            levelProgress = life.progress,
            achievements = unlockedAch.value,
            totalAchievements = totalAch.value,
            tasksDone = doneTasks.value,
            signature = signature.takeIf { it.isNotBlank() } ?: loaded?.signature.orEmpty()
        )
    }

    /**
     * v1.0.0：设置上传头像 —— 只记**原图文件路径**，不动字节、不做压缩。
     * 旧文件不在这里删：用户可能点完就返回不保存，删了就真没了；
     * 真正的旧文件清理放在 [save]（确认落库之后再删，见下）。
     */
    fun setUploadedAvatarPath(path: String) {
        avatarPath = path
        avatarData = null
        avatarKey = "" // 上传优先；清空预设 key
    }

    fun selectPresetAvatar(key: String) {
        avatarKey = key
        avatarData = null
        avatarPath = null
    }

    /** 重置为默认头像（预设 emoji）：清掉路径与字节 */
    fun resetAvatar() {
        avatarKey = ""
        avatarData = null
        avatarPath = null
    }

    /** 分享卡片用：拿到头像文件（旧字节会自动迁移成临时文件，保证卡片上有图） */
    suspend fun avatarFile(): File? = withContext(Dispatchers.IO) {
        avatarPath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }?.let { return@withContext it }
        val bytes = avatarData
        if (bytes == null || bytes.isEmpty()) return@withContext null
        ImageStore.saveBytes(ImageStore.avatarDir(appContext), "avatar", bytes, "jpg")
    }

    fun addCustomField() {
        if (customFields.size < 20) customFields = customFields + CustomField(id = "cf_${System.currentTimeMillis()}")
    }

    fun updateCustomField(idx: Int, label: String, value: String) {
        val list = customFields.toMutableList()
        if (idx in list.indices) {
            list[idx] = list[idx].copy(label = label.take(30), value = value.take(200))
            customFields = list
        }
    }

    fun removeCustomField(idx: Int) {
        customFields = customFields.toMutableList().also { it.removeAt(idx) }
    }

    fun save() {
        viewModelScope.launch {
            val base = loaded ?: ProfileEntity()
            // 头像换过了 -> 落库成功后清掉被替换掉的旧文件（确认写库再删，失败也不丢图）
            val oldPath = base.avatarPath
            val newPath = avatarPath
            if (!oldPath.isNullOrBlank() && oldPath != newPath) {
                ImageStore.deleteFile(oldPath)
            }
            repo.upsert(
                base.copy(
                    name = name,
                    avatarKey = avatarKey,
                    avatarPath = newPath,
                    avatarData = if (newPath.isNullOrBlank()) avatarData else null,
                    gender = gender,
                    country = country,
                    province = province,
                    signature = signature.take(200),
                    birthDate = birthDate,
                    customFieldsJson = Json.encodeToString(ListSerializer(CustomField.serializer()), customFields)
                )
            )
        }
    }
}
