@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.yura.app.reader

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesSerializer
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.Theme

data class ReaderThemeSelection(
    val autoTheme: Boolean,
    val theme: Theme,
)

object ReaderPreferencesStore {
    private const val PREFS_NAME = "reader_preferences"
    private const val PREFS_KEY = "epub"
    private const val PREFS_AUTO_THEME_KEY = "auto_theme"

    private val themeStateLock = Any()
    @Volatile
    private var mutableThemeSelection: MutableStateFlow<ReaderThemeSelection>? = null

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
        val stored = preferences(context)
            .getString(PREFS_KEY, null)
            ?.let { value ->
                runCatching { EpubPreferencesSerializer().deserialize(value) }.getOrNull()
            }
        return defaults + (stored ?: EpubPreferences())
    }

    fun save(context: Context, preferences: EpubPreferences) {
        val serialized = EpubPreferencesSerializer().serialize(preferences)
        preferences(context)
            .edit()
            .putString(PREFS_KEY, serialized)
            .apply()
        updateThemeSelection(context) { current ->
            current.copy(theme = preferences.theme ?: current.theme)
        }
    }

    fun isAutoTheme(context: Context): Boolean =
        preferences(context).getBoolean(PREFS_AUTO_THEME_KEY, true)

    fun saveAutoTheme(context: Context, enabled: Boolean) {
        preferences(context)
            .edit()
            .putBoolean(PREFS_AUTO_THEME_KEY, enabled)
            .apply()
        updateThemeSelection(context) { current -> current.copy(autoTheme = enabled) }
    }

    fun themeSelection(context: Context): StateFlow<ReaderThemeSelection> =
        themeSelectionState(context)

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

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun themeSelectionState(context: Context): MutableStateFlow<ReaderThemeSelection> {
        mutableThemeSelection?.let { return it }
        return synchronized(themeStateLock) {
            mutableThemeSelection ?: MutableStateFlow(readThemeSelection(context)).also {
                mutableThemeSelection = it
            }
        }
    }

    private fun readThemeSelection(context: Context): ReaderThemeSelection = ReaderThemeSelection(
        autoTheme = isAutoTheme(context),
        theme = load(context).theme ?: Theme.LIGHT,
    )

    private fun updateThemeSelection(
        context: Context,
        transform: (ReaderThemeSelection) -> ReaderThemeSelection,
    ) {
        val state = themeSelectionState(context)
        state.value = transform(state.value)
    }
}
