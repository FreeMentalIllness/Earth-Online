package com.example.earthonline.widget

import android.content.Context
import android.util.Log
import androidx.room.InvalidationTracker
import com.example.earthonline.data.local.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「数据变了就刷新小组件」的调度器。
 *
 * 用 Room 的 InvalidationTracker 而不是在每个 ViewModel 里插桩：后者一定会漏
 * （导入备份、成就引擎回写、小组件自己都写库），前者覆盖所有 DAO 写入。
 *
 * 两级节流，缺一不可：
 *  - DEBOUNCE_MS：一次导入会连写 8 张表，合并成一次刷新；
 *  - MIN_INTERVAL_MS：防止"改一个字刷一次"，把 IPC 打满。
 * 加上 WidgetRenderer 里「没有已添加的小组件就直接返回」，未安装小组件时这套逻辑零开销。
 */
@Singleton
class WidgetUpdateDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase
) {

    companion object {
        private const val TAG = "WidgetUpdate"
        private const val DEBOUNCE_MS = 3_000L
        private const val MIN_INTERVAL_MS = 20_000L

        /** 小组件展示的数据所落在的表（顺序无关，漏了的表变更后不会触发刷新） */
        private val TABLES = arrayOf("profile", "tasks", "memos", "achievements", "locations")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null
    @Volatile private var lastAt = 0L
    @Volatile private var started = false

    /** 注册表变更监听，应在 Application.onCreate 调一次（幂等） */
    fun start() {
        if (started) return
        started = true
        runCatching {
            db.invalidationTracker.addObserver(object : InvalidationTracker.Observer(TABLES) {
                // Room 2.6 起参数是只读 Set<String>，不是 MutableSet
                override fun onInvalidated(tables: Set<String>) {
                    schedule()
                }
            })
        }.onFailure { Log.w(TAG, "小组件刷新监听注册失败", it) }
    }

    /** 变更后的防抖调度 */
    fun schedule() {
        pending?.cancel()
        pending = scope.launch {
            delay(DEBOUNCE_MS)
            val now = System.currentTimeMillis()
            if (now - lastAt < MIN_INTERVAL_MS) return@launch
            lastAt = now
            OverviewWidget.refresh(context)
        }
    }
}
