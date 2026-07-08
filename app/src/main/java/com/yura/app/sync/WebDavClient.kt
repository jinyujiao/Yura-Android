package com.yura.app.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

class WebDavClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun testConnection(settings: WebDavSettings): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(settings.serverUrl.isNotBlank()) { "请填写 WebDAV 地址。" }
                val code = propfind(settings, settings.fullRemoteUrl())
                if (code !in listOf(200, 207)) {
                    error("WebDAV 连接失败 ($code)，请检查地址和账号。")
                }
            }
        }

    suspend fun ensureRemoteDirectory(settings: WebDavSettings): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = settings.fullRemoteUrl()
                if (propfind(settings, url) in listOf(200, 207)) return@runCatching

                val request = settings.requestBuilder(url)
                    .method("MKCOL", "".toRequestBody(null))
                    .build()
                http.newCall(request).execute().use { response ->
                    if (response.code !in listOf(200, 201, 204, 405)) {
                        error("创建远端目录失败 (${response.code})：${response.body?.string().orEmpty().take(180)}")
                    }
                }
            }
        }

    suspend fun putText(settings: WebDavSettings, fileName: String, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureRemoteDirectory(settings).getOrThrow()
                val request = settings.requestBuilder(settings.fileUrl(fileName))
                    .put(content.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("上传同步数据失败 (${response.code})：${response.body?.string().orEmpty().take(180)}")
                    }
                }
            }
        }

    suspend fun putFile(settings: WebDavSettings, fileName: String, file: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureRemoteDirectory(settings).getOrThrow()
                val request = settings.requestBuilder(settings.fileUrl(fileName))
                    .put(file.asRequestBody("application/octet-stream".toMediaType()))
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("上传文件失败 (${response.code})：${response.body?.string().orEmpty().take(180)}")
                    }
                }
            }
        }

    suspend fun getText(settings: WebDavSettings, fileName: String): Result<String?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = settings.requestBuilder(settings.fileUrl(fileName)).get().build()
                http.newCall(request).execute().use { response ->
                    when (response.code) {
                        200 -> response.body?.string().orEmpty()
                        404 -> null
                        else -> error("下载同步数据失败 (${response.code})：${response.body?.string().orEmpty().take(180)}")
                    }
                }
            }
        }

    suspend fun getFile(settings: WebDavSettings, fileName: String, target: File): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = settings.requestBuilder(settings.fileUrl(fileName)).get().build()
                http.newCall(request).execute().use { response ->
                    when (response.code) {
                        200 -> {
                            target.parentFile?.mkdirs()
                            response.body?.byteStream()?.use { input ->
                                target.outputStream().use { output -> input.copyTo(output) }
                            } ?: error("远端文件为空。")
                            true
                        }
                        404 -> false
                        else -> error("下载文件失败 (${response.code})：${response.body?.string().orEmpty().take(180)}")
                    }
                }
            }
        }

    private fun propfind(settings: WebDavSettings, url: String): Int {
        val request = settings.requestBuilder(url)
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "0")
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code !in listOf(200, 207, 404)) {
                error("WebDAV 请求失败 (${response.code})：${response.body?.string().orEmpty().take(180)}")
            }
            return response.code
        }
    }

    private fun WebDavSettings.fullRemoteUrl(): String {
        val base = serverUrl.trim().trimEnd('/')
        require(base.startsWith("http://") || base.startsWith("https://")) {
            "WebDAV 地址需要以 http:// 或 https:// 开头。"
        }
        val path = remotePath.trim().ifBlank { "/" }
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return URI("$base$normalizedPath").normalize().toString()
    }

    private fun WebDavSettings.fileUrl(fileName: String): String =
        "${fullRemoteUrl().trimEnd('/')}/$fileName"

    private fun WebDavSettings.requestBuilder(url: String): Request.Builder =
        Request.Builder().url(url).apply {
            if (username.isNotBlank() || password.isNotBlank()) {
                header("Authorization", Credentials.basic(username, password, Charsets.UTF_8))
            }
        }

    private companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        private const val PROPFIND_BODY =
            """<?xml version="1.0" encoding="utf-8" ?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:resourcetype/>
    <d:getlastmodified/>
  </d:prop>
</d:propfind>"""
    }
}
