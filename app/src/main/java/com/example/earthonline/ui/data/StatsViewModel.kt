package com.example.earthonline.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.entity.ActivityEntity
import com.example.earthonline.data.local.entity.TaskEntity
import com.example.earthonline.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/** 单日活跃计数（day 为 YYYY-MM-DD） */
data class DayCount(val day: String, val count: Int)

/**
 * 数据看板 ViewModel：聚合任务/物品/收藏/日志/成就计数，以及近 N 天活跃度、分类分布。
 * 同时把原始 activities / tasks 暴露给日历组件做按日下钻。
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val taskRepo: TaskRepository,
    private val itemRepo: ItemRepository,
    private val collectionRepo: CollectionRepository,
    private val memoRepo: MemoRepository,
    private val achievementRepo: AchievementRepository,
    private val activityRepo: ActivityRepository
) : ViewModel() {

    val totalTasks: StateFlow<Int> =
        taskRepo.count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val doneTasks: StateFlow<Int> =
        taskRepo.doneCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val itemCount: StateFlow<Int> =
        itemRepo.count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val collectionCount: StateFlow<Int> =
        collectionRepo.count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val memoCount: StateFlow<Int> =
        memoRepo.count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val unlockedAchievements: StateFlow<Int> =
        achievementRepo.unlockedCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val tasksByCategory: StateFlow<List<Pair<String, Int>>> =
        taskRepo.observeAll().map { tasks ->
            listOf("main", "side", "todo").map { c -> c to tasks.count { it.category == c } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activitiesByDay: StateFlow<List<DayCount>> =
        activityRepo.observeRecent().map { acts -> lastNDays(acts, 14) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activities: StateFlow<List<ActivityEntity>> =
        activityRepo.observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<TaskEntity>> =
        taskRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/** 取最近 n 天（含今天）的活跃计数，按日期升序 */
private fun lastNDays(acts: List<ActivityEntity>, n: Int): List<DayCount> {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val days = mutableListOf<String>()
    val cal = Calendar.getInstance()
    for (i in (n - 1) downTo 0) {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -i)
        days.add(fmt.format(c.time))
    }
    val counts = acts.groupingBy { it.time.take(10) }.eachCount()
    return days.map { DayCount(it, counts[it] ?: 0) }
}
