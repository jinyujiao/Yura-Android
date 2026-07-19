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
import java.util.Locale
import androidx.compose.ui.zIndex
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.yura.app.data.Book
import com.yura.app.library.LibraryUiState
import com.yura.app.library.LibraryViewModel
import com.yura.app.reader.ReaderActivity
import com.yura.app.reader.ReaderFonts
import com.yura.app.reader.ReaderPreferencesStore
import com.yura.tts.SimpleTtsController
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
import org.readium.r2.navigator.preferences.FontFamily
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
            SettingsGroup(title = "字体") {
                AppPreferenceChoiceRow(
                    choices = listOf(
                        PreferenceOption("默认", preferences.fontFamily == null) {
                            onPreferencesChange(preferences.copy(fontFamily = null, publisherStyles = false))
                        },
                        PreferenceOption("宋体", preferences.fontFamily == FontFamily.SERIF) {
                            onPreferencesChange(preferences.copy(fontFamily = FontFamily.SERIF, publisherStyles = false))
                        },
                        PreferenceOption("霞鹜文楷", preferences.fontFamily == ReaderFonts.LXGW_WEN_KAI) {
                            onPreferencesChange(preferences.copy(fontFamily = ReaderFonts.LXGW_WEN_KAI, publisherStyles = false))
                        },
                    ),
                )
            }
        }
        item {
            SettingsGroup(title = "文字") {
                AppPreferenceSlider(
                    title = "字号",
                    valueLabel = "${((preferences.fontSize ?: 1.0) * 100).toInt()}%",
                    value = (preferences.fontSize ?: 1.0).toFloat(),
                    valueRange = 0.8f..2.0f,
                    steps = 11,
                    onValueChange = { value -> onPreferencesChange(preferences.copy(fontSize = roundToStep(value, 0.1f), publisherStyles = false)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                AppPreferenceSlider(
                    title = "行高",
                    valueLabel = String.format(Locale.ROOT, "%.1f", preferences.lineHeight ?: 1.5),
                    value = (preferences.lineHeight ?: 1.5).toFloat(),
                    valueRange = 1.0f..2.2f,
                    steps = 5,
                    onValueChange = { value -> onPreferencesChange(preferences.copy(lineHeight = roundToStep(value, 0.2f), publisherStyles = false)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                AppPreferenceSlider(
                    title = "字间距",
                    valueLabel = "${((preferences.letterSpacing ?: 0.0) * 100).toInt()}%",
                    value = (preferences.letterSpacing ?: 0.0).toFloat(),
                    valueRange = 0f..1f,
                    steps = 9,
                    onValueChange = { value -> onPreferencesChange(preferences.copy(letterSpacing = roundToStep(value, 0.1f), publisherStyles = false)) },
                )
            }
        }
        item {
            SettingsGroup(title = "段落") {
                AppPreferenceSlider(
                    title = "段首缩进",
                    valueLabel = "${(preferences.paragraphIndent ?: 0.0).toInt()} 字",
                    value = (preferences.paragraphIndent ?: 0.0).toFloat(),
                    valueRange = 0f..4f,
                    steps = 3,
                    onValueChange = { value -> onPreferencesChange(preferences.copy(paragraphIndent = value.toInt().toDouble(), publisherStyles = false)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                AppPreferenceSlider(
                    title = "段间距",
                    valueLabel = "${((preferences.paragraphSpacing ?: 0.0) * 100).toInt()}%",
                    value = (preferences.paragraphSpacing ?: 0.0).toFloat(),
                    valueRange = 0f..2f,
                    steps = 9,
                    onValueChange = { value -> onPreferencesChange(preferences.copy(paragraphSpacing = roundToStep(value, 0.2f), publisherStyles = false)) },
                )
            }
        }
        item {
            SettingsGroup(title = "主题") {
                AppPreferenceChoiceRow(
                    choices = listOf(
                        PreferenceOption("自动", autoTheme) { onAutoThemeChange(true) },
                        PreferenceOption("浅色", !autoTheme && preferences.theme == Theme.LIGHT) {
                            onAutoThemeChange(false)
                            onPreferencesChange(preferences.copy(theme = Theme.LIGHT))
                        },
                        PreferenceOption("深色", !autoTheme && preferences.theme == Theme.DARK) {
                            onAutoThemeChange(false)
                            onPreferencesChange(preferences.copy(theme = Theme.DARK))
                        },
                        PreferenceOption("米色", !autoTheme && preferences.theme == Theme.SEPIA) {
                            onAutoThemeChange(false)
                            onPreferencesChange(preferences.copy(theme = Theme.SEPIA))
                        },
                    ),
                )
                if (autoTheme) {
                    Text(
                        text = if (systemDark) "当前跟随系统：深色" else "当前跟随系统：浅色",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            SettingsGroup(title = "阅读模式") {
                AppPreferenceSwitch(
                    title = "滚动阅读",
                    subtitle = "关闭后使用分页阅读",
                    checked = preferences.scroll == true,
                    onCheckedChange = { checked -> onPreferencesChange(preferences.copy(scroll = checked)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                AppPreferenceSwitch(
                    title = "使用书籍自带版式",
                    subtitle = "开启后部分排版由 EPUB 原始样式决定",
                    checked = preferences.publisherStyles != false,
                    onCheckedChange = { checked -> onPreferencesChange(preferences.copy(publisherStyles = checked)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppPreferenceChoice("自动", preferences.columnCount == null || preferences.columnCount == ColumnCount.AUTO) {
                        onPreferencesChange(preferences.copy(columnCount = ColumnCount.AUTO, spread = Spread.NEVER, scroll = false, publisherStyles = false))
                    }
                    AppPreferenceChoice("单栏", preferences.columnCount == ColumnCount.ONE) {
                        onPreferencesChange(preferences.copy(columnCount = ColumnCount.ONE, spread = Spread.NEVER, scroll = false, publisherStyles = false))
                    }
                    AppPreferenceChoice("双栏", preferences.columnCount == ColumnCount.TWO) {
                        onPreferencesChange(preferences.copy(columnCount = ColumnCount.TWO, spread = Spread.ALWAYS, scroll = false, publisherStyles = false))
                    }
                }
            }
        }
    }
}