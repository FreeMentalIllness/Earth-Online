package com.example.earthonline.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.data.network.AiConfig
import com.example.earthonline.data.network.AiRepository
import com.example.earthonline.data.network.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class UiMessage(val role: String, val content: String)

@HiltViewModel
class AiViewModel @Inject constructor(
    private val repo: AiRepository,
    private val settings: SettingsDataStore,
    private val json: Json
) : ViewModel() {

    private val _messages = MutableStateFlow<List<UiMessage>>(
        listOf(UiMessage("assistant", "你好，我是地球Online的「系统」。告诉我你今天想完成什么？"))
    )
    val messages = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input = _input.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    val aiConfigFlow = settings.aiConfig

    fun onInputChange(s: String) { _input.value = s }

    fun parseAiConfig(s: String): AiConfig =
        runCatching { json.decodeFromString<AiConfig>(s) }.getOrDefault(AiConfig())

    fun saveAiConfig(c: AiConfig) = viewModelScope.launch {
        settings.setAiConfig(json.encodeToString(AiConfig.serializer(), c))
    }

    fun clearError() { _error.value = null }

    fun send() {
        val text = _input.value.trim()
        if (text.isBlank() || _loading.value) return
        _input.value = ""
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            val cfg = parseAiConfig(settings.aiConfig.first())
            // v1.2.1：模型名默认留空，必须用户自己填 —— 这里提前挡掉，
            // 否则多数接口会直接回一段 "model is required" 的英文错误。
            when {
                cfg.baseUrl.isBlank() || cfg.apiKey.isBlank() -> {
                    _loading.value = false
                    _error.value = "请先在「设置 → AI 对话」中配置 Base URL 与 API Key"
                    return@launch
                }
                cfg.model.isBlank() -> {
                    _loading.value = false
                    _error.value = "请先在「设置 → AI 对话」中填写模型名（按服务商文档照抄）"
                    return@launch
                }
            }
            val history = _messages.value.map { ChatMessage(it.role, it.content) } + ChatMessage("user", text)
            _messages.value = _messages.value + UiMessage("user", text)
            try {
                val resp = repo.chat(cfg, history)
                val content = resp.choices.firstOrNull()?.message?.content
                    ?: resp.error?.message
                    ?: "（无返回内容）"
                _messages.value = _messages.value + UiMessage("assistant", content)
            } catch (e: Exception) {
                // 异常映射：不再把英文堆栈原文怼给用户，按原因给可操作的中文提示
                val friendly = when (e) {
                    is java.net.UnknownHostException, is java.net.ConnectException ->
                        "无网络连接，请检查网络后重试"
                    is java.net.SocketTimeoutException ->
                        "请求超时，请稍后重试"
                    is java.io.IOException ->
                        "网络异常，请检查网络后重试"
                    else -> "服务异常：${e.message ?: "未知错误"}"
                }
                _messages.value = _messages.value + UiMessage("assistant", friendly)
            } finally {
                _loading.value = false
            }
        }
    }
}
