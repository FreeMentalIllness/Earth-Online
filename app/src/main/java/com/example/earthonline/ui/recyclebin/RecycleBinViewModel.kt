package com.example.earthonline.ui.recyclebin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.repository.RecycleBinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * v1.0.5 回收站：任务 / 灵感 / 物品 / 收藏 的软删行统一列表。
 * 打开页面即触发一次 30 天到期物理清理（与 App 启动双保险）。
 */
@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val repo: RecycleBinRepository
) : ViewModel() {

    val entries = repo.observeEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { repo.purgeExpired(purgeCutoff()) }
    }

    fun restore(entry: RecycleBinRepository.Entry) = viewModelScope.launch {
        repo.restore(entry)
    }

    fun deleteForever(entry: RecycleBinRepository.Entry) = viewModelScope.launch {
        repo.deleteForever(entry)
    }

    fun purgeExpiredNow() = viewModelScope.launch { repo.purgeExpired(purgeCutoff()) }

    /** 30 天前的时间点（ISO 串，字典序比较） */
    private fun purgeCutoff(): String {
        val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -30) }
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return fmt.format(cal.time)
    }
}
