package com.example.earthonline.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.data.local.entity.ActivityEntity
import com.example.earthonline.data.local.entity.TaskEntity
import com.example.earthonline.data.repository.ActivityRepository
import com.example.earthonline.data.repository.TaskRepository
import com.example.earthonline.util.nowIso
import com.example.earthonline.util.todayStr
import com.example.earthonline.util.uid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 任务 ViewModel：观察全部任务、构建树由 UI 层完成；
 * 负责 状态切换 / doneAt 口径 / 进度联动 / 新增（含子任务）/ 级联删除。
 * 对应 HTML tasks 模块的 CRUD 与完成计时逻辑。
 */
@HiltViewModel
class TaskViewModel @Inject constructor(
    private val repo: TaskRepository,
    private val activityRepo: ActivityRepository,
    private val settings: SettingsDataStore
) : ViewModel() {

    val tasks = repo.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addTask(
        title: String,
        category: String,
        parentId: String? = null,
        dueDate: String? = null,
        note: String? = null,
        status: String = "planning"
    ) {
        val t = title.trim()
        if (t.isBlank()) {
            // v1.2.0：空标题不算数据，但算一次「执念」—— 累计 3 次解锁彩蛋
            viewModelScope.launch { settings.bumpEggBlankTitle() }
            return
        }
        val isDone = status == "done"
        viewModelScope.launch {
            repo.insert(
                TaskEntity(
                    id = uid("task"),
                    parentId = parentId,
                    category = category,
                    title = t,
                    status = status,
                    // 直接建成「已完成」时也要写 doneAt，否则不计入完成任务数
                    doneAt = if (isDone) nowIso() else null,
                    progress = if (isDone) 100 else 0,
                    createdAt = todayStr(),
                    lastModified = todayStr(),
                    dueDate = dueDate,
                    note = note,
                    // 排在同层末尾：用当前同级数量当排序号，避免全部挤在 0 上乱序
                    order = tasks.value.count { it.parentId == parentId }
                )
            )
            if (parentId == null) {
                activityRepo.add(
                    ActivityEntity(id = uid("act"), time = nowIso(), kind = "task", title = "创建了任务：$t")
                )
            }
        }
    }

    /** 勾选完成：done <-> 非 done 切换；doneAt 为「最近完成时间」，改回非 done 时清空 */
    fun toggleDone(t: TaskEntity) {
        viewModelScope.launch {
            val willDone = t.status != "done"
            repo.update(
                t.copy(
                    status = if (willDone) "done" else "planning",
                    doneAt = if (willDone) nowIso() else null,
                    progress = if (willDone) 100 else t.progress,
                    lastModified = todayStr()
                )
            )
            if (willDone) {
                activityRepo.add(
                    ActivityEntity(id = uid("act"), time = nowIso(), kind = "task", title = "完成了任务：${t.title}")
                )
            }
        }
    }

    /** 进度联动：>=100 自动标记完成并写入 doneAt；降回 <100 则清回非完成 */
    fun setProgress(t: TaskEntity, p: Int) {
        val clamped = p.coerceIn(0, 100)
        val willDone = clamped >= 100
        viewModelScope.launch {
            repo.update(
                t.copy(
                    progress = clamped,
                    status = if (willDone) "done" else if (t.status == "done") "planning" else t.status,
                    doneAt = if (willDone) (t.doneAt ?: nowIso()) else null,
                    lastModified = todayStr()
                )
            )
        }
    }

    /**
     * 通用更新：统一维护 doneAt / progress 口径。
     * 编辑对话框里把状态改成「已完成」时，这里补写 doneAt；改回未完成则清空，
     * 保证「完成任务数」只认 doneAt 这一个口径，不会出现状态是 done 却没有 doneAt 的脏数据。
     */
    fun update(t: TaskEntity) = viewModelScope.launch {
        val isDone = t.status == "done"
        repo.update(
            t.copy(
                doneAt = if (isDone) (t.doneAt ?: nowIso()) else null,
                progress = if (isDone) 100 else t.progress,
                lastModified = todayStr()
            )
        )
    }

    /** 把任务挂到新的父任务下（拖拽/改层级时用） */
    fun moveToParent(id: String, parentId: String?) = viewModelScope.launch {
        val t = repo.get(id) ?: return@launch
        repo.update(t.copy(parentId = parentId, lastModified = todayStr()))
    }

    /** 级联删除（含子任务），对应 HTML deleteTaskCascade */
    fun deleteCascade(id: String) = viewModelScope.launch { repo.deleteCascade(id) }
}