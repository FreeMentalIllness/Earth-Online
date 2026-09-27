package com.example.earthonline.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.data.local.entity.TaskEntity
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.EmptyState
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.util.millisToDayStr
import java.text.SimpleDateFormat
import java.util.Locale

private val CATEGORIES = listOf("main" to "主线", "side" to "支线", "todo" to "To Do")

/** 任务状态（对应 TaskEntity.status；done 由 doneAt 口径统一维护） */
private val STATUSES = listOf(
    "planning" to "计划中",
    "active" to "进行中",
    "paused" to "已暂停",
    "done" to "已完成"
)

private val DAY_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

/** 每层缩进宽度（与 TreeGuide 的单格宽度保持一致） */
private val INDENT = 18.dp

@Composable
fun TasksRoute(
    moreActions: MoreMenuActions,
    openAddOnEntry: Boolean = false,
    vm: TaskViewModel = hiltViewModel()
) {
    TasksScreen(vm, moreActions, openAddOnEntry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    vm: TaskViewModel,
    moreActions: MoreMenuActions,
    openAddOnEntry: Boolean = false
) {
    val allTasks by vm.tasks.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf("main") }
    val collapsed = remember { mutableStateOf(setOf<String>()) }
    var showAdd by remember { mutableStateOf(openAddOnEntry) }
    var addParentId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }

    val filtered = remember(allTasks, category) { allTasks.filter { it.category == category } }
    val tree = remember(filtered) { buildTaskTree(filtered) }
    val flat = remember(tree, collapsed.value) { flattenTaskTree(tree, collapsed.value) }

    // 编辑对话框里的「父任务」候选：同分类下的全部任务（编辑自身时要排除自己及其后代，避免成环）
    val parentOptions = remember(filtered, editing) {
        val exclude = editing?.let { e ->
            val self = setOf(e.id)
            fun descendants(id: String, acc: MutableSet<String>): MutableSet<String> {
                filtered.filter { it.parentId == id }.forEach {
                    acc.add(it.id); descendants(it.id, acc)
                }
                return acc
            }
            self + descendants(e.id, mutableSetOf())
        } ?: emptySet()
        filtered.filter { it.id !in exclude }
    }

    val doneCount = remember(flat) { flat.count { it.task.status == "done" } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("任务") },
                actions = { SettingsIconButton(actions = moreActions) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { addParentId = null; showAdd = true },
                containerColor = AmberPrimary
            ) { Icon(Icons.Filled.Add, contentDescription = "新增任务") }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = CATEGORIES.indexOfFirst { it.first == category }.coerceAtLeast(0)) {
                CATEGORIES.forEach { (key, label) ->
                    Tab(selected = key == category, onClick = { category = key }, text = { Text(label) })
                }
            }

            // 工具行：概览 + 全部展开 / 折叠
            if (flat.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiDimens.ListPad, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "共 ${flat.size} 项 · 已完成 $doneCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { collapsed.value = emptySet() }) {
                        Icon(Icons.Filled.UnfoldMore, contentDescription = "全部展开")
                    }
                    IconButton(onClick = {
                        collapsed.value = tree
                            .filter { it.children.isNotEmpty() }
                            .map { it.task.id }
                            .toSet()
                    }) {
                        Icon(Icons.Filled.UnfoldLess, contentDescription = "全部折叠")
                    }
                }
            }

            if (flat.isEmpty()) {
                // v1.2.1：空状态引导 —— 光秃秃一行「暂无任务」不告诉用户下一步该干嘛
                val (label, hint, cta) = when (category) {
                    "main" -> Triple(
                        "主线还是空白的",
                        "主线是你真正想推进的事：学会一样东西、跑完一次半马、把房间收拾干净。" +
                            "先立一条，之后可以拆成子任务慢慢啃。",
                        "创建主线任务"
                    )
                    "side" -> Triple(
                        "还没有支线",
                        "支线是那些「想做但没那么急」的事。想到就记下来，免得转头忘了。",
                        "创建支线任务"
                    )
                    else -> Triple(
                        "To Do 是空的",
                        "临时冒出来的小事往这儿丢：交水电费、回个消息、买瓶酱油。做完划掉就行。",
                        "创建 To Do"
                    )
                }
                EmptyState(
                    emoji = "📋",
                    title = label,
                    message = hint,
                    actionLabel = cta,
                    onAction = { addParentId = null; showAdd = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = UiDimens.ListPad),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        flat,
                        key = { it.task.id },
                        // contentType 告诉 Compose「这些格子长得一样」，滚动时可以直接复用
                        // 上一个同类格子的测量结果与组合结果，省掉一次重新组合
                        contentType = { "taskRow" }
                    ) { node ->
                        TaskRow(
                            node = node,
                            collapsed = collapsed.value.contains(node.task.id),
                            onToggleCollapse = {
                                val id = node.task.id
                                collapsed.value = if (collapsed.value.contains(id))
                                    collapsed.value - id else collapsed.value + id
                            },
                            onToggleDone = { vm.toggleDone(node.task) },
                            onSetProgress = { vm.setProgress(node.task, it) },
                            onEdit = { editing = node.task },
                            onAddChild = { addParentId = node.task.id; showAdd = true },
                            onDelete = { vm.deleteCascade(node.task.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        TaskEditDialog(
            category = category,
            parentOptions = parentOptions,
            initialParentId = addParentId,
            onDismiss = { showAdd = false },
            onSubmit = { title, due, note, status, parentId ->
                vm.addTask(title, category, parentId, due, note, status)
                showAdd = false
            }
        )
    }
    if (editing != null) {
        val t = editing!!
        TaskEditDialog(
            initialTitle = t.title,
            initialDue = t.dueDate,
            initialNote = t.note,
            initialStatus = t.status,
            initialParentId = t.parentId,
            category = t.category,
            parentOptions = parentOptions,
            onDismiss = { editing = null },
            onSubmit = { title, due, note, status, parentId ->
                vm.update(
                    t.copy(
                        title = title, dueDate = due, note = note,
                        status = status, parentId = parentId
                    )
                )
                editing = null
            },
            onDelete = { vm.deleteCascade(t.id); editing = null }
        )
    }
}

/**
 * 树形连接线引导：为每一层祖先画一格 18dp。
 * - 该层下方还有兄弟 -> 竖线贯穿整行
 * - 已是该层最后一个 -> 竖线只到中部，并向右画一条横线形成「└」拐角
 */
@Composable
private fun TreeGuide(depth: Int, ancestorLast: List<Boolean>, isLast: Boolean) {
    if (depth <= 0) return
    val line = MaterialTheme.colorScheme.outlineVariant
    val corner = 24.dp
    Row(Modifier.fillMaxHeight()) {
        repeat(depth) { level ->
            val isFinalLevel = level == depth - 1
            val hasFollowingSibling =
                if (isFinalLevel) !isLast else !ancestorLast.getOrElse(level + 1) { true }
            Box(Modifier.width(INDENT).fillMaxHeight()) {
                if (hasFollowingSibling) {
                    Box(
                        Modifier.fillMaxHeight().width(2.dp).align(Alignment.TopCenter).background(line)
                    )
                } else {
                    Box(
                        Modifier.height(corner).width(2.dp).align(Alignment.TopCenter).background(line)
                    )
                }
                if (isFinalLevel) {
                    Box(
                        Modifier
                            .padding(top = corner)
                            .height(2.dp)
                            .width(INDENT)
                            .align(Alignment.TopCenter)
                            .background(line)
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    node: TaskNode,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onToggleDone: () -> Unit,
    onSetProgress: (Int) -> Unit,
    onEdit: () -> Unit,
    onAddChild: () -> Unit,
    onDelete: () -> Unit
) {
    val t = node.task
    val hasChildren = node.children.isNotEmpty()
    val done = t.status == "done"

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        TreeGuide(depth = node.depth, ancestorLast = node.ancestorLast, isLast = node.isLast)
        Card(
            modifier = Modifier.weight(1f).clickable { onEdit() },
            shape = RoundedCornerShape(UiDimens.ItemRadius),
            colors = CardDefaults.cardColors(
                // 父任务用更高的容器色，与子任务拉开层次
                containerColor = if (hasChildren) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainerLowest
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (hasChildren) {
                        IconButton(onClick = onToggleCollapse, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = if (collapsed) "展开子任务" else "折叠子任务",
                                modifier = Modifier.rotate(if (collapsed) 0f else 90f)
                            )
                        }
                    } else {
                        Spacer(Modifier.width(28.dp))
                    }
                    Checkbox(checked = done, onCheckedChange = { onToggleDone() })
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title,
                            style = if (hasChildren) MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ) else MaterialTheme.typography.bodyMedium,
                            textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusChip(t.status)
                            t.dueDate?.let {
                                Text(
                                    "📅 $it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    // 父任务：显示子任务聚合进度
                    if (hasChildren && node.childTotal > 0) {
                        AssistChip(
                            onClick = onToggleCollapse,
                            label = { Text("${node.childDone}/${node.childTotal}") }
                        )
                    }
                    IconButton(onClick = onAddChild, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.SubdirectoryArrowRight, contentDescription = "添加子任务")
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                    }
                }

                // 父任务进度由子任务推导（不提供手动滑杆，避免两处进度口径打架）
                if (hasChildren && node.childTotal > 0) {
                    LinearProgressIndicator(
                        progress = { node.childDone.toFloat() / node.childTotal },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (collapsed) {
                        Text(
                            "已折叠 ${node.childTotal} 项子任务",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (!done) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("进度", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = t.progress.toFloat(),
                            onValueChange = { onSetProgress(it.toInt()) },
                            valueRange = 0f..100f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${t.progress}%", style = MaterialTheme.typography.labelSmall)
                    }
                }

                t.note?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (label, color) = when (status) {
        "active" -> "进行中" to MaterialTheme.colorScheme.primary
        "paused" -> "已暂停" to MaterialTheme.colorScheme.tertiary
        "done" -> "已完成" to MaterialTheme.colorScheme.outline
        else -> "计划中" to MaterialTheme.colorScheme.secondary
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Medium
    )
}

/**
 * 任务编辑 / 新增对话框。
 * 相比旧版补齐：父任务选择（决定层级）、状态选择、到期日不再限于 todo 分类。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditDialog(
    initialTitle: String = "",
    initialDue: String? = null,
    initialNote: String? = null,
    initialStatus: String = "planning",
    initialParentId: String? = null,
    category: String,
    parentOptions: List<TaskEntity>,
    onDismiss: () -> Unit,
    onSubmit: (title: String, due: String?, note: String?, status: String, parentId: String?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var title by remember { mutableStateOf(initialTitle) }
    var note by remember { mutableStateOf(initialNote ?: "") }
    var due by remember { mutableStateOf(initialDue) }
    var status by remember { mutableStateOf(initialStatus) }
    var parentId by remember { mutableStateOf(initialParentId) }
    var showDatePicker by remember { mutableStateOf(false) }
    var parentPickerOpen by remember { mutableStateOf(false) }

    val parentLabel = parentId?.let { id -> parentOptions.firstOrNull { it.id == id }?.title } ?: "无（作为顶层任务）"

    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onSubmit(
                            title.trim(), due, note.takeIf { it.isNotBlank() }, status, parentId
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
        title = { Text(if (onDelete != null) "编辑任务" else "新增任务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                // 父任务：决定这棵树长什么样。
                // 这里不用 ExposedDropdownMenuBox —— 它在 AlertDialog 内触摸区域会错位，
                // 点选项只关闭下拉、不回调（详见 CategoryPicker 的注释）。改用嵌套对话框。
                if (parentOptions.isNotEmpty() || parentId != null) {
                    OutlinedCard(
                        onClick = { parentPickerOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "父任务",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(parentLabel, style = MaterialTheme.typography.bodyLarge)
                            }
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "选择父任务")
                        }
                    }
                }

                // 状态
                Column {
                    Text("状态", style = MaterialTheme.typography.labelMedium)
                    /* v1.0.1 布局修正：四个状态选项改为「等宽平分一行」。
                       原来是 Row + 各自 wrap 宽度，四个 chip 的自然宽度加起来超过了对话框内容宽度，
                       而 Row 不会换行 —— 于是最后一个「已完成」被压到最窄，文字被迫折成
                       「已完 / 成」两行，宽高与其他三个单行选项明显不一致（看着像错位）。
                       现在改成 weight(1f) 等宽 + 单行不折行：四个选项左右对齐、宽高一致，
                       窄屏或大字体下也只会把文字收窄，不会再把某一项挤成两行。 */
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        STATUSES.forEach { (key, label) ->
                            FilterChip(
                                selected = status == key,
                                onClick = { status = key },
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = TextAlign.Center,
                                        overflow = TextOverflow.Clip,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = due ?: "", onValueChange = {}, readOnly = true,
                    label = { Text("到期日（可选）") },
                    trailingIcon = { IconButton({ showDatePicker = true }) { Icon(Icons.Filled.DateRange, null) } },
                    modifier = Modifier.fillMaxWidth()
                )
                if (due != null) {
                    TextButton(onClick = { due = null }) { Text("清除到期日") }
                }

                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("备注") }, modifier = Modifier.fillMaxWidth(), maxLines = 3
                )

                Text(
                    "分类：${CATEGORIES.firstOrNull { it.first == category }?.second ?: category}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )

    if (showDatePicker) {
        val initialMillis = due?.let { runCatching { DAY_FMT.parse(it)?.time }.getOrNull() }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { due = millisToDayStr(it) }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton({ showDatePicker = false }) { Text("取消") } }
        ) { DatePicker(state) }
    }

    // 父任务选择：嵌套对话框（避免在 AlertDialog 内使用 Popup 类组件）
    if (parentPickerOpen) {
        AnimatedAlertDialog(
            onDismissRequest = { parentPickerOpen = false },
            confirmButton = { TextButton({ parentPickerOpen = false }) { Text("取消") } },
            title = { Text("选择父任务") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    item {
                        ListItem(
                            headlineContent = { Text("无（作为顶层任务）") },
                            modifier = Modifier.clickable {
                                parentId = null
                                parentPickerOpen = false
                            }
                        )
                    }
                    items(parentOptions, key = { it.id }) { p ->
                        ListItem(
                            headlineContent = { Text(p.title) },
                            modifier = Modifier.clickable {
                                parentId = p.id
                                parentPickerOpen = false
                            }
                        )
                    }
                }
            }
        )
    }
}
