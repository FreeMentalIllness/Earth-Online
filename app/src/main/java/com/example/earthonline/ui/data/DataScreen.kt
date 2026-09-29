package com.example.earthonline.ui.data

import com.example.earthonline.ui.components.BarItem
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.ui.components.BarChart
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.ui.components.SectionHeader
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.components.DonutChart
import com.example.earthonline.ui.components.DonutSlice
import com.example.earthonline.ui.components.EmptyState
import com.example.earthonline.ui.navigation.Screen
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.ui.theme.TextSecondaryLight

/** 数据看板页（对应 HTML 数据看板：统计卡 + 图表 + 活跃日历） */
@Composable
fun DataRoute(moreActions: MoreMenuActions, vm: StatsViewModel = hiltViewModel()) {
    DataScreen(vm, moreActions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(vm: StatsViewModel, moreActions: MoreMenuActions) {
    val total by vm.totalTasks.collectAsStateWithLifecycle(initialValue = 0)
    val done by vm.doneTasks.collectAsStateWithLifecycle(initialValue = 0)
    val items by vm.itemCount.collectAsStateWithLifecycle(initialValue = 0)
    val collections by vm.collectionCount.collectAsStateWithLifecycle(initialValue = 0)
    val memos by vm.memoCount.collectAsStateWithLifecycle(initialValue = 0)
    val unlocked by vm.unlockedAchievements.collectAsStateWithLifecycle(initialValue = 0)
    val byCat by vm.tasksByCategory.collectAsStateWithLifecycle(initialValue = emptyList())
    val byDay by vm.activitiesByDay.collectAsStateWithLifecycle(initialValue = emptyList())
    val activities by vm.activities.collectAsStateWithLifecycle(initialValue = emptyList())
    val tasks by vm.tasks.collectAsStateWithLifecycle(initialValue = emptyList())

    val categoryLabels = mapOf("main" to "主线", "side" to "支线", "todo" to "To Do")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("数据看板") },
                actions = { SettingsIconButton(actions = moreActions) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(UiDimens.ScreenPad),
            verticalArrangement = Arrangement.spacedBy(UiDimens.Gap)
        ) {
            // v1.0.0：周期报告入口（日报 / 周报 / 年报）。
            // 放在最上面而不是埋在底部：看板本身是「总览」，报告是「按周期复盘」，
            // 两者的心智模型连着，从看板直接进报告最顺。
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(UiDimens.CardRadius)) {
                    Column(
                        Modifier.fillMaxWidth().padding(UiDimens.CardPad),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SectionHeader("📈 周期报告")
                        Text(
                            "按天 / 周 / 年回看这段时间的产出：完成任务、新增灵感、解锁成就与经验值，并配一张趋势图。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReportEntry("📅", "日报", Modifier.weight(1f)) {
                                moreActions.onNavigate(Screen.Report.route + "?kind=day")
                            }
                            ReportEntry("🗓️", "周报", Modifier.weight(1f)) {
                                moreActions.onNavigate(Screen.Report.route + "?kind=week")
                            }
                            ReportEntry("📆", "年报", Modifier.weight(1f)) {
                                moreActions.onNavigate(Screen.Report.route + "?kind=year")
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("任务", total, Modifier.weight(1f))
                    StatCard("物品", items, Modifier.weight(1f))
                    StatCard("收藏", collections, Modifier.weight(1f))
                    StatCard("日志", memos, Modifier.weight(1f))
                    StatCard("成就", unlocked, Modifier.weight(1f))
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(UiDimens.CardRadius)) {
                    Column(
                        Modifier.fillMaxWidth().padding(UiDimens.CardPad),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SectionHeader("任务完成度")
                        if (total == 0) {
                            // v1.2.3：无数据时不再画一个空荡荡的圆环，改为引导 + 跳转
                            EmptyState(
                                emoji = "📊",
                                title = "还没有任务",
                                message = "添加并完成任务后，这里会显示完成度圆环。",
                                actionLabel = "去添加任务"
                            ) { moreActions.onNavigate(Screen.Tasks.route) }
                        } else {
                            val pct = done * 100 / total
                            DonutChart(
                                slices = listOf(
                                    DonutSlice("已完成", done, AmberPrimary),
                                    DonutSlice("进行中", (total - done).coerceAtLeast(0), TextSecondaryLight.copy(alpha = 0.25f))
                                ),
                                centerText = "$pct%"
                            )
                            Text(
                                "已完成 $done / $total",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(UiDimens.CardRadius)) {
                    Column(Modifier.fillMaxWidth().padding(UiDimens.CardPad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader("近 14 天活跃度")
                        val avgActive = if (byDay.isNotEmpty()) byDay.sumOf { it.count } / byDay.size else 0
                        BarChart(
                            items = byDay.map { BarItem(it.day.takeLast(2), it.count) },
                            averageValue = avgActive,
                            averageLabel = if (avgActive > 0) "历史均值 $avgActive" else null
                        )
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(UiDimens.CardRadius)) {
                    Column(Modifier.fillMaxWidth().padding(UiDimens.CardPad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader("任务分类分布")
                        BarChart(
                            items = byCat.map { BarItem(categoryLabels[it.first] ?: it.first, it.second) }
                        )
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(UiDimens.CardRadius)) {
                    Column(Modifier.fillMaxWidth().padding(UiDimens.CardPad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader("活跃日历")
                        CalendarSection(activities, tasks)
                    }
                }
            }
        }
    }
}

/** 周期报告的三个入口块（日报 / 周报 / 年报） */
@Composable
private fun ReportEntry(
    emoji: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(UiDimens.ItemRadius),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(UiDimens.ItemRadius),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "$value",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AmberPrimary
            )
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}