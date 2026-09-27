package com.example.earthonline.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.entity.AchievementEntity
import com.example.earthonline.data.local.entity.ActivityEntity
import com.example.earthonline.data.local.entity.CollectionEntity
import com.example.earthonline.data.local.entity.ItemEntity
import com.example.earthonline.data.local.entity.LocationEntity
import com.example.earthonline.data.local.entity.MemoEntity
import com.example.earthonline.data.local.entity.ProfileEntity
import com.example.earthonline.data.local.entity.TaskEntity
import com.example.earthonline.data.model.CustomField
import com.example.earthonline.data.repository.AchievementRepository
import com.example.earthonline.data.repository.ActivityRepository
import com.example.earthonline.data.repository.CollectionRepository
import com.example.earthonline.data.repository.ItemRepository
import com.example.earthonline.data.repository.LocationRepository
import com.example.earthonline.data.repository.MemoRepository
import com.example.earthonline.data.repository.ProfileRepository
import com.example.earthonline.data.repository.TaskRepository
import com.example.earthonline.util.LifeStats
import com.example.earthonline.util.lifeStatsOf
import com.example.earthonline.util.localDayOf
import com.example.earthonline.util.nowIso
import com.example.earthonline.util.todayStr
import com.example.earthonline.util.uid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** 人生时间轴条目（任务完成 / 成就解锁 / 足迹标记）
 *  v1.2.0：`day` 精确到日（YYYY-MM-DD，本地时区），此前 `year` 只到年，
 *  同一个月完成的三件事在轴上看不出先后。 */
data class TimelineItem(
    val key: String,
    val day: String,
    val kind: String,   // task / ach / loc
    val title: String,
    val time: String
)

/**
 * v1.2.1 全局搜索命中项（任务 / 物品 / 收藏）。
 *
 * 为什么放在主页而不是单独开一个搜索页：搜索的目的是「少点几下找到东西」，
 * 点进去再回来反而多一次导航。命中项带 route，点一下直接落到对应 Tab。
 */
data class SearchHit(
    val key: String,     // kind + id，列表 key
    val kind: String,    // task / item / collection
    val route: String,   // 点击后跳转的路由
    val icon: String,
    val title: String,
    val sub: String
)

/**
 * 主页 UI 状态。
 *
 * 性能要点：整个主页只订阅 **一个** StateFlow。
 * 改造前是 8 个 collectAsState（profile / 4 个计数 / activities / memos / …），
 * 任意一个表变更都会让整棵主页树重组一次；现在 N 次重组收敛为 1 次。
 * 所有派生量（完成率、等级、签名标签、时间轴）都在 ViewModel 里算好，
 * 组合阶段只做渲染，不做解析、排序、JSON 解码。
 */
data class HomeUiState(
    val loaded: Boolean = false,
    val name: String = "",
    val birthDate: String = "",
    val signature: String = "",
    val avatarKey: String = "",
    /** v1.0.0：头像原图文件路径（显示端按控件尺寸采样，见 ui/components/Avatar.kt） */
    val avatarPath: String? = null,
    /** 兼容字段：v1.0.0 之前头像直接存字节 */
    val avatarData: ByteArray? = null,
    val fieldTags: List<String> = emptyList(),
    val hiddenFieldCount: Int = 0,
    val life: LifeStats = LifeStats.Empty,
    val totalTasks: Int = 0,
    val doneTasks: Int = 0,
    val itemCount: Int = 0,
    val itemCategoryCount: Int = 0,
    val collectionCount: Int = 0,
    val unlockedAch: Int = 0,
    val totalAch: Int = 0,
    val totalMemos: Int = 0,
    val locationCount: Int = 0,
    val recentMemos: List<MemoEntity> = emptyList(),
    val recentActs: List<ActivityEntity> = emptyList(),
    val timeline: List<TimelineItem> = emptyList()
) {
    val doneRatio: Int get() = if (totalTasks > 0) Math.round(doneTasks * 100f / totalTasks) else 0
    /** v1.2.0：成就进度（概览卡第三行，四卡统一三行文案用） */
    val achRatio: Int get() = if (totalAch > 0) Math.round(unlockedAch * 100f / totalAch) else 0
    /** v1.2.0：今日记录条数（概览卡「灵感」第二行） */
    val todayMemoCount: Int
        get() = recentMemos.count { localDayOf(it.createdAt) == todayStr() }
    val displayName: String get() = name.ifBlank { "地球玩家" }
    val latestMemo: String
        get() = recentMemos.firstOrNull()?.text?.take(20)?.ifBlank { "暂无记录" } ?: "暂无记录"
}

