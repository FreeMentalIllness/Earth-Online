package com.example.earthonline.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 单条对话消息（OpenAI 兼容格式） */
@Serializable
data class ChatMessage(val role: String, val content: String)

/** chat/completions 请求体 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)

@Serializable
data class ChatChoice(
    val message: ChatMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
    val index: Int = 0
)

@Serializable
data class ChatError(
    val message: String = "",
    val type: String = ""
)

/** chat/completions 响应体 */
@Serializable
data class ChatResponse(
    val id: String = "",
    val choices: List<ChatChoice> = emptyList(),
    val error: ChatError? = null
)

/** AI 配置（持久化于 DataStore 的 ai_config 键，JSON） */
@Serializable
data class AiConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    /** v1.2.1：模型名默认留空，由用户按自己服务商的文档填写。
     *  之前默认填 gpt-3.5-turbo，会让用户误以为这是官方推荐/可用的模型，
     *  而实际各家地址与模型名都不一样。留空 + 发送前校验更诚实。 */
    val model: String = ""
)

/**
 * WebDAV 配置（持久化于 DataStore 的 webdav_config 键，JSON）。
 * path 为远端文件路径，留空则用 CloudSyncManager.DEFAULT_REMOTE_PATH（与 Web 端默认一致），
 * 新增字段带默认值，旧存档反序列化不受影响。
 */
@Serializable
data class WebDavConfig(
    val url: String = "",
    val user: String = "",
    val pass: String = "",
    val path: String = ""
)
