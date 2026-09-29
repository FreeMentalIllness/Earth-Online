package com.example.earthonline.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 极简 WebDAV 客户端（基于 OkHttp，不引第三方 WebDAV 库）。
 * 支持：list(PROPFIND) / upload(PUT) / download(GET) / delete(DELETE) / mkcol(MKCOL)。
 * 仅做最小必要 XML 解析（href / collection / getcontentlength）。
 */
@Singleton
class WebDavService @Inject constructor(private val client: OkHttpClient) {

    data class DavEntry(val href: String, val isDir: Boolean, val size: Long = 0L)
    data class DavConfig(val url: String, val user: String, val pass: String)

    private fun auth(c: DavConfig) = Credentials.basic(c.user, c.pass)

    /**
     * HTTP 状态码 → 可操作的中文提示。
     * 用户不该面对裸状态码猜原因：401/403 指向账号密码，404 指向路径，5xx 指向服务端。
     */
    private fun friendlyError(op: String, code: Int): Nothing = error(
        when (code) {
            401, 403 -> "密码或地址错误（$code），请检查 WebDAV 账号与密码"
            404 -> "路径不存在（404），请检查服务器目录"
            in 500..599 -> "服务器暂时不可用（$code），请稍后重试"
            else -> "$op 失败（$code），请检查网络与服务器配置"
        }
    )

    private fun join(base: String, path: String): String {
        val b = if (base.endsWith("/")) base else "$base/"
        val p = path.removePrefix("/")
        return b + p
    }

    /** PROPFIND 列出目录（Depth=1） */
    suspend fun list(config: DavConfig, remotePath: String): Result<List<DavEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = "<propfind xmlns=\"DAV:\"><prop><resourcetype/><getcontentlength/></prop></propfind>"
                    .toRequestBody("application/xml; charset=utf-8".toMediaType())
                val req = Request.Builder().url(join(config.url, remotePath))
                    .method("PROPFIND", body)
                    .header("Authorization", auth(config))
                    .header("Depth", "1")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) friendlyError("连接测试", resp.code)
                    parseProps(resp.body?.string().orEmpty())
                }
            }
        }

    /** PUT 上传文件 */
    suspend fun upload(config: DavConfig, remotePath: String, bytes: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = bytes.toRequestBody("application/octet-stream".toMediaType())
                val req = Request.Builder().url(join(config.url, remotePath))
                    .put(body)
                    .header("Authorization", auth(config))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) friendlyError("上传", resp.code)
                }
            }
        }

    /** GET 下载文件 */
    suspend fun download(config: DavConfig, remotePath: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(join(config.url, remotePath))
                    .get()
                    .header("Authorization", auth(config))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) friendlyError("下载", resp.code)
                    resp.body?.bytes() ?: byteArrayOf()
                }
            }
        }

    /** DELETE 删除 */
    suspend fun delete(config: DavConfig, remotePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(join(config.url, remotePath))
                    .delete()
                    .header("Authorization", auth(config))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) friendlyError("删除", resp.code)
                }
            }
        }

    /** MKCOL 建目录（已存在返回 405 视为成功） */
    suspend fun mkcol(config: DavConfig, remotePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(join(config.url, remotePath))
                    .method("MKCOL", null)
                    .header("Authorization", auth(config))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful && resp.code != 405) friendlyError("建目录", resp.code)
                }
            }
        }

    private fun parseProps(xml: String): List<DavEntry> {
        val out = mutableListOf<DavEntry>()
        val respRe = Regex("<(?:D:)?response>(.*?)</(?:D:)?response>", RegexOption.DOT_MATCHES_ALL)
        val hrefRe = Regex("<(?:D:)?href>(.*?)</(?:D:)?href>", RegexOption.DOT_MATCHES_ALL)
        for (m in respRe.findAll(xml)) {
            val block = m.groupValues[1]
            val href = hrefRe.find(block)?.groupValues?.get(1)?.trim() ?: continue
            val isDir = block.contains("<(?:D:)?collection")
            val size = Regex("<(?:D:)?getcontentlength>(\\d+)<").find(block)
                ?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            out.add(DavEntry(href, isDir, size))
        }
        return out
    }
}
