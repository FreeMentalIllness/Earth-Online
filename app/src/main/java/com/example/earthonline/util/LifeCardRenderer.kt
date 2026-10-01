package com.example.earthonline.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File

/**
 * v1.0.5：分享人生卡片长图。
 * Canvas 自绘（无新增依赖）：等级（周岁）、连续天数、本月关键词（本月完成任务的标题词）、
 * 个人签名，落成 PNG 后经 FileProvider 走系统分享（ACTION_SEND image/png）。
 */
object LifeCardRenderer {

    data class CardData(
        val name: String,
        val signature: String,
        val level: Int,             // 等级（周岁；未设生日为 0 → 显示「–」）
        val streakDays: Int,
        val monthDone: Int,
        val keywords: List<String>
    )

    /** 绘制并返回 PNG 文件（cache/life_card.png，覆盖旧卡） */
    fun render(context: Context, data: CardData): File {
        val w = 1080
        val h = 1620
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 背景：米白底 + 琥珀弧形顶栏（与全局视觉基线一致：#f8f6f2 / #d4a373）
        paint.color = 0xFFF8F6F2.toInt(); c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.color = 0xFFD4A373.toInt(); c.drawRect(0f, 0f, w.toFloat(), 340f, paint)
        paint.color = 0xFFE8E2DA.toInt(); c.drawRect(60f, 380f, (w - 60).toFloat(), 392f, paint)

        fun text(s: String, size: Float, color: Long, x: Float, y: Float, bold: Boolean = false) {
            paint.isFakeBoldText = bold
            paint.textSize = size
            paint.color = color.toInt()
            paint.typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
            c.drawText(s, x, y, paint)
        }
        val ink = 0xFF1E1A16L
        val sub = 0xFF7A7268L

        text("地球Online", 64f, 0xFFFFFFFFL, 80f, 150f, true)
        text("人生记录 · 开放世界", 40f, 0xFFEFE7DCL, 80f, 220f)
        text(data.name.ifBlank { "旅行者" }, 88f, ink, 80f, 520f, true)
        text("Lv.${if (data.level > 0) data.level.toString() else "–"} · 人生等级（周岁）", 44f, sub, 80f, 600f)

        // 三块数据区
        val cols = listOf(
            "连续记录" to (if (data.streakDays > 0) "${data.streakDays} 天" else "–"),
            "本月完成" to "$data.monthDone 件事",
            "人生足迹" to "正在书写"
        )
        var cx = 80f
        cols.forEach { (label, value) ->
            text(value, 56f, 0xFFD4A373L, cx, 760f, true)
            text(label, 36f, sub, cx, 820f)
            cx += 320f
        }

        // 本月关键词
        text("本月关键词", 40f, sub, 80f, 940f)
        val words = if (data.keywords.isEmpty()) listOf("记录", "生活", "前行") else data.keywords
        var wx = 80f
        words.take(5).forEach { kw ->
            paint.color = 0xFFEFE7DCL.toInt()
            paint.isFakeBoldText = false
            val tw = paint.measureText(kw)
            paint.textSize = 38f
            val width = paint.measureText(kw) + 48f
            paint.color = 0xFFE8E2DA.toInt()
            c.drawRoundRect(wx, 970f, wx + width, 1040f, 36f, 36f, paint)
            paint.color = ink.toInt()
            c.drawText(kw, wx + 24f, 1020f, paint)
            wx += width + 24f
        }

        // 签名
        text("“${data.signature.ifBlank { "把日子过成自己想要的形状。" }}”", 44f, sub, 80f, 1180f)
        text(java.text.SimpleDateFormat("yyyy年M月d日", java.util.Locale.CHINA)
            .format(java.util.Date()), 36f, sub, 80f, h - 90f)

        val out = File(context.cacheDir, "life_card.png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return out
    }

    /** 系统分享（FileProvider content://） */
    fun share(context: Context, file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "分享人生卡片"))
        }
    }
}
