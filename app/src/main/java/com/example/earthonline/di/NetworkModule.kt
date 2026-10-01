package com.example.earthonline.di

import com.example.earthonline.data.network.WebDavService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 网络层提供：
 * - Json（备份/配置序列化，ignoreUnknownKeys 容错）
 * - OkHttpClient（AI + WebDAV 共用，统一超时）
 * - WebDavService（注入 OkHttpClient）
 * AiRepository 自带 @Inject 构造（注入 okHttp + json），无需在此重复提供。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    /**
     * v1.0.5 QA 修复：补 encodeDefaults = true。
     * kotlinx.serialization 默认「等于默认值的字段不写出」，导致 Android 导出的备份里
     * profile.xp=0、task.progress=0、parentId=null 等字段整体缺失 —— 与 Web 端
     * JSON.stringify 的全字段导出不一致，跨端导入时契约不稳（Web 侧只能靠默认值兜底）。
     * 显式写全字段 + ignoreUnknownKeys 容错读，导出语义与 Web 端对齐；主键合并逻辑不受影响。
     */
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideWebDavService(client: OkHttpClient): WebDavService = WebDavService(client)
}
