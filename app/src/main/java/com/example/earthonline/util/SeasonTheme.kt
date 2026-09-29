package com.example.earthonline.util

import androidx.compose.ui.graphics.Color
import java.util.Calendar

/**
 * 季节限定主题（v1.0.3）。
 * 按四立节气近似切分（立春 2/4、立夏 5/5、立秋 8/7、立冬 11/7），
 * 主页展示专属小徽章并把部分点缀色换成当季限定色。
 * 刻意只做「点缀」不动全局 Material 配色 —— 治愈系暖底是品牌资产，季节色只做新鲜感。
 */
data class Season(
    val id: String,
    val label: String,
    val emoji: String,
    /** 当季限定点缀色 */
    val accent: Color
)

object SeasonTheme {

    val SPRING = Season("spring", "春樱季", "🌸", Color(0xFFD98BA4))
    val SUMMER = Season("summer", "夏夜季", "🌙", Color(0xFF6E9BC5))
    val AUTUMN = Season("autumn", "秋叶季", "🍂", Color(0xFFC97B3D))
    val WINTER = Season("winter", "冬雪季", "❄️", Color(0xFF8FAEC4))

    fun seasonOf(month1based: Int, day: Int): Season = when {
        (month1based == 2 && day >= 4) || month1based in 3..4 || (month1based == 5 && day < 5) -> SPRING
        (month1based == 5 && day >= 5) || month1based in 6..7 || (month1based == 8 && day < 7) -> SUMMER
        (month1based == 8 && day >= 7) || month1based in 9..10 || (month1based == 11 && day < 7) -> AUTUMN
        else -> WINTER
    }

    fun current(): Season {
        val c = Calendar.getInstance()
        return seasonOf(c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
}
