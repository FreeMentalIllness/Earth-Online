package com.example.earthonline.di

import android.content.Context
import com.example.earthonline.data.local.AppDatabase
import com.example.earthonline.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据库与 DAO 提供。
 * - AppDatabase 单例。
 * - 各 DAO 由 Room 生成、无 @Inject 构造函数，必须在此 @Provides。
 * - Repository / SettingsDataStore 自身带 @Inject constructor，由 Hilt 自动绑定，不需在此重复 @Provides（避免重复绑定）。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        AppDatabase.getInstance(ctx)

    @Provides fun provideProfileDao(db: AppDatabase) = db.profileDao()
    @Provides fun provideTaskDao(db: AppDatabase) = db.taskDao()
    @Provides fun provideMemoDao(db: AppDatabase) = db.memoDao()
    @Provides fun provideItemDao(db: AppDatabase) = db.itemDao()
    @Provides fun provideAchievementDao(db: AppDatabase) = db.achievementDao()
    @Provides fun provideCollectionDao(db: AppDatabase) = db.collectionDao()
    @Provides fun provideLocationDao(db: AppDatabase) = db.locationDao()
    @Provides fun provideActivityDao(db: AppDatabase) = db.activityDao()
    @Provides fun provideBagCategoryDao(db: AppDatabase) = db.bagCategoryDao()
}

/**
 * 预留模块位：当某些 Repository 需要手动构造（如注入非 @Inject 依赖）时在此 @Provides。
 * 当前所有 Repository 均为 @Inject 自动绑定。
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule
