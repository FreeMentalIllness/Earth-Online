package com.example.earthonline.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.entity.AchievementEntity
import com.example.earthonline.data.local.entity.MemoEntity
import com.example.earthonline.data.local.entity.TaskEntity
import com.example.earthonline.data.repository.AchievementRepository
import com.example.earthonline.data.repository.LocationRepository
import com.example.earthonline.data.repository.MemoRepository
import com.example.earthonline.data.repository.TaskRepository
import com.example.earthonline.ui.components.BarItem
import com.example.earthonline.util.XpRules
import com.example.earthonline.util.dayStartIso
import com.example.earthonline.util.dayStrOffset
import com.example.earthonline.util.todayStr
import com.example.earthonline.util.yearStartIso
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/** 报告周期 */
enum class ReportKind(val label: String, val emoji: String) {
    DAY("日报", "📅"),
    WEEK("周报", "🗓️"),
    YEAR("年报", "📆")
}

/** 图表形态：柱状 / 折线 */
enum class ChartMode { BAR, LINE }

/** 报告区间（ISO 左闭右开 + 日键闭区间，两种口径都要给：不同表的时间字段格式不同） */
private data class Range(
    val fromIso: String,
    val toIso: String,
    val fromDay: String,
    val toDay: String
)

data class ReportUiState(
    val loaded: Boolean = false,
    val kind: ReportKind = ReportKind.DAY,
    val rangeLabel: String = "",
    val chartTitle: String = "",
    val chartMode: ChartMode = ChartMode.BAR,
    val tasksDone: Int = 0,
    val memos: Int = 0,
    val achievements: Int = 0,
    val locations: Int = 0,
    val xp: Int = 0,
    val bars: List<BarItem> = emptyList(),
    val linePoints: List<Int> = emptyList(),
    val lineLabels: List<String> = emptyList(),
    val summary: String = "",
    /** 区间内最有代表性的几条产出（日报 / 周报展示，年报不展示以免过长） */
    val highlights: List<String> = emptyList()
)

private data class Raw(
    val tasksDone: Int = 0,
    val memos: Int = 0,
    val achievements: Int = 0,
    val locations: Int = 0,
    val memoList: List<MemoEntity> = emptyList(),
    val doneList: List<TaskEntity> = emptyList(),
    val achList: List<AchievementEntity> = emptyList()
)

