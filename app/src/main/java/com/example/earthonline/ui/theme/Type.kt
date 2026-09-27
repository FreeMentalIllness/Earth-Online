package com.example.earthonline.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// 无衬线系统字体栈（对应 HTML 的 Inter/SF Pro/PingFang SC），不做外部字体依赖
val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

/**
 * v1.2.0：字号三档（标准 / 大 / 特大）—— 对应 Web 端 html[data-font-scale] 的 #content 缩放。
 * 所有 15 个 Material3 文本样式整体乘系数（行高一同比放，否则行距会挤在一起）。
 * 未显式定义的样式取的是 Material3 默认值，这里连它们一起缩放，
 * 否则会出现「标题变大了、labelSmall 没变」的撕裂感。
 */
fun scaledTypography(scale: Float): Typography {
    val s = scale.coerceIn(0.85f, 1.5f)
    if (s == 1f) return AppTypography
    fun TextStyle.sc(): TextStyle = copy(
        fontSize = fontSize * s,
        lineHeight = if (lineHeight != TextUnit.Unspecified) lineHeight * s else TextUnit.Unspecified
    )
    val b = AppTypography
    return Typography(
        displayLarge = b.displayLarge.sc(),
        displayMedium = b.displayMedium.sc(),
        displaySmall = b.displaySmall.sc(),
        headlineLarge = b.headlineLarge.sc(),
        headlineMedium = b.headlineMedium.sc(),
        headlineSmall = b.headlineSmall.sc(),
        titleLarge = b.titleLarge.sc(),
        titleMedium = b.titleMedium.sc(),
        titleSmall = b.titleSmall.sc(),
        bodyLarge = b.bodyLarge.sc(),
        bodyMedium = b.bodyMedium.sc(),
        bodySmall = b.bodySmall.sc(),
        labelLarge = b.labelLarge.sc(),
        labelMedium = b.labelMedium.sc(),
        labelSmall = b.labelSmall.sc()
    )
}

/** 字号档位 → 缩放系数（与 Web 端 FONT_SCALES 对齐：std / lg / xl） */
fun fontScaleOf(key: String): Float = when (key) {
    "lg" -> 1.1f
    "xl" -> 1.2f
    else -> 1f
}
