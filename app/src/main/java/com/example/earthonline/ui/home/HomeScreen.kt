package com.example.earthonline.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.data.local.entity.ActivityEntity
import com.example.earthonline.util.millisToDayStr
import com.example.earthonline.util.todayStr
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.EmptyHint
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.ui.components.SectionHeader
import com.example.earthonline.ui.components.TagChip
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.components.UserAvatar
import com.example.earthonline.ui.theme.AmberPrimary

/** 主页 LazyColumn 中「人生时间轴」卡片的位置（0 基），供卡片内的「添加里程碑」定位 */

/** 首页入口定义（对应 HTML renderHome 顶部功能入口）。
 *  仅保留：任务 / 背包 / 成就 / 地图 / 记账 / 系统，去除与下方卡片重复的「新建任务」「记心情」等入口。 */
private data class HomeEntry(
    val icon: String,
    val label: String,
    val sub: String,
    /** route 为空表示「记账」：不走页面导航，改为弹出下载渠道弹窗 */
    val route: String?
)

@Composable
fun HomeRoute(
    onNavigate: (String) -> Unit,
    moreActions: MoreMenuActions,
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onNavigate = onNavigate,
        moreActions = moreActions,
        onAddMemo = vm::addQuickMemo,
        onDeleteMemo = vm::deleteMemo,
        onAddTimelineEvent = vm::addCustomTimelineEvent,
        onDeleteTimelineEvent = vm::deleteCustomTimelineEvent,
        onFeedKindsChange = vm::setFeedKinds,
        onFeedLimitChange = vm::setFeedLimit,
        onAddCustomActivity = vm::addCustomActivity,
        query = query,
        results = results,
        onQueryChange = vm::setQuery,
        onPickResult = { hit ->
            vm.clearQuery()
            onNavigate(hit.route)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onNavigate: (String) -> Unit,
    moreActions: MoreMenuActions,
    onAddMemo: (String, String) -> Unit,
    onDeleteMemo: (String) -> Unit,
    onAddTimelineEvent: (String, String, String) -> Unit = { _, _, _ -> },
    onDeleteTimelineEvent: (String) -> Unit = {},
    onFeedKindsChange: (Set<String>) -> Unit = {},
    /** 最近动态：首页显示条数（0 = 不限）与自定义动态添加 */
    onFeedLimitChange: (Int) -> Unit = {},
    onAddCustomActivity: (String) -> Unit = {},
    /** v1.2.1 全局搜索：当前词 / 命中项 / 改词 / 点中结果 */
    query: String = "",
    results: List<SearchHit> = emptyList(),
    onQueryChange: (String) -> Unit = {},
    onPickResult: (SearchHit) -> Unit = {}
) {
    val context = LocalContext.current
    var memoInput by remember { mutableStateOf("") }
    var memoType by remember { mutableStateOf("note") }

    // 语音输入（系统 SpeechRecognizer；无识别服务的机型降级为手动输入弹窗）
    val speech = remember(context) { SpeechInputController(context) }
    DisposableEffect(speech) { onDispose { speech.destroy() } }
    var voiceFallback by remember { mutableStateOf(false) }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice(speech, context) { text -> memoInput = (memoInput + text).take(200) }
        else Toast.makeText(context, "没有麦克风权限，请在系统设置中开启", Toast.LENGTH_SHORT).show()
    }

    var showAddTimeline by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        // v1.2.1：世界日志输入框在页面中部，键盘弹起时若不收缩可视高度，
        // 输入框会被键盘整个盖住（用户看不到自己在打什么）。imePadding 让列表底部腾出键盘高度。
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(
            start = UiDimens.ScreenPad,
            end = UiDimens.ScreenPad,
            top = 8.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(UiDimens.Gap)
    ) {
        item(key = "titlebar", contentType = "titlebar") { HomeTitleBar(actions = moreActions) }

        item(key = "search", contentType = "search") {
            HomeSearchBox(
                query = query,
                results = results,
                onQueryChange = onQueryChange,
                onPickResult = onPickResult
            )
        }

        item(key = "hero", contentType = "hero") {
            HeroBanner(
                state = state,
                onAvatarClick = { onNavigate("profile") }
            )
        }

        item(key = "overview", contentType = "overview") {
            OverviewGrid(state = state, onNavigate = onNavigate, onAccounting = moreActions.onAccounting)
        }

        item(key = "worldlog", contentType = "card") {
            Card(shape = RoundedCornerShape(UiDimens.CardRadius), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(UiDimens.CardPad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("🌍 世界日志")
                    OutlinedTextField(
                        value = memoInput,
                        onValueChange = { memoInput = it },
                        placeholder = { Text("记录一闪而过的想法…") },
                        singleLine = true,
                        leadingIcon = {
                            IconButton(onClick = {
                                if (speech.listening) { speech.stop(); return@IconButton }
                                // 降级：设备没有可用识别服务（常见于模拟器/精简 ROM）→ 手动输入弹窗
                                if (!speechAvailable(context)) { voiceFallback = true; return@IconButton }
                                micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }) {
                                Icon(
                                    Icons.Filled.Mic,
                                    contentDescription = if (speech.listening) "停止录音" else "语音输入",
                                    tint = if (speech.listening) MaterialTheme.colorScheme.primary
                                    else LocalContentColor.current
                                )
                            }
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    onAddMemo(memoInput, memoType)
                                    memoInput = ""
                                },
                                enabled = memoInput.isNotBlank()
                            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "记录") }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    MemoTypeRow(memoType) { memoType = it }
                    if (state.recentMemos.isEmpty()) {
                        EmptyHint("还没有记录，在上方输入第一条世界日志吧", emoji = "📭")
                    } else {
                        state.recentMemos.forEach { memo ->
                            MemoRow(
                                text = memo.text,
                                type = memo.type,
                                time = memo.createdAt.take(10),
                                onDelete = { onDeleteMemo(memo.id) }
                            )
                        }
                        if (state.totalMemos > state.recentMemos.size) {
                            TextButton(onClick = { onNavigate("data") }, modifier = Modifier.fillMaxWidth()) {
                                Text("查看全部 ${state.totalMemos} 条世界日志 ›")
                            }
                        }
                    }
                }
            }
        }

        item(key = "acts", contentType = "card") {
            ActsCard(
                allActs = state.allActs,
                feedKinds = state.feedKinds,
                feedLimit = state.feedLimit,
                onFeedKindsChange = onFeedKindsChange,
                onFeedLimitChange = onFeedLimitChange,
                onAddCustom = onAddCustomActivity,
                onOpenAll = { onNavigate("all_activities") }
            )
        }

        item(key = "timeline", contentType = "card") {
            TimelineCard(
                items = state.timeline,
                customIds = state.customTimeline.map { it.key }.toSet(),
                onAdd = { showAddTimeline = true },
                onDelete = onDeleteTimelineEvent
            )
        }

        item(key = "lifecard", contentType = "card") {
            LifeCard(
                name = state.displayName,
                level = state.life.age,
                unlockedAch = state.unlockedAch,
                locationCount = state.locationCount,
                doneRatio = state.doneRatio,
                onShare = {
                    val text = buildString {
                        append("🌍 地球Online 人生卡片\n")
                        append("角色：").append(state.displayName).append('\n')
                        append("等级 Lv.").append(state.life.age)
                        append(" · 成就 ").append(state.unlockedAch).append('/').append(state.totalAch)
                        append(" · 足迹 ").append(state.locationCount)
                        append(" · 任务完成率 ").append(state.doneRatio).append('%')
                    }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    runCatching { context.startActivity(Intent.createChooser(send, "分享人生卡片")) }
                }
            )
        }
    }

        // 语音不可用时的降级入口：直接打字填入世界日志
        if (voiceFallback) {
            VoiceFallbackDialog(
                onDismiss = { voiceFallback = false },
                onConfirm = { text ->
                    memoInput = (memoInput + text).take(200)
                    voiceFallback = false
                }
            )
        }

        // 时间轴：添加自定义里程碑
        if (showAddTimeline) {
            AddTimelineDialog(
                onDismiss = { showAddTimeline = false },
                onConfirm = { title, day, note ->
                    onAddTimelineEvent(title, day, note)
                    showAddTimeline = false
                }
            )
        }
    }
}

