package com.example.earthonline.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.earthonline.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: ProfileEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY sort_order ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parentId IS NULL ORDER BY sort_order ASC")
    fun observeRoots(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parentId = :pid ORDER BY sort_order ASC")
    fun observeChildren(pid: String): Flow<List<TaskEntity>>

    @Query("SELECT COUNT(*) FROM tasks")
    fun count(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE doneAt IS NOT NULL")
    fun doneCount(): Flow<Int>

    /** 人生时间轴：最近完成的任务（限量，避免首页加载全表） */
    @Query("SELECT * FROM tasks WHERE doneAt IS NOT NULL ORDER BY doneAt DESC LIMIT :limit")
    fun observeDoneRecent(limit: Int): Flow<List<TaskEntity>>

    /* ---------------- v1.0.0：周期性报告（日报 / 周报 / 年报） ----------------
       区间一律「左闭右开」[from, to)，与 DateUtils.dayStartIso 生成的边界配套。
       doneAt 是定长 UTC ISO 串（yyyy-MM-dd'T'HH:mm:ss'Z'），字典序即时间序，
       所以可以直接用字符串比较，不需要在 SQL 里做日期转换（SQLite 也没有本地时区概念）。 */

    @Query(
        "SELECT COUNT(*) FROM tasks WHERE doneAt IS NOT NULL " +
            "AND doneAt >= :from AND doneAt < :to"
    )
    fun doneCountBetween(from: String, to: String): Flow<Int>

    @Query(
        "SELECT * FROM tasks WHERE doneAt IS NOT NULL " +
            "AND doneAt >= :from AND doneAt < :to ORDER BY doneAt DESC LIMIT :limit"
    )
    fun doneBetween(from: String, to: String, limit: Int): Flow<List<TaskEntity>>

    /**
     * v1.2.1 全局搜索：标题 / 备注模糊匹配，限量 20。
     * 走 SQL 而不是「拉全表再 filter」—— 数据量大了以后，全表进内存再过滤的成本
     * 比一条带 LIMIT 的 LIKE 高一个数量级。IFNULL 是必须的：note 可空，
     * NULL LIKE '%x%' 在 SQLite 里结果是 NULL（不是 false），整条会被静默漏掉。
     */
    @Query(
        "SELECT * FROM tasks WHERE title LIKE '%' || :q || '%' " +
            "OR IFNULL(note, '') LIKE '%' || :q || '%' ORDER BY sort_order ASC LIMIT 20"
    )
    fun search(q: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(t: TaskEntity)

    @Update
    suspend fun update(t: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM tasks WHERE parentId = :pid")
    suspend fun deleteChildren(pid: String)
}

@Dao
interface MemoDao {
    @Query("SELECT * FROM memos ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MemoEntity>>

    /** 首页只取最近 N 条，不再把全表拉进内存 */
    @Query("SELECT * FROM memos ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<MemoEntity>>

    @Query("SELECT COUNT(*) FROM memos")
    fun count(): Flow<Int>

    /** v1.0.0：区间内新增灵感数（报告用，左闭右开） */
    @Query("SELECT COUNT(*) FROM memos WHERE createdAt >= :from AND createdAt < :to")
    fun countBetween(from: String, to: String): Flow<Int>

    /** v1.0.0：区间内灵感明细（报告图表按小时/天/月聚合用） */
    @Query(
        "SELECT * FROM memos WHERE createdAt >= :from AND createdAt < :to " +
            "ORDER BY createdAt DESC LIMIT :limit"
    )
    fun between(from: String, to: String, limit: Int): Flow<List<MemoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(m: MemoEntity)

    @Query("DELETE FROM memos WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT COUNT(*) FROM items")
    fun count(): Flow<Int>

    /** v1.2.1 全局搜索：物品名 / 描述模糊匹配 */
    @Query(
        "SELECT * FROM items WHERE name LIKE '%' || :q || '%' " +
            "OR IFNULL(description, '') LIKE '%' || :q || '%' ORDER BY createdAt DESC LIMIT 20"
    )
    fun search(q: String): Flow<List<ItemEntity>>

    /** 已使用的自定义分类数（首页概览卡「分类 N」） */
    @Query("SELECT COUNT(DISTINCT category) FROM items WHERE category IS NOT NULL AND category != ''")
    fun categoryCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(i: ItemEntity)

    @Update
    suspend fun update(i: ItemEntity)

    /** 删除分类时把引用它的物品回落为「未分类」（物品本身保留） */
    @Query("UPDATE items SET category = NULL WHERE category = :name")
    suspend fun clearCategory(name: String)

    /** 分类改名后同步条目上的分类名（条目存的是分类名字符串，不同步会失联） */
    @Query("UPDATE items SET category = :newName WHERE category = :oldName")
    suspend fun renameCategory(oldName: String, newName: String)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements ORDER BY type ASC, title ASC")
    fun observeAll(): Flow<List<AchievementEntity>>

    @Query("SELECT COUNT(*) FROM achievements WHERE unlocked = 1")
    fun unlockedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM achievements")
    fun totalCount(): Flow<Int>

    /** v1.0.0：区间内解锁成就数（报告用；unlockedAt 可空，NULL 行不计入） */
    @Query(
        "SELECT COUNT(*) FROM achievements WHERE unlocked = 1 AND unlockedAt IS NOT NULL " +
            "AND unlockedAt >= :from AND unlockedAt < :to"
    )
    fun unlockedCountBetween(from: String, to: String): Flow<Int>

    /** v1.0.0：区间内解锁成就明细（报告图表聚合用） */
    @Query(
        "SELECT * FROM achievements WHERE unlocked = 1 AND unlockedAt IS NOT NULL " +
            "AND unlockedAt >= :from AND unlockedAt < :to ORDER BY unlockedAt DESC LIMIT :limit"
    )
    fun unlockedBetween(from: String, to: String, limit: Int): Flow<List<AchievementEntity>>

    /** 人生时间轴：最近解锁的成就（限量） */
    @Query(
        "SELECT * FROM achievements WHERE unlocked = 1 AND unlockedAt IS NOT NULL " +
            "ORDER BY unlockedAt DESC LIMIT :limit"
    )
    fun observeUnlockedRecent(limit: Int): Flow<List<AchievementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(a: AchievementEntity)

    @Update
    suspend fun update(a: AchievementEntity)

    @Query("DELETE FROM achievements WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT COUNT(*) FROM collections")
    fun count(): Flow<Int>

    /** v1.2.1 全局搜索：收藏标题 / 备注模糊匹配 */
    @Query(
        "SELECT * FROM collections WHERE title LIKE '%' || :q || '%' " +
            "OR IFNULL(note, '') LIKE '%' || :q || '%' ORDER BY createdAt DESC LIMIT 20"
    )
    fun search(q: String): Flow<List<CollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: CollectionEntity)

    @Update
    suspend fun update(c: CollectionEntity)

    /** 删除分类时把引用它的收藏回落为「未分类」（收藏本身保留） */
    @Query("UPDATE collections SET category = NULL WHERE category = :name")
    suspend fun clearCategory(name: String)

    /** 分类改名后同步条目上的分类名 */
    @Query("UPDATE collections SET category = :newName WHERE category = :oldName")
    suspend fun renameCategory(oldName: String, newName: String)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY date DESC")
    fun observeAll(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<LocationEntity>>

    @Query("SELECT COUNT(*) FROM locations")
    fun count(): Flow<Int>

    /** v1.0.0：区间内新增足迹数。locations.date 存的是本地日键（YYYY-MM-DD），
     *  不是 ISO 时间戳，所以这里是闭区间 [fromDay, toDay] —— 与上面几张表的语义不同，勿混用。 */
    @Query("SELECT COUNT(*) FROM locations WHERE date >= :fromDay AND date <= :toDay")
    fun countBetweenDays(fromDay: String, toDay: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(l: LocationEntity)

    @Update
    suspend fun update(l: LocationEntity)

    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities ORDER BY time DESC")
    fun observeAll(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities ORDER BY time DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActivityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(a: ActivityEntity)

    /** 删除单条动态（用户自定义动态的删除入口） */
    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun delete(id: String)

    /** 裁剪：仅保留最近 50 条，超出部分删最旧 */
    @Query("DELETE FROM activities WHERE id NOT IN (SELECT id FROM activities ORDER BY time DESC LIMIT 50)")
    suspend fun trim()
}
