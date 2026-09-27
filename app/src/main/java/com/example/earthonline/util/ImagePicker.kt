package com.example.earthonline.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * v1.0.0：统一的「选一张图片」入口（头像 / 壁纸共用）。
 *
 * ## 为什么要抽这一层
 * 之前头像和壁纸各写一份 `rememberLauncherForActivityResult(GetContent())`，
 * 两份都有同一个隐患：**回调里只记下 uri，解码丢给协程稍后做**。
 * 选择器回传的是**临时**读权限，协程排到的时候权限常常已经被系统撤销，
 * 于是 `openInputStream` 返回 null —— 表现出来就是「导入失败」，且和图片大小无关
 * （所以小图偶尔也失败，大图失败率更高）。
 *
 * 修法是在回调里**同步**申请持久化读权限（takePersistableUriPermission），
 * 之后的异步解码才有合法身份读这张图。
 *
 * ## 选择器选择
 * - Android 13+（以及 Play 系统更新 backport 到 11/12 的机型）用系统 Photo Picker：
 *   不需要任何存储权限，也不会把整个相册暴露给应用；
 * - 其余机型回退 `GetContent`（走系统文件选择器，同样无需权限）。
 * 两个 launcher 都注册，运行时挑一个调用 —— 两个契约对象都必须创建，
 * 否则会破坏 Compose 的调用顺序稳定性。
 */
@Composable
fun rememberImagePicker(
    onPicked: (Uri) -> Unit,
    /** 单个 mime 精确过滤时用（如只要 jpg）；留空用 ImageOnly */
    mimeType: String? = null
): () -> Unit {
    val context = LocalContext.current

    @Suppress("DEPRECATION")
    val contentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> onPickedSafe(context, uri, onPicked) }

    val visualLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> onPickedSafe(context, uri, onPicked) }

    val canUsePhotoPicker = remember(context) {
        runCatching {
            ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)
        }.getOrDefault(false)
    }

    return {
        if (canUsePhotoPicker) {
            val request = if (mimeType.isNullOrBlank()) {
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            } else {
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.SingleMimeType(mimeType))
            }
            runCatching { visualLauncher.launch(request) }
                .onFailure { contentLauncher.launch(mimeType ?: "image/*") }
        } else {
            contentLauncher.launch(mimeType ?: "image/*")
        }
    }
}

private fun onPickedSafe(context: Context, uri: Uri?, onPicked: (Uri) -> Unit) {
    if (uri == null) return
    // 临时权限 -> 持久权限。部分 provider（如 Download）不支持持久化，失败就忽略，
    // 此时靠「立刻在同一帧内启动解码」也能救回来，所以这里不允许抛异常打断流程。
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
    onPicked(uri)
}

/** 允许导入的图片类型说明（UI 提示文案用，与 ImageDownsampler.ALLOWED_MIME 对应） */
const val IMAGE_TYPES_HINT = "JPG / PNG / WebP"
