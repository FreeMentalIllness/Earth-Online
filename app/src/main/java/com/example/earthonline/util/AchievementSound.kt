package com.example.earthonline.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.example.earthonline.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 成就解锁音效（SoundPool 播放 res/raw/achievement_unlock.wav）。
 *
 * 为什么用 SoundPool 而不是 MediaPlayer：
 *  - 解锁提示是几百毫秒的短音，MediaPlayer 每次播放都要重新 prepare，延迟明显且占资源；
 *  - SoundPool 把音频解码进内存一次，之后 play() 是纯内存拷贝，延迟 <10ms；
 *  - 多个成就在 1 秒内连续解锁时，SoundPool 的 maxStreams 能同时发声而不互相打断。
 *
 * 音效开关：跟随设置里的「成就音效」开关（默认开）。关闭时 play() 直接返回，
 * 并且不再持有 SoundPool（省一份常驻内存）。
 */
object AchievementSound {

    private const val TAG = "AchievementSound"

    /** 音效开关（进程内生效；持久化在 SettingsDataStore） */
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    @Volatile private var pool: SoundPool? = null
    @Volatile private var soundId: Int = 0
    @Volatile private var loaded: Boolean = false

    fun setEnabled(v: Boolean) {
        _enabled.value = v
        if (!v) release()
    }

    /** 预热：进入主页时调一次，把解码开销挪到解锁之前 */
    fun prepare(context: Context) {
        if (!_enabled.value || pool != null) return
        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val p = SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(attrs)
                .build()
            p.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    soundId = sampleId
                    loaded = true
                }
            }
            p.load(context, R.raw.achievement_unlock, 1)
            pool = p
        }.onFailure { Log.w(TAG, "SoundPool 初始化失败，成就将静音", it) }
    }

    fun play(context: Context) {
        if (!_enabled.value) return
        if (pool == null) prepare(context)
        val p = pool ?: return
        if (!loaded) {
            // 首次解锁往往发生在预热完成前：延迟 120ms 再试一次（解码通常 <100ms），
            // 仍失败就放弃 —— 一次没响不值得阻塞 UI 或弹错误。
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (loaded) playNow(p)
            }, 120)
            return
        }
        playNow(p)
    }

    private fun playNow(p: SoundPool) {
        runCatching {
            p.play(soundId, 0.55f, 0.55f, 1, 0, 1.0f)
        }.onFailure { Log.w(TAG, "成就音效播放失败", it) }
    }

    fun release() {
        runCatching { pool?.release() }
        pool = null
        soundId = 0
        loaded = false
    }
}
