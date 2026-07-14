@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.yura.app.reader

import android.content.Context
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesSerializer
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.Theme

object ReaderPreferencesStore {
    private const val PREFS_NAME = "reader_preferences"
    private const val PREFS_KEY = "epub"
    private const val PREFS_AUTO_THEME_KEY = "auto_theme"

    val defaults = EpubPreferences(
        fontSize = 1.3,
        lineHeight = 1.5,
        paragraphIndent = 2.0,
        paragraphSpacing = 0.0,
        letterSpacing = 0.0,
        scroll = false,
        columnCount = ColumnCount.AUTO,
        spread = Spread.NEVER,
        publisherStyles = false,
        theme = Theme.LIGHT,
    )

    fun load(context: Context): EpubPreferences {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREFS_KEY, null)
            ?.let { value ->
                runCatching { EpubPreferencesSerializer().deserialize(value) }.getOrNull()
            }
        return defaults + (stored ?: EpubPreferences())
    }

    fun save(context: Context, preferences: EpubPreferences) {
        val serialized = EpubPreferencesSerializer().serialize(preferences)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFS_KEY, serialized)
            .apply()
    }

    fun isAutoTheme(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREFS_AUTO_THEME_KEY, true)

    fun saveAutoTheme(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREFS_AUTO_THEME_KEY, enabled)
            .apply()
    }

    fun resolveTheme(
        preferences: EpubPreferences,
        autoTheme: Boolean,
        systemDark: Boolean,
    ): EpubPreferences =
        if (autoTheme) {
            preferences.copy(theme = if (systemDark) Theme.DARK else Theme.LIGHT)
        } else {
            preferences
        }
}
