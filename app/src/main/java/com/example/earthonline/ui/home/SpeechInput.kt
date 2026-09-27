package com.example.earthonline.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 系统是否提供可用的语音识别服务。
 * 国内机型多数由 ROM 厂商（讯飞 / 百度 / Google）提供识别服务；部分精简系统或模拟器没有，
 * 此时返回 false —— 调用方应降级为手动输入，而不是让用户点了没反应。
 */
fun speechAvailable(context: Context): Boolean =
    runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

/**
 * Android 语音输入控制器（android.speech.SpeechRecognizer）。
 *
 * 用法：
 * ```
 * val speech = remember(context) { SpeechInputController(context) }
 * DisposableEffect(speech) { onDispose { speech.destroy() } }
 * speech.start(onFinal = { text -> memoInput += text }, onError = { msg -> toast(msg) })
 * ```
 *
 * 注意：SpeechRecognizer 必须在主线程创建 / 销毁，且用完必须 destroy()，否则会泄漏识别服务连接。
 */
class SpeechInputController(private val context: Context) {

    /** 是否正在收音 */
    var listening by mutableStateOf(false)
        private set

    /** 最近一次「部分结果」（边说边出字），可用于实时回显 */
    var partial by mutableStateOf("")
        private set

    private var recognizer: SpeechRecognizer? = null
    private var onFinalCb: ((String) -> Unit)? = null
    private var onErrorCb: ((String) -> Unit)? = null
    private var finalText = ""

    /**
     * 开始一次识别。
     * @param onFinal 识别完成后的最终文本（只回调一次非空结果）
     * @param onError 失败原因（已转成中文可直接提示用户的文案）
     */
    fun start(onFinal: (String) -> Unit, onError: (String) -> Unit) {
        if (listening) return
        onFinalCb = onFinal
        onErrorCb = onError
        finalText = ""
        partial = ""

        val rec = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (rec == null) {
            onError("无法创建语音识别服务，请手动输入")
            return
        }
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() { listening = true }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }

            override fun onPartialResults(partialResults: Bundle?) {
                val t = firstText(partialResults)
                if (!t.isNullOrBlank()) partial = t
            }

            override fun onResults(results: Bundle?) {
                val t = firstText(results)
                listening = false
                if (!t.isNullOrBlank()) {
                    finalText = t
                    onFinalCb?.invoke(t)
                } else {
                    onErrorCb?.invoke("没有听清，请再说一次")
                }
                release()
            }

            override fun onError(error: Int) {
                listening = false
                // 部分机型在报错前已吐出部分结果，优先使用，避免用户白说一遍
                if (finalText.isBlank() && partial.isNotBlank()) {
                    onFinalCb?.invoke(partial)
                } else {
                    onErrorCb?.invoke(errorMessage(error))
                }
                release()
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // 中文优先；设备不支持 zh-CN 时回落到系统默认语言
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.CHINA.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话…")
        }
        runCatching { rec.startListening(intent) }
            .onFailure { listening = false; onError("语音识别启动失败，请手动输入"); release() }
    }

    /** 提前结束（用户再点一次麦克风光标） */
    fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    /** 页面离开时调用，释放识别服务 */
    fun destroy() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        listening = false
    }

    private fun release() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        listening = false
    }

    private fun firstText(bundle: Bundle?): String? {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return list?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "录音失败，请检查麦克风是否被占用"
        SpeechRecognizer.ERROR_CLIENT -> "语音识别客户端异常，请手动输入"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有麦克风权限，请在系统设置中开启"
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络不可用，语音识别需要联网（可手动输入）"
        SpeechRecognizer.ERROR_NO_MATCH -> "没有听清，请再说一次"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务忙，请稍后再试"
        SpeechRecognizer.ERROR_SERVER -> "识别服务不可用，请手动输入"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听到声音，请再试一次"
        else -> "语音识别失败（$code），请手动输入"
    }
}
