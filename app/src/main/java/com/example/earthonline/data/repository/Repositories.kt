package com.example.earthonline.data.repository

import com.example.earthonline.data.local.dao.*
import com.example.earthonline.data.local.entity.*
import com.example.earthonline.util.nowIso
import com.example.earthonline.util.todayStr
import com.example.earthonline.util.uid
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(private val dao: ProfileDao) {
    fun observe(): Flow<ProfileEntity?> = dao.observe()
    suspend fun get(): ProfileEntity? = dao.get()
    suspend fun upsert(p: ProfileEntity) = dao.upsert(p)
}

@Singleton
class TaskRepository @Inject constructor(private val dao: TaskDao) {
    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()
    fun observeRoots(): Flow<List<TaskEntity>> = dao.observeRoots()
    fun observeChildren(pid: String): Flow<List<TaskEntity>> = dao.observeChildren(pid)
    fun count(): Flow<Int> = dao.count()
    fun doneCount(): Flow<Int> = dao.doneCount()
    fun observeDoneRecent(limit: Int): Flow<List<TaskEntity>> = dao.observeDoneRecent(limit)
    /** v1.2.1 全局搜索 */
    fun search(q: String): Flow<List<TaskEntity>> = dao.search(q)
    /** v1.0.0 报告：区间内完成任务数（左闭右开） */
    fun doneCountBetween(from: String, to: String): Flow<Int> = dao.doneCountBetween(from, to)
    /** v1.0.0 报告：区间内完成任务明细（图表聚合用） */
    fun doneBetween(from: String, to: String, limit: Int): Flow<List<TaskEntity>> = dao.doneBetween(from, to, limit)
    suspend fun get(id: String) = dao.get(id)
    suspend fun insert(t: TaskEntity) = dao.insert(t)
    suspend fun update(t: TaskEntity) = dao.update(t)
    suspend fun delete(id: String) = dao.delete(id)
    /** 级联删除子任务（对应 HTML deleteTaskCascade） */
    suspend fun deleteCascade(id: String) {
        dao.deleteChildren(id)
        dao.delete(id)
    }
}

@Singleton
class MemoRepository @Inject constructor(private val dao: MemoDao) {
    fun observeAll(): Flow<List<MemoEntity>> = dao.observeAll()
    fun observeRecent(limit: Int): Flow<List<MemoEntity>> = dao.observeRecent(limit)
    fun count(): Flow<Int> = dao.count()
    /** v1.0.0 报告：区间内新增灵感数 / 明细 */
    fun countBetween(from: String, to: String): Flow<Int> = dao.countBetween(from, to)
    fun between(from: String, to: String, limit: Int): Flow<List<MemoEntity>> = dao.between(from, to, limit)
    suspend fun insert(m: MemoEntity) = dao.insert(m)
    suspend fun delete(id: String) = dao.delete(id)
}

@Singleton
class ItemRepository @Inject constructor(private val dao: ItemDao) {
    fun observeAll(): Flow<List<ItemEntity>> = dao.observeAll()
    fun count(): Flow<Int> = dao.count()
    fun categoryCount(): Flow<Int> = dao.categoryCount()
    /** v1.2.1 全局搜索 */
    fun search(q: String): Flow<List<ItemEntity>> = dao.search(q)
    /** v1.0.3 报告：区间内拾取物品数（日键闭区间） */
    fun countBetweenDays(fromDay: String, toDay: String): Flow<Int> = dao.countBetweenDays(fromDay, toDay)
    suspend fun insert(i: ItemEntity) = dao.insert(i)
    suspend fun update(i: ItemEntity) = dao.update(i)
    suspend fun clearCategory(name: String) = dao.clearCategory(name)
    suspend fun renameCategory(oldName: String, newName: String) = dao.renameCategory(oldName, newName)
    suspend fun delete(id: String) = dao.delete(id)
}

@Singleton
class AchievementRepository @Inject constructor(private val dao: AchievementDao) {
    fun observeAll(): Flow<List<AchievementEntity>> = dao.observeAll()
    fun unlockedCount(): Flow<Int> = dao.unlockedCount()
    fun totalCount(): Flow<Int> = dao.totalCount()
    fun observeUnlockedRecent(limit: Int): Flow<List<AchievementEntity>> = dao.observeUnlockedRecent(limit)
    /** v1.0.0 报告：区间内解锁成就数 / 明细 */
    fun unlockedCountBetween(from: String, to: String): Flow<Int> = dao.unlockedCountBetween(from, to)
    fun unlockedBetween(from: String, to: String, limit: Int): Flow<List<AchievementEntity>> =
        dao.unlockedBetween(from, to, limit)
    suspend fun insert(a: AchievementEntity) = dao.insert(a)
    suspend fun update(a: AchievementEntity) = dao.update(a)
    suspend fun delete(id: String) = dao.delete(id)
}

