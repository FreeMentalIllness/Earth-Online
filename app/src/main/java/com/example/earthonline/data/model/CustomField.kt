package com.example.earthonline.data.model

import kotlinx.serialization.Serializable

/** 个人资料自定义字段（对应 HTML state.profile.customFields） */
@Serializable
data class CustomField(
    val id: String = "",
    val label: String = "",
    val value: String = ""
)
