package com.example.earthonline.data.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 对话仓库。
 * baseUrl / apiKey 运行时可变，因此每次请求按当前 AiConfig 临时构建一个 Retrofit 实例，
 * 避免全局可变 baseUrl 带来的状态不一致。
 */
@Singleton
class AiRepository @Inject constructor(
    private val okHttp: OkHttpClient,
    private val json: Json
) {
    private fun apiFor(baseUrl: String): AiApi {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val retrofit = Retrofit.Builder()
            .baseUrl(base)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(AiApi::class.java)
    }

    suspend fun chat(config: AiConfig, messages: List<ChatMessage>): ChatResponse {
        val api = apiFor(config.baseUrl)
        // v1.2.1：不再兜底成 gpt-3.5-turbo。模型名为空时让服务端返回它自己的报错，
        // 或者在 ViewModel 里提前拦下并提示「请先填写模型名」——比悄悄换一个
        // 多半不存在的模型、再看一段看不懂的英文错误要好。
        return api.chat(
            authorization = "Bearer ${config.apiKey}",
            body = ChatRequest(model = config.model, messages = messages)
        )
    }
}
