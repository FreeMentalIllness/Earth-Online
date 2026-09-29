package com.example.earthonline.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 键值型设置（对应 HTML 的 localStorage / EOStore 简单键）：
 *  - onboarding_done  -> 原“首次引导”分流（本端由它决定是否进引导页）
 *  - earth_theme     -> 原键 `earth_theme`（light / dark / system）
 *  - earth_wallpaper -> 原键 `earth_wallpaper`（配置 JSON；图片二进制存沙盒，不进此表）
 *  - earth_notify    -> 原键 `earth_notify`（到期提醒开关）
 *  - ai_config       -> 原 state.aiConfig（baseUrl/apiKey/model 的 JSON）
 *  - webdav_config   -> 原键 `earth_online_webdav_v1`（统一收口到 DataStore，不再明文裸存 localStorage）
 *  - earth_autosync  -> 自动同步开关（启动拉取 / 切后台推送）
 *  - earth_last_sync -> 上次同步时间（ISO，冲突比对基准：与云端 exportedAt 比大小）
 */
@Singleton
class SettingsDataStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val Context.dataStore by preferencesDataStore(name = "earth_settings")
    private val ds = context.dataStore

    companion object {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val THEME = stringPreferencesKey("earth_theme")
        val WALLPAPER = stringPreferencesKey("earth_wallpaper")
        val WALLPAPER_ALPHA = floatPreferencesKey("earth_wallpaper_alpha")
        val NOTIFY = booleanPreferencesKey("earth_notify")
        val AI_CONFIG = stringPreferencesKey("ai_config")
        val WEBDAV_CONFIG = stringPreferencesKey("webdav_config")
        val AUTO_SYNC = booleanPreferencesKey("earth_autosync")
        val LAST_SYNC = stringPreferencesKey("earth_last_sync")
        /** v1.2.0：彩蛋计数器（对应 Web 的 state.eggs）。只有无法从数据推导的事件才存这里。 */
        val EGG_BLANK_TITLE = intPreferencesKey("egg_blank_title")
        /** v1.2.0：字号档位（std / lg / xl，对应 Web 的 earth_font_scale） */
        val FONT_SCALE = stringPreferencesKey("earth_font_scale")
        /** v1.2.1：成就解锁音效开关（对应 Web 的 earth_ach_sound） */
        val ACH_SOUND = booleanPreferencesKey("earth_ach_sound")
        /** 主页「人生时间轴」用户自定义里程碑事件（JSON 列表，避免动 DB 表结构） */
        val CUSTOM_TIMELINE = stringPreferencesKey("custom_timeline_events")
        /** 主页「最近动态」显示类型筛选（JSON 集合：task/ach/item/memo，全空则显示全部） */
        val HOME_FEED_FILTER = stringPreferencesKey("home_feed_filter")
    }

    val onboardingDone: Flow<Boolean> = ds.data.map { it[ONBOARDING_DONE] ?: false }
    val theme: Flow<String> = ds.data.map { it[THEME] ?: "system" }
    val wallpaper: Flow<String> = ds.data.map { it[WALLPAPER] ?: "" }

    /** 壁纸不透明度 0.05 ~ 0.90（0 = 完全看不见，1 = 完全遮挡内容，故上限收紧） */
    val wallpaperAlpha: Flow<Float> = ds.data.map { (it[WALLPAPER_ALPHA] ?: 0.35f).coerceIn(0.05f, 0.9f) }
    val notify: Flow<Boolean> = ds.data.map { it[NOTIFY] ?: false }
    val aiConfig: Flow<String> = ds.data.map { it[AI_CONFIG] ?: "" }
    val webdavConfig: Flow<String> = ds.data.map { it[WEBDAV_CONFIG] ?: "" }
    /** 自动同步开关：默认开启（配置好 WebDAV 即生效，未配置时不会发起任何请求） */
    val autoSync: Flow<Boolean> = ds.data.map { it[AUTO_SYNC] ?: true }
    /** 上次同步时间（空串 = 从未同步过） */
    val lastSyncAt: Flow<String> = ds.data.map { it[LAST_SYNC] ?: "" }

    /** 彩蛋：空白标题保存被拒次数（≥3 解锁「空白也是一种态度」） */
    val eggBlankTitle: Flow<Int> = ds.data.map { it[EGG_BLANK_TITLE] ?: 0 }

    /** 字号档位：std / lg / xl */
    val fontScale: Flow<String> = ds.data.map {
        when (it[FONT_SCALE]) { "lg", "xl" -> it[FONT_SCALE]!! else -> "std" }
    }

    suspend fun setOnboardingDone(v: Boolean) = ds.edit { it[ONBOARDING_DONE] = v }
    suspend fun setTheme(v: String) = ds.edit { it[THEME] = v }
    suspend fun setWallpaper(v: String) = ds.edit { it[WALLPAPER] = v }
    suspend fun setWallpaperAlpha(v: Float) =
        ds.edit { it[WALLPAPER_ALPHA] = v.coerceIn(0.05f, 0.9f) }
    suspend fun setNotify(v: Boolean) = ds.edit { it[NOTIFY] = v }
    suspend fun setAiConfig(v: String) = ds.edit { it[AI_CONFIG] = v }
    suspend fun setWebdavConfig(v: String) = ds.edit { it[WEBDAV_CONFIG] = v }
    suspend fun setAutoSync(v: Boolean) = ds.edit { it[AUTO_SYNC] = v }
    suspend fun setLastSyncAt(v: String) = ds.edit { it[LAST_SYNC] = v }

    /** 空白标题被拒时 +1（上限 999999，防止有人挂机刷爆 int） */
    suspend fun bumpEggBlankTitle() = ds.edit {
        it[EGG_BLANK_TITLE] = ((it[EGG_BLANK_TITLE] ?: 0) + 1).coerceAtMost(999999)
    }

    /** 设置字号档位（非法值一律回落 std） */
    suspend fun setFontScale(v: String) = ds.edit {
        it[FONT_SCALE] = if (v == "lg" || v == "xl") v else "std"
    }

    /** 成就解锁音效开关（默认开启） */
    val achSound: Flow<Boolean> = ds.data.map { it[ACH_SOUND] ?: true }

    /** 主页时间轴自定义事件（JSON 列表，空串 = 无自定义事件） */
    val customTimelineJson: Flow<String> = ds.data.map { it[CUSTOM_TIMELINE] ?: "" }

    /** 主页动态流筛选（JSON 集合，空串 = 显示全部） */
    val homeFeedFilterJson: Flow<String> = ds.data.map { it[HOME_FEED_FILTER] ?: "" }

    suspend fun setAchSound(v: Boolean) = ds.edit { it[ACH_SOUND] = v }
    suspend fun setCustomTimelineJson(v: String) = ds.edit { it[CUSTOM_TIMELINE] = v }
    suspend fun setHomeFeedFilterJson(v: String) = ds.edit { it[HOME_FEED_FILTER] = v }
}
