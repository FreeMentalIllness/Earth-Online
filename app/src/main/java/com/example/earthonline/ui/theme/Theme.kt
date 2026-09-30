package com.example.earthonline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
  注意：Material3 的 Card / FilterChip / 各种 Container 实际取的是
  surfaceContainer* / surfaceTint / *Container 这些"新角色"，
  只覆盖 primary/background/surface 会落回默认的紫色基线调色板
  （现象：卡片发淡紫）。这里把整套暖白配色补齐。
*/
private val LightColorScheme = lightColorScheme(
    primary = AmberPrimary,
    onPrimary = Color(0xFF241A10),
    primaryContainer = AmberContainer,
    onPrimaryContainer = TextPrimaryLight,

    secondary = AmberPrimary,
    onSecondary = Color(0xFF241A10),
    secondaryContainer = AmberContainer,
    onSecondaryContainer = TextPrimaryLight,

    tertiary = AmberPrimary,
    onTertiary = Color(0xFF241A10),
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = TextPrimaryLight,

    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = BackgroundLight,
    onSurfaceVariant = TextSecondaryLight,

    // 卡片 / 容器层级（Card 默认取 surfaceContainerLow）
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF8F6F2),
    surfaceContainerHigh = Color(0xFFF1EDE7),
    surfaceContainerHighest = Color(0xFFEAE4DC),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFEFEBE5),
    surfaceTint = AmberPrimary,

    outline = BorderLight,
    outlineVariant = BorderLight,

    inverseSurface = TextPrimaryLight,
    inverseOnSurface = Color(0xFFFFFFFF),
    inversePrimary = AmberPrimaryDark
)

private val DarkColorScheme = darkColorScheme(
    primary = AmberPrimaryDark,
    onPrimary = Color(0xFF241A10),
    primaryContainer = Color(0xFF3A2E22),
    onPrimaryContainer = Color(0xFFF0E2D2),

    secondary = AmberPrimaryDark,
    onSecondary = Color(0xFF241A10),
    secondaryContainer = Color(0xFF3A2E22),
    onSecondaryContainer = Color(0xFFF0E2D2),

    tertiary = AmberPrimaryDark,
    onTertiary = Color(0xFF241A10),
    tertiaryContainer = Color(0xFF3A2E22),
    onTertiaryContainer = Color(0xFFF0E2D2),

    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = BackgroundDark,
    onSurfaceVariant = TextSecondaryDark,

    surfaceContainerLowest = Color(0xFF1A1A1A),
    surfaceContainerLow = Color(0xFF232326),
    surfaceContainer = Color(0xFF28282B),
    surfaceContainerHigh = Color(0xFF2E2E32),
    surfaceContainerHighest = Color(0xFF35353A),
    surfaceBright = Color(0xFF35353A),
    surfaceDim = Color(0xFF1A1A1A),
    surfaceTint = AmberPrimaryDark,

    outline = BorderDark,
    outlineVariant = BorderDark,

    inverseSurface = TextPrimaryDark,
    inverseOnSurface = Color(0xFF1A1A1A),
    inversePrimary = AmberPrimary
)

/**
 * 全局主题。darkTheme 由 SettingsDataStore 的 theme 偏好决定（light/dark/system），
 * 对应 HTML 的 [data-theme="dark"] 切换；其余沿用 Material3 默认排版形状。
 */
@Composable
fun EarthOnlineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** v1.2.0：字号档位 key（std / lg / xl），由设置页写入 DataStore */
    fontScaleKey: String = "std",
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = scaledTypography(fontScaleOf(fontScaleKey)),
        /* 对话框圆角统一为 16dp，与 Web 端 .modal（--radius-card=16px）及卡片圆角一致 */
        shapes = MaterialTheme.shapes.copy(extraLarge = RoundedCornerShape(16.dp)),
        content = content
    )
}
