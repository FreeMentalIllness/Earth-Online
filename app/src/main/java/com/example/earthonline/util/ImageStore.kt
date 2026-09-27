package com.example.earthonline.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * v1.0.0：图片**原样存取**（头像 / 壁纸共用）。
 *
 * ## 为什么不再在导入时压缩
 * 上一版为了防 OOM，在导入阶段就把图降采样 + 重编码成 JPEG：
 * 一张 4K 照片被压成 512px（头像）/ 一屏（壁纸），**画质实打实地糊了**，
 * 放大看全是压缩噪点，用户选的图被偷偷换掉，这比 OOM 更让人不愉快。
 *
 * 正确的分工是：
 *  - **存储**：把原图字节**原封不动**复制进 App 私有目录（filesDir 下，别的 App 读不到）。
 *    复制不做解码，因此**不存在**「全尺寸位图进内存」这一步 —— 4K 图也不会 OOM。
 *  - **显示**：交给 Coil 加载本地文件，并显式告诉它「我要多大的图」（`.size(px)`）。
 *    Coil 会用 `BitmapFactory.inSampleSize` 按目标尺寸解码，只分配
 *    80dp 头像（≈240px）或一屏壁纸所需的像素 —— 4K 原图的全尺寸位图同样永远不会进内存。
 *
 * 一句话：**磁盘上留原图，内存里只留需要的那一点像素**。画质和内存两头都要。
 *
 * ## 类型限制
 * 只收 JPG / PNG / WebP（系统选择器已过滤一层，这里按 mime 再校验一层）。
 */
object ImageStore {

    /** 允许导入的 mime（与 ImagePicker.IMAGE_TYPES_HINT 的文案对应） */
    val ALLOWED_MIME = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")

    /** 头像私有目录：filesDir/avatar */
    fun avatarDir(context: Context): File = File(context.filesDir, "avatar").apply { mkdirs() }

    /** 壁纸私有目录：filesDir/wallpaper */
    fun wallpaperDir(context: Context): File = File(context.filesDir, "wallpaper").apply { mkdirs() }

    fun extFor(mime: String?): String = when (mime) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }

    /**
     * 把用户选中的图片**完整复制**到 [dir]（不解码、不缩放、不重编码）。
     *
     * @return Result<File>：成功是落盘后的文件（绝对路径即持久化的值），失败带一句能直接给用户看的人话。
     */
    fun importOriginal(context: Context, uri: Uri, dir: File, prefix: String): Result<File> {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if (!mime.isNullOrBlank()) {
            if (!mime.startsWith("image/")) {
                return Result.failure(IllegalArgumentException("这不是图片文件（$mime）"))
            }
            if (mime !in ALLOWED_MIME) {
                return Result.failure(IllegalArgumentException("只支持 JPG / PNG / WebP，当前是 $mime"))
            }
        }

        val ext = extFor(mime)
        val tmp = File(dir, "${prefix}_tmp_${System.currentTimeMillis()}")
        val dst = File(dir, "${prefix}_${System.currentTimeMillis()}.$ext")
        return runCatching {
            dir.mkdirs()
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("读取图片失败（权限可能已失效），请重新选择一次")
            val copied = input.use { src ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = src.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        total += n
                    }
                    total
                }
            }
            if (copied <= 0L) throw IllegalStateException("读到的图片内容为空，换一张试试")
            if (!tmp.renameTo(dst)) {
                // 极少数分区 rename 会失败，退回逐字节拷贝，保证一定拿到可用文件
                tmp.inputStream().use { it.copyTo(dst.outputStream()) }
                tmp.delete()
            }
            dst
        }.onFailure {
            runCatching { tmp.delete() }
            runCatching { dst.delete() }
        }
    }

    /**
     * 把一段字节原样写成文件（两处用到）：
     *  ① 备份导入：JSON 里的头像还是 Base64 字节，落盘后才能按文件路径显示；
     *  ② 老数据迁移：v1.0.0 之前头像直接存 ByteArray 在数据库里，迁出来变成文件。
     */
    fun saveBytes(dir: File, prefix: String, bytes: ByteArray, ext: String = "jpg"): File? {
        if (bytes.isEmpty()) return null
        return runCatching {
            dir.mkdirs()
            val f = File(dir, "${prefix}_${System.currentTimeMillis()}.$ext")
            FileOutputStream(f).use { it.write(bytes) }
            f
        }.onFailure { }.getOrNull()
    }

    /** 删单个文件（路径可能为空 / 文件可能已被清掉，一律静默） */
    fun deleteFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.isFile }?.delete() }
    }

    /** 清目录里除 [keep] 之外的旧文件：换头像 / 换壁纸时避免私有目录越用越大 */
    fun clearDirExcept(dir: File, keep: File?) {
        runCatching {
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.absolutePath != keep?.absolutePath) f.delete()
            }
        }
    }

    /** 体积文案：小于 1MB 用 KB，否则用 MB（一天之内换几次图也不会看腻的粒度） */
    fun prettySize(bytes: Long): String =
        if (bytes >= 1024 * 1024) String.format("%.1f MB", bytes / 1048576.0)
        else "${(bytes / 1024).coerceAtLeast(1)} KB"
}