private data class TaskStat(val total: Int, val done: Int)
private data class MemoStat(val total: Int, val recent: List<MemoEntity>)
private data class AchStat(val unlocked: Int, val total: Int, val recent: List<AchievementEntity>)
private data class ItemStat(val total: Int, val categories: Int)
private data class LocStat(val total: Int, val recent: List<LocationEntity>)

private data class Parts(
    val profile: ProfileEntity?,
    val task: TaskStat,
    val memo: MemoStat,
    val ach: AchStat,
    val item: ItemStat,
    val loc: LocStat = LocStat(0, emptyList()),
    val acts: List<ActivityEntity> = emptyList(),
    val doneTaskRows: List<TaskEntity> = emptyList(),
    val collectionCount: Int = 0
)

/**
 * 首页 ViewModel：把资料 / 计数 / 最近动态 / 日志 / 时间轴聚合成单一状态。
 * 对应 HTML renderHome() 一次读取全局 state 后整体渲染。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val profileRepo: ProfileRepository,
    private val taskRepo: TaskRepository,
    private val memoRepo: MemoRepository,
    private val activityRepo: ActivityRepository,
    private val achievementRepo: AchievementRepository,
    private val itemRepo: ItemRepository,
    private val locationRepo: LocationRepository,
    private val collectionRepo: CollectionRepository,
    private val json: Json
) : ViewModel() {

    private val profileFlow = profileRepo.observe()
    private val taskFlow = combine(taskRepo.count(), taskRepo.doneCount()) { t, d -> TaskStat(t, d) }
    private val memoFlow = combine(memoRepo.count(), memoRepo.observeRecent(MEMO_LIMIT)) { n, l -> MemoStat(n, l) }
    private val achFlow = combine(
        achievementRepo.unlockedCount(),
        achievementRepo.totalCount(),
        achievementRepo.observeUnlockedRecent(TIMELINE_LIMIT)
    ) { u, t, l -> AchStat(u, t, l) }
    private val itemFlow = combine(itemRepo.count(), itemRepo.categoryCount()) { i, c -> ItemStat(i, c) }
    private val locFlow = combine(locationRepo.count(), locationRepo.observeRecent(TIMELINE_LIMIT)) { n, l -> LocStat(n, l) }
    private val actFlow = activityRepo.observeRecent(ACT_LIMIT)
    private val doneTaskFlow = taskRepo.observeDoneRecent(TIMELINE_LIMIT)
    // v1.2.0：概览「背包」卡第三行要显示收藏数
    private val collectionFlow = collectionRepo.count()

    val state: StateFlow<HomeUiState> =
        combine(profileFlow, taskFlow, memoFlow, achFlow, itemFlow) { p, t, m, a, i ->
            Parts(p, t, m, a, i)
        }
            .combine(locFlow) { parts, loc -> parts.copy(loc = loc) }
            .combine(actFlow) { parts, acts -> parts.copy(acts = acts) }
            .combine(doneTaskFlow) { parts, rows -> parts.copy(doneTaskRows = rows) }
            .combine(collectionFlow) { parts, c -> parts.copy(collectionCount = c) }
            .map { it.toUi() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /* ---------------- v1.2.1：主页顶部全局搜索 ---------------- */

    private val queryFlow = MutableStateFlow("")
    val query: StateFlow<String> = queryFlow

    fun setQuery(q: String) { queryFlow.value = q }
    fun clearQuery() { queryFlow.value = "" }

    /**
     * 搜索结果流。
     * - debounce 200ms：逐字输入时每敲一个字就查一次库，在低端机上会掉帧；
     * - flatMapLatest：新词来了就取消上一次查询，避免旧结果后到覆盖新结果（经典竞态）；
     * - 空词直接发空列表，不查库。
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<SearchHit>> = queryFlow
        .debounce(200)
        .map { it.trim() }
        .flatMapLatest { q ->
            if (q.isBlank()) {
                flowOf(emptyList())
            } else {
                combine(
                    taskRepo.search(q),
                    itemRepo.search(q),
                    collectionRepo.search(q)
                ) { tasks, items, cols -> buildHits(tasks, items, cols) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun buildHits(
        tasks: List<TaskEntity>,
        items: List<ItemEntity>,
        cols: List<CollectionEntity>
    ): List<SearchHit> {
        val out = ArrayList<SearchHit>(tasks.size + items.size + cols.size)
        tasks.take(8).forEach { t ->
            out += SearchHit(
                key = "task-${t.id}", kind = "task", route = "tasks", icon = "📋",
                title = t.title.ifBlank { "(未命名任务)" },
                sub = listOfNotNull(
                    STATUS_LABEL[t.status],
                    t.dueDate?.takeIf { it.isNotBlank() }?.let { "到期 $it" }
                ).joinToString(" · ")
            )
        }
        items.take(8).forEach { i ->
            out += SearchHit(
                key = "item-${i.id}", kind = "item", route = "backpack", icon = "🎒",
                title = i.name.ifBlank { "(未命名物品)" },
                sub = i.category?.takeIf { it.isNotBlank() } ?: "未分类"
            )
        }
        cols.take(8).forEach { c ->
            out += SearchHit(
                key = "col-${c.id}", kind = "collection", route = "collections", icon = "💗",
                title = c.title.ifBlank { "(未命名收藏)" },
                sub = c.note?.takeIf { it.isNotBlank() } ?: (c.category?.takeIf { it.isNotBlank() } ?: "未分类")
            )
        }
        return out
    }

    /** 灵感闪念：回车即记录（对应 HTML 侧栏 quick-memo） */
    fun addQuickMemo(text: String, type: String = "note") {
        val t = text.trim()
        if (t.isBlank()) return
        viewModelScope.launch {
            memoRepo.insert(MemoEntity(id = uid("memo"), text = t, type = type, createdAt = nowIso()))
            activityRepo.add(
                ActivityEntity(id = uid("act"), time = nowIso(), kind = "memo", title = "记录了一条灵感")
            )
        }
    }

    fun deleteMemo(id: String) {
        viewModelScope.launch { memoRepo.delete(id) }
    }

    private fun Parts.toUi(): HomeUiState {
        val fields = parseFields(profile?.customFieldsJson)
        val shown = fields.take(3)
        return HomeUiState(
            loaded = true,
            name = profile?.name.orEmpty(),
            birthDate = profile?.birthDate.orEmpty(),
            signature = profile?.signature.orEmpty(),
            avatarKey = profile?.avatarKey.orEmpty(),
            avatarPath = profile?.avatarPath,
            avatarData = profile?.avatarData,
            fieldTags = shown.map { f ->
                val v = f.value
                "${f.label}：${if (v.length > 20) v.take(20) + "…" else v}"
            },
            hiddenFieldCount = (fields.size - shown.size).coerceAtLeast(0),
            life = lifeStatsOf(profile?.birthDate),
            totalTasks = task.total,
            doneTasks = task.done,
            itemCount = item.total,
            itemCategoryCount = item.categories,
            collectionCount = collectionCount,
            unlockedAch = ach.unlocked,
            totalAch = ach.total,
            totalMemos = memo.total,
            locationCount = loc.total,
            recentMemos = memo.recent,
            recentActs = acts,
            timeline = buildTimeline()
        )
    }

    private fun parseFields(jsonText: String?): List<CustomField> {
        if (jsonText.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(CustomField.serializer()), jsonText)
        }.getOrDefault(emptyList()).filter { it.label.isNotBlank() && it.value.isNotBlank() }
    }

    /**
     * 任务完成 / 成就解锁 / 足迹三类事件合并按时间倒序，取前 16 条。
     * v1.2.0：日键一律走 localDayOf（本地时区）—— ISO 存的是 UTC，
     * 直接 take(10) 会把东八区晚上 8 点之后发生的事算成"明天"。
     */
    private fun Parts.buildTimeline(): List<TimelineItem> {
        val out = ArrayList<TimelineItem>(
            doneTaskRows.size + ach.recent.size + loc.recent.size
        )
        doneTaskRows.forEach { t ->
            val time = t.doneAt ?: return@forEach
            out += TimelineItem(t.id, localDayOf(time) ?: time.take(10), "task", t.title.ifBlank { "任务" }, time)
        }
        ach.recent.forEach { a ->
            val time = a.unlockedAt ?: return@forEach
            out += TimelineItem(a.id, localDayOf(time) ?: time.take(10), "ach", a.title.ifBlank { "成就" }, time)
        }
        loc.recent.forEach { l ->
            // 足迹本来就是本地日键，直接取前 10 位
            val day = l.date.take(10)
            out += TimelineItem(l.id, day, "loc", l.name.ifBlank { "足迹" }, l.date)
        }
        out.sortWith(compareByDescending { it.time })
        return if (out.size > TIMELINE_SHOW) out.subList(0, TIMELINE_SHOW) else out
    }

    private companion object {
        val STATUS_LABEL = mapOf(
            "planning" to "计划中",
            "active" to "进行中",
            "paused" to "已暂停",
            "done" to "已完成"
        )
        const val MEMO_LIMIT = 5
        const val ACT_LIMIT = 3
        const val TIMELINE_LIMIT = 16
        const val TIMELINE_SHOW = 16
    }
}
