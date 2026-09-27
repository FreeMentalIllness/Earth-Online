package com.example.earthonline.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 分类作用域：物品 */
const val SCOPE_ITEM = "item"

/** 分类作用域：收藏 */
const val SCOPE_COLLECTION = "collection"

/**
 * 背包自定义分类（物品 / 收藏各一套，由 scope 区分）。
 *
 * 与「从既有条目里提取 category 字符串」的区别：
 * 分类是独立存在的实体，用户可以预先建好、重命名、删除；
 * 删除分类时引用它的条目会回落为「未分类」（条目本身不删）。
 *
 * 对应 HTML 的 state.collectionCategories，本端同时覆盖物品与收藏两个 Tab。
 */
@Entity(
    tableName = "bag_categories",
    indices = [Index(value = ["scope", "sortOrder"])]
)
data class BagCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** 归属：[SCOPE_ITEM] 或 [SCOPE_COLLECTION] */
    val scope: String,
    /** 排序号，越小越靠前 */
    val sortOrder: Int = 0
)
