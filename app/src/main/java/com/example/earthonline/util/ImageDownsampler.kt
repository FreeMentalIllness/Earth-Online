package com.example.earthonline.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * 按目标尺寸解码图片（v1.0.0：只做「解码侧」的降采样，不再负责存储侧的压缩）。
 *
 * ## 职责边界（重要）
 * 导入阶段**不再**压缩原图 —— 见 [ImageStore]：原图字节原样落盘，画质 100% 保住。
 * 本类只服务那些**必须自己拿到 Bitmap** 的场景：
 *   - 桌面小组件的圆形头像（RemoteViews 只能吃 Bitmap，没有 Coil）；
 *   - 分享卡片上的头像（原生 Canvas 绘制）。
 * 共同点是「目标尺寸是已知固定值」，所以一律用 `inSampleSize` 在解码阶段降采样。
 *
 * ## 为什么必须降采样
 * 12MP（4000×3000）的 ARGB_8888 位图 = 宽×高×4 ≈ 48MB。而小组件头像只需要
 * 几十像素，卡片头像 260px —— 直接解原图属于拿大炮打蚊子，还容易把进程打死。
 *
 * ## 两个老坑（都还留着）
 * 1. **EXIF 方向**：手机相册里的照片普遍带方向标记，不解出来头像就是歪的；
 * 2. **单次解码可能返回 null**：部分编码 / 机型在指定采样率下会失败，
 *    所以解码要能自动降档重试（采样率翻倍）。
 */
object ImageDownsampler {

    /** 计算采样率（只取 2 的幂，解码器对非 2 的幂会自行向下取整，不如自己算清楚） */
    fun inSampleSize(srcW: Int, srcH: Int, reqPx: Int): Int {
        if (srcW <= 0 || srcH <= 0 || reqPx <= 0) return 1
        var s = 1
        // 看短边：头像按正方形取中间一块，长边超一点没关系
        while (min(srcW, srcH) / s > reqPx) s *= 2
        return s
    }

    /**
     * 从磁盘文件按目标尺寸解码（带 EXIF 转正 + 自动降档重试）。
     *
     * @param reqPx 结果需要的**短边**像素（如小组件头像 48dp ≈ 144px）
     * @return 解码后的位图；失败返回 null（调用方自行回落到 emoji / 占位）
     */
    fun decodeSampledFile(file: File, reqPx: Int): Bitmap? {
        if (!file.exists() || file.length() <= 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.absolutePath, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = inSampleSize(bounds.outWidth, bounds.outHeight, max(reqPx, 1))
        repeat(4) {
            val bmp = runCatching {
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                )
            }.getOrNull()
            // repeat 是 inline，这里的 return 就是整个函数返回
            if (bmp != null) return applyExif(file, bmp)
            sample *= 2
        }
        return null
    }

    /**
     * 从字节数组按目标尺寸解码（老版本存进数据库的头像 ByteArray 仍要能显示）。
     * 旧字节是导入时就转过正的，这里不再读 EXIF。
     */
    fun decodeSampledBytes(bytes: ByteArray, reqPx: Int): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = inSampleSize(bounds.outWidth, bounds.outHeight, max(reqPx, 1))
        repeat(4) {
            val bmp = runCatching {
                BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.size,
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                )
            }.getOrNull()
            if (bmp != null) return bmp
            sample *= 2
        }
        return null
    }

    /**
     * 头像专用：优先从**原图文件**解码，文件缺失（换设备 / 备份没带上图）时
     * 回落到老版本存在数据库里的 ByteArray。两条路都按 [reqPx] 降采样。
     */
    fun decodeAvatar(avatarPath: String?, avatarData: ByteArray?, reqPx: Int): Bitmap? {
        val file = avatarPath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
        if (file != null) decodeSampledFile(file, reqPx)?.let { return it }
        return avatarData?.let { decodeSampledBytes(it, reqPx) }
    }

    /** 读取文件 EXIF 方向并旋转位图；无方向信息 / 已是正向则原样返回（不拷贝） */
    private fun applyExif(file: File, src: Bitmap): Bitmap {
        val degrees = runCatching {
            when (ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (degrees == 0f) return src
        return runCatching {
            val out = Bitmap.createBitmap(
                src, 0, 0, src.width, src.height,
                Matrix().apply { postRotate(degrees) }, true
            )
            if (out !== src) src.recycle()
            out
        }.getOrDefault(src)
    }
}