/** 启动一次语音识别，结果追加到输入框，错误以 Toast 呈现 */
private fun startVoice(
    controller: SpeechInputController,
    context: Context,
    onText: (String) -> Unit
) {
    controller.start(
        onFinal = { text -> onText(text) },
        onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    )
}

/**
 * 无可用语音识别服务时的手动输入弹窗（国内机型的降级方案）
 */
@Composable
private fun VoiceFallbackDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动输入") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "当前设备没有可用的语音识别服务，可直接输入内容（也可使用输入法的语音按钮）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(200) },
                    label = { Text("内容") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()); },
                enabled = text.isNotBlank()
            ) { Text("填入") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
}

/**
 * v1.2.1：主页顶部全局搜索（任务 / 物品 / 收藏）。
 *
 * 放在标题栏正下方，而不是每个列表页各放一个 —— 用户想找东西时第一反应是回主页找，
 * 而且三个数据源（任务 / 背包 / 收藏）本来就是跨页的，只有在主页做才是「全局」。
 * 有词才展开结果区，空词时整块只有一根搜索条，不抢首屏内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeSearchBox(
    query: String,
    results: List<SearchHit>,
    onQueryChange: (String) -> Unit,
    onPickResult: (SearchHit) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("搜索任务 / 物品 / 收藏…") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "搜索") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "清空")
                    }
                }
            },
            shape = RoundedCornerShape(UiDimens.ItemRadius),
            modifier = Modifier.fillMaxWidth()
        )
        if (query.isNotBlank()) {
            Card(shape = RoundedCornerShape(UiDimens.CardRadius), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(UiDimens.CardPad)) {
                    if (results.isEmpty()) {
                        EmptyHint("没有匹配「$query」的内容", emoji = "🔍")
                    } else {
                        Text(
                            "找到 ${results.size} 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        results.forEach { hit ->
                            Surface(
                                onClick = { onPickResult(hit) },
                                color = androidx.compose.ui.graphics.Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(hit.icon, style = MaterialTheme.typography.titleMedium)
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            hit.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (hit.sub.isNotBlank()) {
                                            Text(
                                                hit.sub,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Text(
                                        when (hit.kind) { "task" -> "任务"; "item" -> "背包"; else -> "收藏" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (hit !== results.last()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTitleBar(actions: MoreMenuActions) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("🌍", style = MaterialTheme.typography.titleLarge)
        Column(Modifier.weight(1f)) {
            Text("地球Online", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "人生记录 · 开放世界",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SettingsIconButton(actions = actions)
    }
}

@Composable
private fun HeroBanner(state: HomeUiState, onAvatarClick: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AvatarWithBadge(state.avatarPath, state.avatarData, state.avatarKey, onAvatarClick)
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    state.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (state.birthDate.isBlank()) "🎂 未设置生日" else "🎂 ${state.birthDate}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.signature.isNotBlank()) {
                    Text(
                        "“${state.signature}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.fieldTags.isNotEmpty() || state.hiddenFieldCount > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.fieldTags.take(2).forEach { tag ->
                            FieldTag(tag)
                        }
                        if (state.hiddenFieldCount > 0) FieldTag("+${state.hiddenFieldCount}")
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Lv.${state.life.age}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = AmberPrimary
                )
                Text(
                    if (state.life.hasBirth) "距下一级还有 ${state.life.daysToNext} 天" else "设置生日后开启等级",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                ExpBar(
                    progress = if (state.life.hasBirth) state.life.progress else 0f,
                    modifier = Modifier.width(88.dp)
                )
            }
        }
    }
}

/** 个人自定义字段标签 —— 统一复用 TagChip */
@Composable
private fun FieldTag(text: String) {
    TagChip(text = text)
}

