package com.example.earthonline.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.EmptyHint
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.ui.components.SectionHeader
import com.example.earthonline.ui.components.TagChip
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.components.UserAvatar
import com.example.earthonline.ui.theme.AmberPrimary

/** 首页入口（对应 HTML renderHome） */
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
            OverviewGrid(state = state, onNavigate = onNavigate)
        }

        item(key = "quick", contentType = "quick") {
            QuickGrid(
                onNavigate = onNavigate,
                onAccounting = moreActions.onAccounting,
                onPickMood = { mood -> onAddMemo(mood, "mood") }
            )
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
            Card(shape = RoundedCornerShape(UiDimens.CardRadius), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(UiDimens.CardPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("⚡ 最近动态")
                    if (state.recentActs.isEmpty()) {
                        EmptyHint("暂无动态，去完成任务或解锁成就试试")
                    } else {
                        state.recentActs.forEach { act ->
                            val icon = when (act.kind) {
                                "ach" -> "🏆"; "task" -> "📋"; "item" -> "🎒"; else -> "💭"
                            }
                            val verb = when (act.kind) {
                                "ach" -> "解锁成就"; "task" -> "完成任务"; "item" -> "获得物品"; else -> "记录"
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
                    }
                }
            }
        }

        item(key = "timeline", contentType = "card") {
            Card(shape = RoundedCornerShape(UiDimens.CardRadius), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(UiDimens.CardPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("🕰 人生时间轴")
                    if (state.timeline.isEmpty()) {
                        EmptyHint("还没有重要事件，去完成任务、解锁成就或标记足迹吧")
                    } else {
                        state.timeline.forEach { item ->
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
                                    when (item.kind) { "ach" -> "🏆"; "loc" -> "🗺️"; else -> "📋" }
                                )
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
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

/** 无可用语音识别服务时的手动输入弹窗（国内机型的降级方案） */
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
/**
 * v1.2.0：四张卡严格等高。
 * 之前每张卡的文案行数不同（任务 3 行 / 灵感 1 行），Row 按内容撑高 → 四卡高度参差。
 * 现在两行都锁在同一高度 [UiDimens.OverviewCardHeight]，卡内 fillMaxHeight，
 * 文案统一三行（不足的补占位），视觉上就是四块一样的格子。
 */
private fun OverviewGrid(state: HomeUiState, onNavigate: (String) -> Unit) {
    val cardHeight = UiDimens.OverviewCardHeight
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(cardHeight)
        ) {
            OverviewCard(
                icon = "📋", label = "任务",
                lines = listOf("总数 ${state.totalTasks}", "已完成 ${state.doneTasks}", "完成率 ${state.doneRatio}%"),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) { onNavigate("tasks") }
            OverviewCard(
                icon = "🎒", label = "背包",
                lines = listOf("物品 ${state.itemCount}", "分类 ${state.itemCategoryCount}", "收藏 ${state.collectionCount}"),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) { onNavigate("backpack") }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(cardHeight)
        ) {
            OverviewCard(
                icon = "🏆", label = "成就",
                lines = listOf("已解锁 ${state.unlockedAch}", "总 ${state.totalAch}", "进度 ${state.achRatio}%"),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) { onNavigate("achievements") }
            OverviewCard(
                icon = "📝", label = "灵感",
                lines = listOf("共 ${state.totalMemos} 条", "今日 ${state.todayMemoCount} 条", state.latestMemo),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) { onNavigate("data") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewCard(
    icon: String,
    label: String,
    lines: List<String>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(UiDimens.ItemRadius),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(12.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            lines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 快速入口。v1.2.0：固定 8 个（两行四列）。
 * - route 为空表示「记账」：不走页面导航，改为弹出下载渠道弹窗
 * - route = MOOD_ROUTE 表示「心情」：弹心情选择器，选完直接记一条 mood 日志
 */
private const val MOOD_ROUTE = "__mood__"

private data class QuickEntry(
    val icon: String,
    val label: String,
    val route: String?
)

private val QUICK_ENTRIES = listOf(
    QuickEntry("📝", "新建任务", "new_task"),
    QuickEntry("🎒", "添加物品", "backpack"),
    QuickEntry("🏆", "查看成就", "achievements"),
    QuickEntry("📅", "今日日程", "data"),
    QuickEntry("🎭", "心情", MOOD_ROUTE),
    QuickEntry("🤖", "系统", "ai"),
    QuickEntry("📊", "记账", null),
    QuickEntry("🗺️", "足迹地图", "map")
)

/** 心情选项（与 Web 端 MOOD_CHOICES 一致） */
private val MOOD_CHOICES = listOf(
    "😄 开心", "😌 平静", "🥱 疲惫", "😤 上头",
    "😢 难过", "🤯 爆炸", "🥳 庆祝", "😴 摆烂"
)

@Composable
private fun QuickGrid(
    onNavigate: (String) -> Unit,
    onAccounting: () -> Unit,
    onPickMood: (String) -> Unit
) {
    var showMood by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(UiDimens.CardRadius), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(UiDimens.CardPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("⚡ 快速入口")
            QUICK_ENTRIES.chunked(4).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEach { entry ->
                        QuickTile(entry, Modifier.weight(1f)) {
                            when (entry.route) {
                                null -> onAccounting()
                                MOOD_ROUTE -> showMood = true
                                else -> onNavigate(entry.route)
                            }
                        }
                    }
                    repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    if (showMood) {
        MoodPickerDialog(
            onDismiss = { showMood = false },
            onPick = { mood ->
                showMood = false
                onPickMood(mood)
            }
        )
    }
}

/** 心情选择器：点一下就记一条 mood 类型的世界日志，不用打字 */
@Composable
private fun MoodPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎭 记一笔心情") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "选一个就行，会记进世界日志（类型：心情）。纯表情的记录还有隐藏彩蛋哦。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MOOD_CHOICES.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { mood ->
                            val emoji = mood.substringBefore(" ")
                            val label = mood.substringAfter(" ")
                            OutlinedButton(
                                onClick = { onPick("$emoji $label") },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(emoji, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickTile(entry: QuickEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(vertical = 10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(entry.icon, style = MaterialTheme.typography.titleMedium)
            Text(
                entry.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
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
    else -> MaterialTheme.colorScheme.primary
}
