package com.example.earthonline.data.backup

import com.example.earthonline.data.local.entity.MemoEntity
import com.example.earthonline.data.local.entity.TaskEntity
import com.example.earthonline.util.todayStr
import com.example.earthonline.util.uid
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1.0.5 数据导入增强：CSV / Markdown 文本文件解析器。
 *
 * CSV 契约（UTF-8，首行 header，逗号分隔）：
 *  - header 含 `title` 列 → 导入为【任务】：title（必填）、status、dueDate、note 可选；
 *  - header 含 `text` 列 → 导入为【灵感/世界日志】：text（必填）、type、createdAt（ISO）可选；
 *  - 两者都没有 → 解析失败（返回 null，由调用方给友好提示）。
 *  简易引号处理：字段两侧成对双引号剥掉，内部 "" 转义为 "。
 *
 * Markdown 契约：
 *  - `#` / `##` 行内含 YYYY-MM-DD 日期 → 作为该段日记日期；
 *  - 其余非空行剥掉列表符号（- / * / 1. ）后作为一条灵感（归属最近出现的日期，缺省今天）。
 *
 * 导入语义与 JSON 备份一致：REPLACE 按主键合并（id 由内容哈希派生，重复导入不产生重复行）。
 */
@Singleton
class TextImportParser @Inject constructor(private val json: Json) {

    /** 导入结果 */
    data class Result(val tasks: Int, val memos: Int, val message: String)

    private fun stableId(prefix: String, raw: String): String =
        prefix + "_" + Integer.toHexString(raw.hashCode()) + "_" + raw.length

    /** 解析并落库（不走 BackupRepository 的 Payload —— 文本导入是增量，不覆盖其它表） */
    suspend fun import(
        content: String,
        taskDao: com.example.earthonline.data.local.dao.TaskDao,
        memoDao: com.example.earthonline.data.local.dao.MemoDao
    ): Result {
        val trimmed = content.trimStart('\uFEFF')
        return when {
            trimmed.startsWith("title,") || trimmed.startsWith("\"title\",") -> importTasksCsv(trimmed, taskDao)
            trimmed.startsWith("text,") || trimmed.startsWith("\"text\",") -> importMemosCsv(trimmed, memoDao)
            trimmed.contains(Regex("(?m)^#{1,2}\\s")) || trimmed.startsWith("- ") -> importMarkdown(trimmed, memoDao)
            else -> Result(0, 0, "无法识别文件格式：CSV 需 title 或 text 表头，Markdown 需日期标题")
        }
    }

    private suspend fun importTasksCsv(text: String, dao: com.example.earthonline.data.local.dao.TaskDao): Result {
        val rows = splitCsv(text)
        val header = rows.firstOrNull() ?: return Result(0, 0, "CSV 为空")
        val idx = header.map { it.trim().removeSurrounding("\"").lowercase() }
        val iT = idx.indexOf("title")
        if (iT < 0) return Result(0, 0, "缺少 title 列")
        val iStatus = idx.indexOf("status")
        val iDue = idx.indexOf("duedate")
        val iNote = idx.indexOf("note")
        var n = 0
        val today = todayStr()
        rows.drop(1).forEach { cols ->
            val title = cols.getOrNull(iT)?.trim().orEmpty()
            if (title.isBlank()) return@forEach
            val status = cols.getOrNull(iStatus)?.trim().orEmpty()
                .ifBlank { "planning" }
                .let { if (it in setOf("planning", "active", "paused", "done")) it else "planning" }
            val due = cols.getOrNull(iDue)?.trim()?.takeIf { it.isNotBlank() }
            val note = cols.getOrNull(iNote)?.trim()?.takeIf { it.isNotBlank() }
            dao.insert(
                TaskEntity(
                    id = stableId("csvt", "$title|$due|$note"),
                    category = "todo", title = title, status = status,
                    note = note, dueDate = due, createdAt = today, lastModified = today
                )
            )
            n++
        }
        return Result(n, 0, "已导入 $n 条任务（To Do 分类，重复导入自动去重）")
    }

    private suspend fun importMemosCsv(text: String, dao: com.example.earthonline.data.local.dao.MemoDao): Result {
        val rows = splitCsv(text)
        val header = rows.firstOrNull() ?: return Result(0, 0, "CSV 为空")
        val idx = header.map { it.trim().removeSurrounding("\"").lowercase() }
        val iText = idx.indexOf("text")
        if (iText < 0) return Result(0, 0, "缺少 text 列")
        val iType = idx.indexOf("type")
        val iAt = idx.indexOf("createdat")
        var n = 0
        rows.drop(1).forEach { cols ->
            val body = cols.getOrNull(iText)?.trim().orEmpty()
            if (body.isBlank()) return@forEach
            val type = cols.getOrNull(iType)?.trim()?.takeIf { it in setOf("note", "idea", "important") } ?: "note"
            val at = cols.getOrNull(iAt)?.trim()?.takeIf { it.isNotBlank() }
                ?: com.example.earthonline.util.nowIso()
            dao.insert(
                MemoEntity(id = stableId("csvm", "$at|$body"), text = body, type = type, createdAt = at)
            )
            n++
        }
        return Result(0, n, "已导入 $n 条灵感（重复导入自动去重）")
    }

    private suspend fun importMarkdown(text: String, dao: com.example.earthonline.data.local.dao.MemoDao): Result {
        val dateRe = Regex("(?m)^#{1,2}\\s.*?(\\d{4}-\\d{2}-\\d{2})")
        val listRe = Regex("^\\s*(?:[-*]|\\d+[.)])\\s+")
        var currentDay = todayStr()
        var n = 0
        text.lineSequence().forEach { raw ->
            val dateMatch = dateRe.matchEntire(raw)
            if (dateMatch != null) {
                currentDay = dateMatch.groupValues[1]
                return@forEach
            }
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val body = listRe.replaceFirst(line, "").trim()
            if (body.isBlank()) return@forEach
            dao.insert(
                MemoEntity(
                    id = stableId("mdm", "$currentDay|$body"),
                    text = body, type = "note", createdAt = "${currentDay}T12:00:00Z"
                )
            )
            n++
        }
        if (n == 0) return Result(0, 0, "Markdown 里没有可导入的日记内容")
        return Result(0, n, "已导入 $n 条日记（重复导入自动去重）")
    }

    /** 简易 CSV 拆行拆列：支持引号包裹与引号内逗号/换行 */
    private fun splitCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuote -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                    c == '"' -> inQuote = false
                    else -> cell.append(c)
                }
                c == '"' -> inQuote = true
                c == ',' -> { row.add(cell.toString()); cell.clear() }
                c == '\r' -> { /* skip */ }
                c == '\n' -> { row.add(cell.toString()); cell.clear(); if (row.any { it.isNotBlank() }) rows.add(row.toList()); row.clear() }
                else -> cell.append(c)
            }
            i++
        }
        row.add(cell.toString())
        if (row.any { it.isNotBlank() }) rows.add(row.toList())
        return rows
    }
}
