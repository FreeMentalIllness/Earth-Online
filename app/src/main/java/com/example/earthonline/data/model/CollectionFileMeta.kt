package com.example.earthonline.data.model

import kotlinx.serialization.Serializable

/**
 * 收藏文件附件的元信息（仅存元信息，二进制复制到应用私有目录持久化）。
 * 对应 HTML 收藏仅会话内 Blob（刷新丢失）的改进：本端离线可用、可分享。
 */
@Serializable
data class CollectionFileMeta(
    val name: String = "",
    val mime: String = "",
    val size: Long = 0L
)