@Singleton
class CollectionRepository @Inject constructor(private val dao: CollectionDao) {
    fun observeAll(): Flow<List<CollectionEntity>> = dao.observeAll()
    fun count(): Flow<Int> = dao.count()
    /** v1.2.1 全局搜索 */
    fun search(q: String): Flow<List<CollectionEntity>> = dao.search(q)
    suspend fun insert(c: CollectionEntity) = dao.insert(c)
    suspend fun update(c: CollectionEntity) = dao.update(c)
    suspend fun clearCategory(name: String) = dao.clearCategory(name)
    suspend fun renameCategory(oldName: String, newName: String) = dao.renameCategory(oldName, newName)
    suspend fun delete(id: String) = dao.delete(id)
}

/**
 * 背包自定义分类仓储。
 * 删除分类是「两步」：先把引用该分类的条目回落未分类，再删分类行 —— 顺序不能反，
 * 否则条目上会残留一个已不存在的分类名，筛选时表现为幽灵 chip。
 */
@Singleton
class BagCategoryRepository @Inject constructor(private val dao: BagCategoryDao) {
    fun observeByScope(scope: String): Flow<List<BagCategoryEntity>> = dao.observeByScope(scope)
    fun observeAll(): Flow<List<BagCategoryEntity>> = dao.observeAll()
    suspend fun get(id: String) = dao.get(id)
    suspend fun maxOrder(scope: String) = dao.maxOrder(scope)
    suspend fun insert(c: BagCategoryEntity) = dao.insert(c)
    suspend fun update(c: BagCategoryEntity) = dao.update(c)
    suspend fun delete(id: String) = dao.delete(id)
    suspend fun countSameName(scope: String, name: String, excludeId: String = "") =
        dao.countSameName(scope, name, excludeId)
}

@Singleton
class LocationRepository @Inject constructor(private val dao: LocationDao) {
    fun observeAll(): Flow<List<LocationEntity>> = dao.observeAll()
    fun observeRecent(limit: Int): Flow<List<LocationEntity>> = dao.observeRecent(limit)
    fun count(): Flow<Int> = dao.count()
    /** v1.0.0 报告：区间内新增足迹数（日键闭区间） */
    fun countBetweenDays(fromDay: String, toDay: String): Flow<Int> = dao.countBetweenDays(fromDay, toDay)
    suspend fun insert(l: LocationEntity) = dao.insert(l)
    suspend fun update(l: LocationEntity) = dao.update(l)
    suspend fun delete(id: String) = dao.delete(id)
}

@Singleton
class ActivityRepository @Inject constructor(private val dao: ActivityDao) {
    fun observeRecent(): Flow<List<ActivityEntity>> = dao.observeAll()
    fun observeRecent(limit: Int): Flow<List<ActivityEntity>> = dao.observeRecent(limit)
    suspend fun add(a: ActivityEntity) {
        dao.insert(a)
        dao.trim()
    }

    /** 删除单条动态（用户自定义动态的删除入口） */
    suspend fun delete(id: String) = dao.delete(id)
}

/**
 * v1.0.4：XP 流水仓库 —— 每一笔经验进出落一条 xp_events。
 * 纯审计流水：任务/成就的 XP 总量仍由 XpRules.totalXp 从可枚举计数派生，
 * 自定义里程碑（kind=custom）则靠本表 SUM 累计（无法从行数推导）。
 */
@Singleton
class XpEventRepository @Inject constructor(private val dao: XpEventDao) {
    fun observeRecent(limit: Int = 50): Flow<List<XpEventEntity>> = dao.observeRecent(limit)

    /** 自定义里程碑累计 XP（派生口径之外的增量部分，对账时叠加） */
    suspend fun customXpSum(): Int = dao.sumByKind("custom")

    suspend fun record(kind: String, amount: Int, reason: String) {
        dao.insert(
            XpEventEntity(
                id = uid("xpe"),
                kind = kind,
                amount = amount,
                reason = reason,
                createdAt = nowIso(),
                date = todayStr()
            )
        )
    }
}
