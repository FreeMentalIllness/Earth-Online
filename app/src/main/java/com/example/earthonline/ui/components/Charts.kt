package com.example.earthonline.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.earthonline.ui.theme.AmberPrimary
import kotlin.math.min

data class DonutSlice(val label: String, val value: Int, val color: Color)
data class BarItem(val label: String, val value: Int)

/**
 * 环形图（纯 Canvas 绘制）。多段用 drawArc 拼接，中心可显示文字（用 Compose Text 叠加）。
 * 对应 HTML 数据看板的完成率环。
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    centerText: String = ""
) {
    val total = slices.sumOf { it.value.toLong() }.toFloat().coerceAtLeast(1f)
    Box(modifier = modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.22f
            val diameter = size.minDimension - stroke
            val radius = diameter / 2f
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            var start = -90f
            slices.forEach { s ->
                val sweep = (s.value / total) * 360f
                if (sweep > 0f) {
                    drawArc(
                        color = s.color,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke)
                    )
                    start += sweep
                }
            }
        }
        if (centerText.isNotBlank()) {
            Text(centerText, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}

/**
 * 柱状图（纯 Compose 布局，不依赖第三方图表库）。
 * 每根柱子高度按 value/max 比例，柱顶显示数值，下方显示标签。
 */
@Composable
fun BarChart(
    items: List<BarItem>,
    modifier: Modifier = Modifier,
    maxHeight: androidx.compose.ui.unit.Dp = 140.dp,
    color: Color = AmberPrimary
) {
    if (items.isEmpty()) {
        Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val max = items.maxOf { it.value }.coerceAtLeast(1)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(maxHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            items.forEach { bar ->
                val barHeight = maxHeight * (bar.value.toFloat() / max)
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        if (bar.value > 0) bar.value.toString() else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(barHeight.coerceAtLeast(2.dp))
                            .background(
                                color,
                                RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                            )
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { bar ->
                Text(
                    bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/* ==========================================================================
 * v1.0.0：周期性报告图表（渐入动画）
 *
 * 为什么自己画而不引图表库：只要两根柱子一条折线，引一个库要付 APK 体积、
 * 额外的测量/布局开销，还得迁就它的 API。Canvas 直绘几十行就够，且动画参数完全可控。
 *
 * 动画要点：
 *  - 用 Animatable 从 0 跑到 1，柱子高度 / 折线长度都乘这个系数 —— 只改绘制参数，
 *    不触发任何重新布局（Compose 的 Canvas 内绘制不参与 measure/layout），滚动时不掉帧；
 *  - 柱子带 stagger（第 i 根晚 0.35*i 起步），看起来是「依次长起来」而不是整体缩放；
 *  - 缓动用 FastOutSlowIn（起步快、收尾稳），700ms 左右 —— 超过 1s 会让人觉得在等。
 * ========================================================================== */

/**
 * 渐入柱状图。
 * @param topColor 渐变顶色（不传则单色）
 */
@Composable
fun AnimatedBarChart(
    items: List<BarItem>,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 140.dp,
    color: Color = AmberPrimary,
    topColor: Color = color
) {
    if (items.isEmpty()) {
        Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    /* 全 0 的周期（比如今天还没记任何东西）如果照常画，得到的是一个空框 ——
       用户看到的第一反应是「图坏了」而不是「我还没数据」。所以这里给一句明确的话。 */
    if (items.sumOf { it.value } == 0) {
        Column(modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().height(maxHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "这段时间还没有记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ChartLabels(items.map { it.label })
        }
        return
    }
    val max = items.maxOf { it.value }.coerceAtLeast(1)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(items) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 720, easing = FastOutSlowInEasing))
    }
    /* 柱顶数值直接在 Canvas 里画：柱子高度是动画算出来的，若用 Compose Text 排一份，
       得再算一次偏移，动画中途两者容易错位。这里走原生 Paint（drawContext.canvas.nativeCanvas），
       一次性 measureText 即可居中。 */
    val density = LocalDensity.current
    val valueColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valuePaint = remember(valueColor, density) {
        // 注意：Paint 没有 Kotlin 属性 `color` / `textSize`，直接写 `color = ...`
        // 会解析到外层函数同名参数（val）而报「val cannot be reassigned」，必须用 setter。
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            setColor(valueColor.toArgb())
            setTextSize(with(density) { 11.sp.toPx() })
        }
    }
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(maxHeight)
        ) {
            val n = items.size
            val slot = size.width / n
            val barW = (slot * 0.56f).coerceAtMost(28.dp.toPx())
            val radius = (barW / 2f).coerceAtMost(7.dp.toPx())
            items.forEachIndexed { i, bar ->
                val stagger = (i.toFloat() / n) * 0.35f
                val p = ((progress.value - stagger) / 0.65f).coerceIn(0f, 1f)
                if (p <= 0f) return@forEachIndexed
                val h = size.height * (bar.value.toFloat() / max) * p
                // 0 值也给一根 2dp 的「底线」，否则空的那天什么都看不到，像是漏画了
                val drawH = h.coerceAtLeast(if (bar.value > 0) 2.dp.toPx() else 0f)
                if (drawH <= 0f) return@forEachIndexed
                val left = slot * i + (slot - barW) / 2f
                val top = size.height - drawH
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(topColor, color),
                        startY = top,
                        endY = size.height
                    ),
                    topLeft = Offset(left, top),
                    size = Size(barW, drawH),
                    cornerRadius = CornerRadius(radius, radius)
                )
                if (bar.value > 0) {
                    val text = bar.value.toString()
                    val textW = valuePaint.measureText(text)
                    drawContext.canvas.nativeCanvas.drawText(
                        text,
                        left + (barW - textW) / 2f,
                        // nativeCanvas.drawText 的 y 是文字**基线**，往上留 4dp 空隙即可
                        (top - 4.dp.toPx()).coerceAtLeast(valuePaint.textSize),
                        valuePaint
                    )
                }
            }
        }
        ChartLabels(items.map { it.label })
    }
}

