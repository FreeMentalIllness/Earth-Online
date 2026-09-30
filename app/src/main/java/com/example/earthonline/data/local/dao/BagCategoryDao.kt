package com.example.earthonline.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.earthonline.data.local.entity.BagCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BagCategoryDao {
    @Query("SELECT * FROM bag_categories WHERE scope = :scope ORDER BY sortOrder ASC, name ASC")
    fun observeByScope(scope: String): Flow<List<BagCategoryEntity>>

    @Query("SELECT * FROM bag_categories ORDER BY scope ASC, sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<BagCategoryEntity>>

    @Query("SELECT * FROM bag_categories WHERE id = :id")
    suspend fun get(id: String): BagCategoryEntity?

    /** 当前作用域下最大的排序号（用于追加新分类时排在末尾），空表返回 null */
    @Query("SELECT MAX(sortOrder) FROM bag_categories WHERE scope = :scope")
    suspend fun maxOrder(scope: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: BagCategoryEntity)

    @Update
    suspend fun update(c: BagCategoryEntity)

    @Query("DELETE FROM bag_categories WHERE id = :id")
    suspend fun delete(id: String)

    /** 同作用域下是否已存在同名分类（重命名 / 新增时查重） */
    @Query("SELECT COUNT(*) FROM bag_categories WHERE scope = :scope AND name = :name AND id != :excludeId")
    suspend fun countSameName(scope: String, name: String, excludeId: String): Int

    /** v1.0.5 清空数据：整表清空（自定义分类同属用户数据） */
    @Query("DELETE FROM bag_categories")
    suspend fun deleteAll()
}
