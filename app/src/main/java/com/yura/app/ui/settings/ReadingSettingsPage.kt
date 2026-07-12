@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.yura.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.yura.app.data.Book
import com.yura.app.library.LibraryUiState
import com.yura.app.library.LibraryViewModel
import com.yura.app.reader.ReaderActivity
import com.yura.app.reader.ReaderPreferencesStore
import com.yura.app.reader.tts.SimpleTtsController
import com.yura.app.sync.WebDavClient
import com.yura.app.ui.shelf.LibraryScreen
import com.yura.app.ui.shelf.LibraryTopBar
import com.yura.app.ui.shelf.ShelfSort
import com.yura.app.ui.shelf.ShelfBookFilter
import com.yura.app.sync.WebDavSettings
import com.yura.app.sync.WebDavSettingsStore
import com.yura.app.sync.WebDavSyncWorker
import com.yura.app.sync.WebDavSyncRepository
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.Theme


@Composable
fun ReadingSettingsPage(
    preferences: EpubPreferences,
    autoTheme: Boolean,
    systemDark: Boolean,
    onAutoThemeChange: (Boolean) -> Unit,
    onPreferencesChange: (EpubPreferences) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SettingsGroup(title = "\u4e3b\u9898") {
                AppPreferenceChoiceRow(
                    choices = listOf(
                        PreferenceOption("\u81ea\u52a8", autoTheme) {
                            onAutoThemeChange(true)
                        },
                        PreferenceOption("\u6d45\u8272", !autoTheme && preferences.theme == Theme.LIGHT) {
                            onAutoThemeChange(false)
                            onPreferencesChange(preferences.copy(theme = Theme.LIGHT))
                        },
                        PreferenceOption("\u6df1\u8272", !autoTheme && preferences.theme == Theme.DARK) {
                            onAutoThemeChange(false)
                            onPreferencesChange(preferences.copy(theme = Theme.DARK))
                        },
                        PreferenceOption("\u7c73\u8272", !autoTheme && preferences.theme == Theme.SEPIA) {
                            onAutoThemeChange(false)
                            onPreferencesChange(preferences.copy(theme = Theme.SEPIA))
                        },
                    ),
                )
                if (autoTheme) {
                    Text(
                        text = if (systemDark) "\u5f53\u524d\u8ddf\u968f\u7cfb\u7edf\uff1a\u6df1\u8272" else "\u5f53\u524d\u8ddf\u968f\u7cfb\u7edf\uff1a\u6d45\u8272",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            SettingsGroup(title = "\u6587\u5b57") {
                AppPreferenceSlider(
                    title = "\u5b57\u53f7",
                    valueLabel = "${((preferences.fontSize ?: 1.0) * 100).toInt()}%",
                    value = (preferences.fontSize ?: 1.0).toFloat(),
                    valueRange = 0.8f..2.0f,
                    steps = 11,
                    onValueChange = { value ->
                        onPreferencesChange(preferences.copy(fontSize = roundToStep(value, 0.1f), publisherStyles = false))
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                AppPreferenceSlider(
                    title = "\u884c\u9ad8",
                    valueLabel = String.format("%.1f", preferences.lineHeight ?: 1.5),
                    value = (preferences.lineHeight ?: 1.5).toFloat(),
                    valueRange = 1.0f..2.2f,
                    steps = 5,
                    onValueChange = { value ->
                        onPreferencesChange(preferences.copy(lineHeight = roundToStep(value, 0.2f), publisherStyles = false))
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                AppPreferenceSlider(
                    title = "\u6bb5\u9996\u7f29\u8fdb",
                    valueLabel = "${(preferences.paragraphIndent ?: 0.0).toInt()} \u5b57",
                    value = (preferences.paragraphIndent ?: 0.0).toFloat(),
                    valueRange = 0f..4f,
                    steps = 3,
                    onValueChange = { value ->
                        onPreferencesChange(preferences.copy(paragraphIndent = value.toInt().toDouble(), publisherStyles = false))
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                AppPreferenceSlider(
                    title = "\u6bb5\u95f4\u8ddd",
                    valueLabel = "${((preferences.paragraphSpacing ?: 0.0) * 100).toInt()}%",
                    value = (preferences.paragraphSpacing ?: 0.0).toFloat(),
                    valueRange = 0f..2f,
                    steps = 9,
                    onValueChange = { value ->
                        onPreferencesChange(preferences.copy(paragraphSpacing = roundToStep(value, 0.2f), publisherStyles = false))
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                AppPreferenceSlider(
                    title = "\u5b57\u95f4\u8ddd",
                    valueLabel = "${((preferences.letterSpacing ?: 0.0) * 100).toInt()}%",
                    value = (preferences.letterSpacing ?: 0.0).toFloat(),
                    valueRange = 0f..1f,
                    steps = 9,
                    onValueChange = { value ->
                        onPreferencesChange(preferences.copy(letterSpacing = roundToStep(value, 0.1f), publisherStyles = false))
                    },
                )
            }
        }
        item {
            SettingsGroup(title = "\u9605\u8bfb") {
                AppPreferenceSwitch(
                    title = "\u6eda\u52a8\u9605\u8bfb",
                    subtitle = "\u5173\u95ed\u540e\u4f7f\u7528\u5206\u9875\u9605\u8bfb",
                    checked = preferences.scroll == true,
                    onCheckedChange = { checked -> onPreferencesChange(preferences.copy(scroll = checked)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                AppPreferenceSwitch(
                    title = "\u4f7f\u7528\u4e66\u7c4d\u81ea\u5e26\u7248\u5f0f",
                    subtitle = "\u5f00\u542f\u540e\u90e8\u5206\u6392\u7248\u7531 EPUB \u539f\u59cb\u6837\u5f0f\u51b3\u5b9a",
                    checked = preferences.publisherStyles != false,
                    onCheckedChange = { checked -> onPreferencesChange(preferences.copy(publisherStyles = checked)) },
                )
            }
        }
        item {
            SettingsGroup(title = "\u7248\u5f0f") {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppPreferenceChoice(
                        text = "\u81ea\u52a8",
                        selected = preferences.columnCount == null || preferences.columnCount == ColumnCount.AUTO,
                        onClick = {
                            onPreferencesChange(preferences.copy(columnCount = ColumnCount.AUTO, spread = Spread.NEVER, scroll = false, publisherStyles = false))
                        },
                    )
                    AppPreferenceChoice(
                        text = "\u5355\u680f",
                        selected = preferences.columnCount == ColumnCount.ONE,
                        onClick = {
                            onPreferencesChange(preferences.copy(columnCount = ColumnCount.ONE, spread = Spread.NEVER, scroll = false, publisherStyles = false))
                        },
                    )
                    AppPreferenceChoice(
                        text = "\u53cc\u680f",
                        selected = preferences.columnCount == ColumnCount.TWO,
                        onClick = {
                            onPreferencesChange(preferences.copy(columnCount = ColumnCount.TWO, spread = Spread.ALWAYS, scroll = false, publisherStyles = false))
                        },
                    )
                }
            }
        }
    }
}