/**
 * 周期性报告 ViewModel（日报 / 周报 / 年报）。
 *
 * 数据流只有一条：周期 -> 区间 -> 各表聚合 -> UI 状态。
 * 切换周期时 flatMapLatest 会取消上一次的查询，不会出现「切到年报却先闪一下日报的数字」。
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val taskRepo: TaskRepository,
    private val memoRepo: MemoRepository,
    private val achRepo: AchievementRepository,
    private val locationRepo: LocationRepository
) : ViewModel() {

    private val kindFlow = MutableStateFlow(ReportKind.DAY)

    private val rawFlow: StateFlow<Raw> = kindFlow
        .flatMapLatest { kind ->
            val r = rangeOf(kind)
            combine(
                taskRepo.doneCountBetween(r.fromIso, r.toIso),
                memoRepo.countBetween(r.fromIso, r.toIso),
                achRepo.unlockedCountBetween(r.fromIso, r.toIso),
                locationRepo.countBetweenDays(r.fromDay, r.toDay)
            ) { t, m, a, l -> Raw(t, m, a, l) }
                .combine(memoRepo.between(r.fromIso, r.toIso, DETAIL_LIMIT)) { raw, list ->
                    raw.copy(memoList = list)
                }
                .combine(taskRepo.doneBetween(r.fromIso, r.toIso, DETAIL_LIMIT)) { raw, list ->
                    raw.copy(doneList = list)
                }
                .combine(achRepo.unlockedBetween(r.fromIso, r.toIso, DETAIL_LIMIT)) { raw, list ->
                    raw.copy(achList = list)
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Raw())

    val state: StateFlow<ReportUiState> =
        combine(kindFlow, rawFlow) { kind, raw -> buildState(kind, raw) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState())

    fun selectKind(kind: ReportKind) {
        kindFlow.value = kind
    }

    private fun rangeOf(kind: ReportKind): Range = when (kind) {
        ReportKind.DAY -> Range(dayStartIso(0), dayStartIso(1), dayStrOffset(0), dayStrOffset(0))
        ReportKind.WEEK -> Range(dayStartIso(-6), dayStartIso(1), dayStrOffset(-6), dayStrOffset(0))
        ReportKind.YEAR -> {
            val year = Calendar.getInstance().get(Calendar.YEAR)
            Range(yearStartIso(0), yearStartIso(1), "${year}-01-01", "${year}-12-31")
        }
    }

    private fun buildState(kind: ReportKind, raw: Raw): ReportUiState {
        val xp = raw.tasksDone * XpRules.TASK_DONE +
            raw.achievements * XpRules.ACHIEVEMENT +
            raw.memos * XpRules.MEMO +
            raw.locations * XpRules.LOCATION

        // 一个「事件」= 一条灵感 / 一个完成任务 / 一个解锁成就 —— 图表统计的是"活跃事件数"，
        // 与上面四项计数口径不同（那四项是分类计数，这个是总量分布）。
        val events = ArrayList<Triple<Long, Int, String>>(
            raw.memoList.size + raw.doneList.size + raw.achList.size
        )
        raw.memoList.forEach { events += Triple(tsOf(it.createdAt), 0, it.text) }
        raw.doneList.forEach { events += Triple(tsOf(it.doneAt), 1, it.title) }
        raw.achList.forEach { events += Triple(tsOf(it.unlockedAt), 2, it.title) }

        return when (kind) {
            ReportKind.DAY -> buildDay(raw, events, xp)
            ReportKind.WEEK -> buildWeek(raw, events, xp)
            ReportKind.YEAR -> buildYear(raw, events, xp)
        }
    }

    private fun buildDay(raw: Raw, events: List<Triple<Long, Int, String>>, xp: Int): ReportUiState {
        val buckets = IntArray(24)
        events.forEach { (ts, _, _) ->
            val h = hourOfTs(ts)
            if (h in 0..23) buckets[h]++
        }
        val bars = buckets.mapIndexed { h, v -> BarItem(if (h % 3 == 0) "%02d".format(h) else "", v) }
        return ReportUiState(
            loaded = true,
            kind = ReportKind.DAY,
            rangeLabel = todayStr(),
            chartTitle = "今日 24 小时活跃分布",
            chartMode = ChartMode.BAR,
            tasksDone = raw.tasksDone,
            memos = raw.memos,
            achievements = raw.achievements,
            locations = raw.locations,
            xp = xp,
            bars = bars,
            summary = daySummary(raw, buckets),
            highlights = highlightsOf(raw)
        )
    }

    private fun buildWeek(raw: Raw, events: List<Triple<Long, Int, String>>, xp: Int): ReportUiState {
        val days = (6 downTo 0).map { dayStrOffset(-it) }
        val index = days.withIndex().associate { it.value to it.index }
        val buckets = IntArray(7)
        events.forEach { (ts, _, _) ->
            val d = dayOfTs(ts) ?: return@forEach
            val i = index[d]
            if (i != null) buckets[i]++
        }
        val labels = days.map { weekdayLabel(it) }
        return ReportUiState(
            loaded = true,
            kind = ReportKind.WEEK,
            rangeLabel = "${days.first().takeLast(5).replace('-', '/')} – ${days.last().takeLast(5).replace('-', '/')}",
            chartTitle = "近 7 天活跃趋势",
            chartMode = ChartMode.LINE,
            tasksDone = raw.tasksDone,
            memos = raw.memos,
            achievements = raw.achievements,
            locations = raw.locations,
            xp = xp,
            linePoints = buckets.toList(),
            lineLabels = labels,
            summary = weekSummary(raw, buckets),
            highlights = highlightsOf(raw)
        )
    }

    private fun buildYear(raw: Raw, events: List<Triple<Long, Int, String>>, xp: Int): ReportUiState {
        val buckets = IntArray(12)
        events.forEach { (ts, _, _) ->
            val m = monthOfTs(ts)
            if (m in 0..11) buckets[m]++
        }
        val year = Calendar.getInstance().get(Calendar.YEAR)
        return ReportUiState(
            loaded = true,
            kind = ReportKind.YEAR,
            rangeLabel = "$year 年",
            chartTitle = "$year 年逐月活跃分布",
            chartMode = ChartMode.BAR,
            tasksDone = raw.tasksDone,
            memos = raw.memos,
            achievements = raw.achievements,
            locations = raw.locations,
            xp = xp,
            bars = buckets.mapIndexed { m, v -> BarItem((m + 1).toString(), v) },
            summary = yearSummary(raw, buckets),
            highlights = emptyList()
        )
    }

    private fun highlightsOf(raw: Raw): List<String> {
        val out = ArrayList<String>(6)
        raw.achList.take(3).forEach { out += "🏆 解锁「${it.title.ifBlank { "成就" }}」" }
        raw.doneList.take(3).forEach { out += "✅ 完成「${it.title.ifBlank { "任务" }}」" }
        raw.memoList.take(2).forEach {
            val t = it.text.trim().replace('\n', ' ')
            out += "💭 ${if (t.length > 18) t.take(18) + "…" else t}"
        }
        return out
    }

    /** 日报总结：挑一个峰值时段说人话，全空时给一句不打击人的话 */
    private fun daySummary(raw: Raw, buckets: IntArray): String {
        val total = buckets.sum()
        if (total == 0) return "今天还没有任何记录。写下第一条世界日志，就算开局了。"
        val peak = buckets.withIndex().maxByOrNull { it.value }
        val peakText = if (peak != null && peak.value > 0) "${peak.index}:00 前后最活跃" else "分布比较均匀"
        return "今天共 $total 次记录，$peakText。" +
            "完成 ${raw.tasksDone} 个任务、记下 ${raw.memos} 条灵感，" +
            "解锁 ${raw.achievements} 个成就，获得 ${
                raw.tasksDone * XpRules.TASK_DONE + raw.achievements * XpRules.ACHIEVEMENT +
                    raw.memos * XpRules.MEMO + raw.locations * XpRules.LOCATION
            } 点经验。"
    }

    private fun weekSummary(raw: Raw, buckets: IntArray): String {
        val total = buckets.sum()
        if (total == 0) return "这一周还是空的。明天先完成一个小任务试试。"
        val active = buckets.count { it > 0 }
        return "近 7 天有 $active 天留下记录，合计 $total 次。" +
            "完成任务 ${raw.tasksDone} 个，新增灵感 ${raw.memos} 条，" +
            "解锁成就 ${raw.achievements} 个，标记足迹 ${raw.locations} 处。"
    }

    private fun yearSummary(raw: Raw, buckets: IntArray): String {
        val total = buckets.sum()
        if (total == 0) return "今年还没有记录。人生存档从第一条开始。"
        val best = buckets.withIndex().maxByOrNull { it.value }
        val bestText = if (best != null && best.value > 0) "最活跃的是 ${best.index + 1} 月" else "各月分布相近"
        return "今年共 $total 次记录，$bestText。" +
            "完成任务 ${raw.tasksDone} 个，解锁成就 ${raw.achievements} 个，" +
            "新增灵感 ${raw.memos} 条、足迹 ${raw.locations} 处。"
    }

    private fun tsOf(iso: String?): Long {
        // 只需要拿到毫秒用于按时/日/月分桶；解析不了的事件直接归到「无时间」，不计入图表
        if (iso.isNullOrBlank()) return -1L
        return runCatching { ISO_MS.parse(iso)?.time }.getOrNull() ?: -1L
    }

    private fun hourOfTs(ts: Long): Int {
        if (ts < 0) return -1
        return Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.HOUR_OF_DAY)
    }

    private fun dayOfTs(ts: Long): String? {
        if (ts < 0) return null
        return DAY_FMT.format(java.util.Date(ts))
    }

    private fun monthOfTs(ts: Long): Int {
        if (ts < 0) return -1
        return Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.MONTH)
    }

    private fun weekdayLabel(day: String): String {
        val d = runCatching { DAY_FMT.parse(day) }.getOrNull() ?: return day.takeLast(2)
        return SimpleDateFormat("EEE", Locale.CHINA).format(d)
    }

    private companion object {
        /** 图表明细上限：个人应用一天/一周的量远小于此，主要防止极端数据把内存撑爆 */
        const val DETAIL_LIMIT = 500
        val ISO_MS = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val DAY_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
