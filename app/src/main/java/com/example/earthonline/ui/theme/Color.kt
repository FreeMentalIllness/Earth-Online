package com.example.earthonline.ui.theme

import androidx.compose.ui.graphics.Color

// 对应 HTML 暖白 Notion 风 + 克制毛玻璃（style.css 变量）
// 主色：暖琥珀；背景：纸感米白；边框：浅卡其；文字：近黑暖灰
val AmberPrimary = Color(0xFFD4A373)
val AmberPrimaryDark = Color(0xFFB9885A)
val AmberContainer = Color(0xFFF0E2D2)

val BackgroundLight = Color(0xFFF8F6F2)   // --bg #f8f6f2
val SurfaceLight = Color(0xFFFFFFFF)        // --panel #fff
val BorderLight = Color(0xFFE8E2DA)        // --border #e8e2da
val TextPrimaryLight = Color(0xFF1E1A16)    // --text #1e1a16
val TextSecondaryLight = Color(0xFF7A7268)  // --text-2 #7a7268
val TextTertiaryLight = Color(0xFFB0A89C)   // --text-3 #b0a89c

/* v1.2.1 深色对比度复核（第 18 项）：
   原深色值里 --caption/#71717A 这类三级文字在 #232326 上只有约 3.3:1，
   小字号（labelSmall）下几乎看不清；边框 #333 与背景 #232326 的差异也只有肉眼勉强可辨。
   下面把三级/二级文字与边框整体提亮一档，正文与背景的对比度拉到 12:1 以上，
   三级说明文字也从 3.3:1 提到 5:1 以上（WCAG AA 对小字的要求是 4.5:1）。 */
val BackgroundDark = Color(0xFF1A1A1A)
val SurfaceDark = Color(0xFF232326)
val BorderDark = Color(0xFF3F3F46)
val TextPrimaryDark = Color(0xFFF2F2F5)
val TextSecondaryDark = Color(0xFFB6B6BF)
val TextTertiaryDark = Color(0xFF8F8F99)
