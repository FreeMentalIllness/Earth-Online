package com.example.earthonline.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.earthonline.data.local.dao.*
import com.example.earthonline.data.local.entity.*

/**
 * v1 -> v2：新增「背包自定义分类」表。
 * 必须是真迁移 —— 一旦走 destructive，用户已录入的任务 / 收藏 / 足迹会被清空。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bag_categories` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`scope` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`)" +
                ")"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_bag_categories_scope_sortOrder` " +
                "ON `bag_categories` (`scope`, `sortOrder`)"
        )
    }
}

/**
 * v2 -> v3：成就新增「分类」列。
 * 依旧是真迁移（ADD COLUMN，可空），旧成就行保留、category 为 null，由成就引擎回填。
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `achievements` ADD COLUMN `category` TEXT")
    }
}

/**
 * v3 -> v4：头像改为「原图文件 + 路径」（画质不再被压缩）。
 * 真迁移（ADD COLUMN，可空）：老用户库里已经存在的 avatarData 字节不动，
 * 打开资料页时会自动把那一份字节落成文件并回填 avatarPath（见 ProfileViewModel.load）。
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `avatarPath` TEXT")
    }
}

/**
 * 主数据库（对应 HTML 的单一主存档键 `earth_data`，本端归一化为多表）。
 * version=4；迁移链 MIGRATION_1_2 / MIGRATION_2_3 / MIGRATION_3_4
 *（fallbackToDestructiveMigration 仅作无匹配迁移时的兜底）。
 */
@Database(
    entities = [
        ProfileEntity::class,
        TaskEntity::class,
        MemoEntity::class,
        ItemEntity::class,
        AchievementEntity::class,
        CollectionEntity::class,
        LocationEntity::class,
        ActivityEntity::class,
        BagCategoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun taskDao(): TaskDao
    abstract fun memoDao(): MemoDao
    abstract fun itemDao(): ItemDao
    abstract fun achievementDao(): AchievementDao
    abstract fun collectionDao(): CollectionDao
    abstract fun locationDao(): LocationDao
    abstract fun activityDao(): ActivityDao
    abstract fun bagCategoryDao(): BagCategoryDao

    companion object {
        private const val DB_NAME = "earth_online.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
    }
}
