package com.example.earthonline.data.network

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/** OpenAI 兼容的 chat/completions 接口 */
interface AiApi {
    @POST("chat/completions")
    suspend fun chat(
        @Header("Authorization") authorization: String,
        @Body body: ChatRequest
    ): ChatResponse
}