@Composable
private fun ExpBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(AmberPrimary)
        )
    }
}

// v1.0.0：预设头像 emoji 统一取自 util/AvatarPresets.kt（资料页、主页、桌面小组件共用一份）

/** 头像：点击直达个人资料页（对应 HTML hero-avatar 的 goto-profile） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvatarWithBadge(
    avatarPath: String?,
    avatarData: ByteArray?,
    avatarKey: String,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.size(72.dp)) {
        Surface(
            onClick = onClick,
            color = AmberPrimary.copy(alpha = 0.15f),
            shape = CircleShape,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // v1.0.0：统一走 UserAvatar —— 原图文件 + 按 72dp 控件尺寸采样
                UserAvatar(avatarPath, avatarData, avatarKey, 72.dp)
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.align(Alignment.BottomEnd).size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("✎", style = MaterialTheme.typography.labelSmall, color = AmberPrimary)
            }
        }
    }
}

@Composable
private fun OverviewGrid(
    state: HomeUiState,
    onNavigate: (String) -> Unit,
    onAccounting: () -> Unit
) {
    val entries = listOf(
        HomeEntry("📋", "任务", "完成率 ${state.doneRatio}%", "tasks"),
        HomeEntry("🎒", "背包", "${state.itemCount} 件物品", "backpack"),
        HomeEntry("🏆", "成就", "${state.unlockedAch}/${state.totalAch}", "achievements"),
        HomeEntry("🗺️", "地图", "${state.locationCount} 处足迹", "map"),
        HomeEntry("📊", "记账", "账本与导出", null),
        HomeEntry("🤖", "系统", "AI 助手", "ai")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.chunked(3).forEach { rowEntries ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowEntries.forEach { entry ->
                    HomeEntryTile(
                        entry = entry,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (entry.route != null) onNavigate(entry.route) else onAccounting()
                    }
                }
                // 末行不足 3 个时用空白占位，保持左右对齐
                repeat(3 - rowEntries.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeEntryTile(entry: HomeEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(UiDimens.ItemRadius),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(76.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(entry.icon, style = MaterialTheme.typography.titleMedium)
            Text(entry.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                entry.sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 最近动态：
 * - 顶部单选分类标签（全部 / 任务 / 成就 / 物品 / 记录），默认「全部」；
 *   「全部」按用户自定义的启用分类展示完整动态流（自定义动态恒显示）；
 * - 右上角「自定义」按钮 → BottomSheet 配置面板：勾选展示分类 / 设置显示条数 / 添加自定义动态；
 * - 底部「查看全部 N 条 ›」跳转完整动态列表页（AllActivitiesRoute）。
 * 启用分类（feedKinds）与条数（feedLimit）由 HomeViewModel 经 DataStore 持久化；
 * 标签选中态为轻量 UI 状态（默认「全部」，不持久化）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActsCard(
    allActs: List<ActivityEntity>,
    feedKinds: Set<String>,
    feedLimit: Int,
    onFeedKindsChange: (Set<String>) -> Unit,
    onFeedLimitChange: (Int) -> Unit,
    onAddCustom: (String) -> Unit,
    onOpenAll: () -> Unit = {}
) {
    val tabKinds = listOf(
        "all" to "全部",
        "task" to "任务",
        "ach" to "成就",
        "item" to "物品",
        "memo" to "记录"
    )
    val sheetKinds = listOf(
        "task" to "任务",
        "ach" to "成就",
        "item" to "物品",
        "memo" to "记录"
    )

    var tab by remember { mutableStateOf("all") }
    var showConfig by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    // 筛选逻辑：具体标签 = 单独该类型（标签是显式意图，覆盖启用分类配置）；
    // 「全部」= 按启用分类展示，自定义动态恒显示
    val filtered = if (tab == "all") {
        allActs.filter { it.kind in feedKinds || it.kind == "custom" }
    } else {
        allActs.filter { it.kind == tab }
    }
    val shown = if (feedLimit > 0) filtered.take(feedLimit) else filtered

    Card(shape = RoundedCornerShape(UiDimens.CardRadius), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(UiDimens.CardPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionHeader("⚡ 最近动态")
                IconButton(onClick = { showConfig = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "自定义最近动态",
                        tint = AmberPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 单选分类标签（5 枚，横向可滚动防溢出）
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                tabKinds.forEach { (kind, label) ->
                    FilterChip(
                        selected = tab == kind,
                        onClick = { tab = kind },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(30.dp)
                    )
                }
            }

            if (shown.isEmpty()) {
                EmptyHint(
                    if (tab == "all") "暂无动态，去完成任务或解锁成就试试"
                    else "该分类下暂无动态"
                )
            } else {
                shown.forEach { act ->
                    val (icon, verb) = when (act.kind) {
                        "ach" -> "🏆" to "解锁成就"
                        "task" -> "📋" to "完成任务"
                        "item" -> "🎒" to "获得物品"
                        "custom" -> "⭐" to "自定义事件"
                        else -> "💭" to "记录"
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(act.time.take(10), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(icon)
                        Text(
                            "$verb「${act.title}」",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (filtered.size > shown.size) {
                    TextButton(onClick = onOpenAll, modifier = Modifier.fillMaxWidth()) {
                        Text("查看全部 ${filtered.size} 条动态 ›", color = AmberPrimary)
                    }
                }
            }
        }
    }

    // 自定义配置面板（BottomSheet）：启用分类 / 显示条数 / 添加自定义动态 / 全部动态入口
    if (showConfig) {
        ModalBottomSheet(onDismissRequest = { showConfig = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("自定义最近动态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                // 1) 启用分类（多选；自定义动态恒显示）
                Text("要展示的分类", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sheetKinds.forEach { (kind, label) ->
                        val selected = kind in feedKinds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val next = if (selected) feedKinds - kind else feedKinds + kind
                                // 至少保留一个分类，避免出现「全部隐藏」的死局面
                                if (next.isNotEmpty()) onFeedKindsChange(next)
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text(
                    "「全部」标签只显示勾选的分类；⭐ 自定义动态始终显示。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 2) 显示条数
                Text("最多显示条数", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5 to "5 条", 10 to "10 条", 20 to "20 条", 0 to "全部").forEach { (n, label) ->
                        FilterChip(
                            selected = feedLimit == n,
                            onClick = { onFeedLimitChange(n) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // 3) 添加自定义动态 + 完整列表入口
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showConfig = false; showAdd = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("添加自定义动态")
                    }
                    OutlinedButton(onClick = { showConfig = false; onOpenAll() }, modifier = Modifier.weight(1f)) {
                        Text("查看全部动态 ›")
                    }
                }
            }
        }
    }

    // 添加自定义动态对话框
    if (showAdd) {
        AddCustomActivityDialog(
            onDismiss = { showAdd = false },
            onConfirm = { title ->
                onAddCustom(title)
                showAdd = false
            }
        )
    }
}

/** 添加一条自定义动态（kind=custom，随动态流与时间轴展示） */
@Composable
private fun AddCustomActivityDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⭐ 添加自定义动态") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    title, { title = it.take(60) },
                    label = { Text("事件内容") },
                    placeholder = { Text("例如：开始一段新旅程") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "自定义动态会显示在主页「最近动态」中（⭐ 标记），不受分类筛选影响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title.trim()) }, enabled = title.isNotBlank()) { Text("添加") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
}

