package com.example.earthonline.data.repository

import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.util.XpRules
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1.0.4：XP 对账器 —— profile.xp 的唯一写入方。
 *
 * 单一事实源设计：XP 总量 = XpRules.totalXp（任务/成就/日志/足迹/物品/照片六类
 * 可枚举计数的派生值）+ 自定义里程碑流水累计（xp_events 中 kind=custom 的 SUM）。
 * 派生值无法被「忘记加列」式增量更新搞脏，因此每次对账全量重算：
 *  - 主页加载时对账一次（App 启动即触达）；
 *  - 导出 JSON 前再对账一次，保证导出的 xp 与报告页/小组件展示口径永远一致。
 * xp_events 中 task/ach 流水仅作审计，不参与总量计算（总量从计数派生，天然防漂移）。
 */
@Singleton
class XpLedger @Inject constructor(
    private val taskRepo: TaskRepository,
    private val memoRepo: MemoRepository,
    private val achievementRepo: AchievementRepository,
    private val locationRepo: LocationRepository,
    private val itemRepo: ItemRepository,
    private val profileRepo: ProfileRepository,
    private val xpEventRepo: XpEventRepository,
    private val settings: SettingsDataStore,
    private val json: Json
) {

    /** 记忆相册照片数（DataStore JSON 列表，仅取长度） */
    private suspend fun photoCount(): Int = runCatching {
        val txt = settings.memoryPhotosJson.first()
        if (txt.isBlank()) 0
        else (json.parseToJsonElement(txt) as? kotlinx.serialization.json.JsonArray)?.size ?: 0
    }.getOrDefault(0)

    /** 当前应持有的 XP 总量（派生六类 + 自定义里程碑累计） */
    suspend fun expectedXp(): Int {
        val derived = XpRules.totalXp(
            tasksDone = taskRepo.doneCount().first(),
            achievements = achievementRepo.unlockedCount().first(),
            memos = memoRepo.count().first(),
            locations = locationRepo.count().first(),
            items = itemRepo.count().first(),
            photos = photoCount()
        )
        return derived + xpEventRepo.customXpSum()
    }

    /**
     * 对账校准：profile.xp 与期望值不一致时写回（单行 upsert，幂等）。
     * 返回校准后的最终值（profile 不存在时返回期望值但不落库）。
     */
    suspend fun reconcile(): Int {
        val expected = expectedXp()
        val p = profileRepo.get() ?: return expected
        if (p.xp != expected) profileRepo.upsert(p.copy(xp = expected))
        return expected
    }
}
