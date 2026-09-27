package com.example.earthonline.util

/**
 * 预设头像：key -> emoji（单一来源）。
 * 个人资料页用它生成「🌍 默认」这样的选项文案，桌面小组件用它做无上传头像时的兜底图标，
 * 两处共用一份映射，避免改了资料页忘了改小组件。
 */
val AVATAR_EMOJI: Map<String, String> = mapOf(
    "" to "\uD83C\uDF0D",       // 🌍
    "default" to "\uD83C\uDF0D",
    "earth" to "\uD83C\uDF0D",
    "rocket" to "\uD83D\uDE80", // 🚀
    "game" to "\uD83C\uDFAE",   // 🎮
    "cat" to "\uD83D\uDC31",    // 🐱
    "leaf" to "\uD83C\uDF43",   // 🍃
    "music" to "\uD83C\uDFB5",  // 🎵
    "star" to "⭐"
)

/** key -> 选项文案（"🌍 默认"）用的中文名 */
private val AVATAR_LABEL: Map<String, String> = mapOf(
    "" to "默认",
    "rocket" to "火箭",
    "game" to "游戏",
    "cat" to "猫",
    "leaf" to "叶子",
    "music" to "音乐",
    "star" to "星星"
)

/** 取预设头像 emoji；未知 key 回落地球 */
fun avatarEmoji(key: String): String = AVATAR_EMOJI[key] ?: AVATAR_EMOJI.getValue("")

/** 资料页头像选项（key -> "🌍 默认"） */
val AVATAR_PRESET_OPTIONS: List<Pair<String, String>> =
    listOf("", "rocket", "game", "cat", "leaf", "music", "star").map { key ->
        key to "${avatarEmoji(key)} ${AVATAR_LABEL.getValue(key)}"
    }
