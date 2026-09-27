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
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

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
