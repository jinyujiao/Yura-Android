package com.yura.app.sync

import android.content.Context
import androidx.core.content.edit

data class WebDavSettings(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val remotePath: String = "/Yura",
)

object WebDavSettingsStore {
    private const val PREFS_NAME = "webdav_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_REMOTE_PATH = "remote_path"

    fun load(context: Context): WebDavSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WebDavSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            serverUrl = prefs.getString(KEY_SERVER_URL, "").orEmpty(),
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            password = prefs.getString(KEY_PASSWORD, "").orEmpty(),
            remotePath = prefs.getString(KEY_REMOTE_PATH, "/Yura").orEmpty().ifBlank { "/Yura" },
        )
    }

    fun save(context: Context, settings: WebDavSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ENABLED, settings.enabled)
            putString(KEY_SERVER_URL, settings.serverUrl.trim())
            putString(KEY_USERNAME, settings.username.trim())
            putString(KEY_PASSWORD, settings.password)
            putString(KEY_REMOTE_PATH, settings.remotePath.trim().ifBlank { "/Yura" })
        }
    }
}
