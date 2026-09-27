package com.example.earthonline.util

import java.util.Calendar

/**
 * 等级 / 生日周期统计（对应 HTML core.js 的 getLifeStats）。
 * 等级 = 周岁；进度 = 距上一个生日已过天数 / 本周期总天数。
 * minSdk 24 无 java.time（未开 desugaring），统一走 Calendar。
 */
data class LifeStats(
    val hasBirth: Boolean = false,
    val age: Int = 0,
    val daysLived: Int = 0,
    val daysToNext: Int = 0,
    val progress: Float = 0f
) {
    companion object {
        val Empty = LifeStats()
    }
}

private fun zeroOfDay(c: Calendar): Calendar = (c.clone() as Calendar).apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

private fun daysBetween(from: Calendar, to: Calendar): Int =
    Math.round((to.timeInMillis - from.timeInMillis) / 86_400_000.0).toInt()

/** 解析 YYYY-MM-DD；非法（2 月 30 日等）返回 null */
private fun parseBirth(birth: String?): Calendar? {
    if (birth.isNullOrBlank()) return null
    val parts = birth.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return null
    val (y, m, d) = parts
    val c = Calendar.getInstance().apply { set(y, m - 1, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
    return if (c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m - 1) c else null
}

fun lifeStatsOf(birth: String?): LifeStats {
    val b = parseBirth(birth) ?: return LifeStats.Empty
    val age = ageFromBirthDate(birth) ?: return LifeStats.Empty
    val today = zeroOfDay(Calendar.getInstance())
    val birthDay = zeroOfDay(b)

    val next = zeroOfDay(b).apply { set(Calendar.YEAR, today.get(Calendar.YEAR)) }
    if (next.before(today)) next.set(Calendar.YEAR, today.get(Calendar.YEAR) + 1)
    val prev = (next.clone() as Calendar).apply { set(Calendar.YEAR, next.get(Calendar.YEAR) - 1) }

    val cycle = daysBetween(prev, next)
    val elapsed = daysBetween(prev, today)
    val progress = if (cycle > 0) (elapsed.toFloat() / cycle).coerceIn(0f, 1f) else 1f

    return LifeStats(
        hasBirth = true,
        age = age,
        daysLived = daysBetween(birthDay, today),
        daysToNext = daysBetween(today, next),
        progress = progress
    )
}
