package com.yura.app.sync

import java.net.URI

object WebDavUrlResolver {
    fun remoteDirectoryUrl(serverUrl: String, remotePath: String): String {
        val base = serverUrl.trim().trimEnd('/')
        val parsedBase = URI(base)
        require(parsedBase.scheme.equals("https", ignoreCase = true)) {
            "WebDAV 地址必须使用 HTTPS，以保护账号和同步数据。"
        }
        val path = remotePath.trim().ifBlank { "/" }
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return URI("$base$normalizedPath").normalize().toString()
    }

    fun fileUrl(serverUrl: String, remotePath: String, fileName: String): String =
        "${remoteDirectoryUrl(serverUrl, remotePath).trimEnd('/')}/$fileName"
}