/**
 * 人生时间轴：在自动事件之外，允许用户添加自定义里程碑（「+」按钮），并可删除自定义项。
 * 自定义事件由 HomeViewModel 经 DataStore 持久化（不改动 Room 表结构）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineCard(
    items: List<TimelineItem>,
    customIds: Set<String>,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(UiDimens.CardRadius),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(UiDimens.CardPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = AmberPrimary.copy(alpha = 0.16f),
                        shape = CircleShape,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text("🕰", style = MaterialTheme.typography.labelMedium) }
                    }
                    Text("人生时间轴", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onAdd, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "添加里程碑", tint = AmberPrimary)
                }
            }
            if (items.isEmpty()) {
                EmptyHint("还没有重要事件，点右上角 + 记录一个里程碑吧")
            } else {
                items.forEach { item ->
                    val isCustom = item.key in customIds
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(timelineColor(item.kind))
                        )
                        Text(item.day, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when (item.kind) { "ach" -> "🏆"; "loc" -> "🗺️"; "custom" -> "⭐"; else -> "📋" }
                        )
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCustom) {
                            IconButton(onClick = { onDelete(item.key) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除", modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTimelineDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, day: String, note: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(todayStr()) }
    var note by remember { mutableStateOf("") }
    var showDate by remember { mutableStateOf(false) }
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🕰 添加里程碑") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(40) }, label = { Text("标题") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(day, {}, readOnly = true, label = { Text("日期") },
                    trailingIcon = { IconButton({ showDate = true }) { Icon(Icons.Filled.DateRange, null) } },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it.take(200) }, label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim(), day, note.trim()) },
                enabled = title.isNotBlank()
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
    if (showDate) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { day = millisToDayStr(it) }
                showDate = false
            }) { Text("确定") } },
            dismissButton = { TextButton({ showDate = false }) { Text("取消") } }
        ) { DatePicker(state) }
    }
}

@Composable
private fun LifeCard(
    name: String,
    level: Int,
    unlockedAch: Int,
    locationCount: Int,
    doneRatio: Int,
    onShare: () -> Unit
) {
    Card(shape = RoundedCornerShape(UiDimens.CardRadius), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(UiDimens.CardPad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("🌍 地球Online · 人生卡片")
            Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LifeStat("Lv.$level", "等级")
                LifeStat("$unlockedAch", "成就")
                LifeStat("$locationCount", "足迹")
                LifeStat("$doneRatio%", "完成率")
            }
            OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("分享人生卡片")
            }
        }
    }
}

@Composable
private fun LifeStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AmberPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun timelineColor(kind: String) = when (kind) {
    "ach" -> AmberPrimary
    "loc" -> MaterialTheme.colorScheme.tertiary
    "custom" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

private val MEMO_TYPES = listOf(
    Triple("note", "💭", "随笔"),
    Triple("idea", "⭐", "灵感"),
    Triple("important", "📌", "重要"),
    Triple("mood", "🎭", "心情")
)

@Composable
private fun MemoTypeRow(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MEMO_TYPES.forEach { (value, emoji, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text("$emoji $label", style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun MemoRow(text: String, type: String, time: String, onDelete: () -> Unit) {
    val dot = when (type) {
        "important" -> "📌"
        "idea" -> "⭐"
        "mood" -> "🎭"
        else -> "💭"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(dot, style = MaterialTheme.typography.bodySmall)
        Text(
            time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "删除", modifier = Modifier.size(16.dp))
        }
    }
}