/** 图表横轴标签行（柱状 / 折线共用，保证两端样式一致） */
@Composable
private fun ChartLabels(labels: List<String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { l ->
            Text(
                l,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/**
 * 渐入折线图（带渐变面积）。
 * @param points 数值序列（长度 ≥ 2 才画；不足时展示提示文案）
 * @param labels 与 points 一一对应的横轴标签
 */
@Composable
fun AnimatedLineChart(
    points: List<Int>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    color: Color = AmberPrimary,
    dotRing: Color = Color.White
) {
    if (points.size < 2) {
        Text(
            "数据点不足，换一个更长的时间范围看看",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    // 全 0 同样给文案（一条贴着底边的直线看起来像渲染失败）
    if (points.all { it == 0 }) {
        Column(modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().height(height),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "这段时间还没有记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ChartLabels(labels)
        }
        return
    }
    val max = points.max().coerceAtLeast(1)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 760, easing = FastOutSlowInEasing))
    }
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = points.size
            val pad = 10.dp.toPx()
            val w = size.width - pad * 2
            val h = size.height - pad * 2

            fun xAt(i: Int) = pad + w * (i.toFloat() / (n - 1))
            fun yAt(v: Int) = pad + h * (1f - v.toFloat() / max)

            // 只画到 progress 走到的一段，末段做线性插值 —— 效果是「线从左往右长出来」
            val visible = progress.value * (n - 1)
            val full = visible.toInt().coerceAtMost(n - 1)
            val frac = visible - full
            val pts = ArrayList<Offset>(n)
            for (i in 0..full) pts.add(Offset(xAt(i), yAt(points[i])))
            if (full < n - 1 && frac > 0f) {
                val x = xAt(full) + (xAt(full + 1) - xAt(full)) * frac
                val y = yAt(points[full]) + (yAt(points[full + 1]) - yAt(points[full])) * frac
                pts.add(Offset(x, y))
            }

            // 网格（三条水平线：顶 / 中 / 底）
            for (g in 0..2) {
                val y = pad + h * g / 2f
                drawLine(gridColor, Offset(pad, y), Offset(size.width - pad, y), 1.dp.toPx())
            }

            if (pts.size >= 2) {
                val linePath = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                }
                val areaPath = Path().apply {
                    addPath(linePath)
                    lineTo(pts.last().x, pad + h)
                    lineTo(pts[0].x, pad + h)
                    close()
                }
                drawPath(
                    areaPath,
                    Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.30f), Color.Transparent),
                        startY = pad,
                        endY = pad + h
                    )
                )
                drawPath(
                    linePath,
                    color,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            pts.forEach { p ->
                drawCircle(dotRing, radius = 4.5.dp.toPx(), center = p)
                drawCircle(color, radius = 2.5.dp.toPx(), center = p)
            }
        }
        Spacer(Modifier.height(4.dp))
        ChartLabels(labels)
    }
}

/** 报告页的单个数据块（数字 + 单位 + 说明） */
@Composable
fun MetricTile(
    value: Int,
    label: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "$value",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = AmberPrimary
            )
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}