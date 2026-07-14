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
import com.yura.app.ui.icons.YuraIcons
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


private enum class SettingsDetail {
    Tts,
    Reading,
    WebDav,
    About,
}

@Composable
fun SettingsHubScreen(
    ttsController: SimpleTtsController,
    active: Boolean,
) {
    val context = LocalContext.current
    var detail by remember { mutableStateOf<SettingsDetail?>(null) }
    var readerPreferences by remember { mutableStateOf(ReaderPreferencesStore.load(context)) }
    var autoReaderTheme by remember { mutableStateOf(ReaderPreferencesStore.isAutoTheme(context)) }
    val systemDark = isSystemInDarkTheme()

    BackHandler(enabled = active && detail != null) {
        detail = null
    }

    when (detail) {
        null -> SettingsHome(onOpen = { detail = it })
        SettingsDetail.Tts -> SettingsDetailScaffold("\u6717\u8bfb\u8bbe\u7f6e", onBack = { detail = null }) {
            CleanTtsSettingsPage(ttsController)
        }
        SettingsDetail.Reading -> SettingsDetailScaffold("\u9605\u8bfb\u8bbe\u7f6e", onBack = { detail = null }) {
            ReadingSettingsPage(
                preferences = readerPreferences,
                autoTheme = autoReaderTheme,
                systemDark = systemDark,
                onAutoThemeChange = { enabled ->
                    autoReaderTheme = enabled
                    ReaderPreferencesStore.saveAutoTheme(context, enabled)
                },
                onPreferencesChange = { updated ->
                    readerPreferences = updated
                    ReaderPreferencesStore.save(context, updated)
                },
            )
        }
        SettingsDetail.WebDav -> SettingsDetailScaffold("\u540c\u6b65\u8bbe\u7f6e", onBack = { detail = null }) {
            WebDavSettingsPage()
        }
        SettingsDetail.About -> SettingsDetailScaffold("\u5173\u4e8e", onBack = { detail = null }) {
            SettingsInfoCard("Yura\n\u4e00\u4e2a\u4e13\u6ce8 EPUB \u9605\u8bfb\u548c\u6717\u8bfb\u7684\u5c0f\u5e94\u7528\u3002")
        }
    }
}

@Composable
private fun SettingsHome(onOpen: (SettingsDetail) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsEntryRow("朗读设置", "云端语音、音色、API Key 和测试朗读", YuraIcons.ReadAloud) {
                onOpen(SettingsDetail.Tts)
            }
        }
        item {
            SettingsEntryRow("阅读设置", "字号、行高、段间距、缩进、字间距和版式", YuraIcons.Font) {
                onOpen(SettingsDetail.Reading)
            }
        }
        item {
            SettingsEntryRow("同步设置", "WebDAV", YuraIcons.Sync) {
                onOpen(SettingsDetail.WebDav)
            }
        }
        item {
            SettingsEntryRow("关于", "版本和项目信息", YuraIcons.Info) {
                onOpen(SettingsDetail.About)
            }
        }
    }
}

@Composable
private fun SettingsDetailScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Text("\u2039", style = MaterialTheme.typography.headlineMedium)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        }
        content()
    }
}

@Composable
fun AppPreferenceSlider(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(valueLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Composable
fun AppPreferenceSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun AppPreferenceChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.background(
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            shape = RoundedCornerShape(18.dp),
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

data class PreferenceOption(
    val text: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun AppPreferenceChoiceRow(choices: List<PreferenceOption>) {
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { choice ->
            AppPreferenceChoice(
                text = choice.text,
                selected = choice.selected,
                onClick = choice.onClick,
            )
        }
    }
}

fun roundToStep(value: Float, step: Float): Double =
    (kotlin.math.round(value / step) * step).toDouble()

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingDropdownRow(
    title: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    menu: @Composable () -> Unit,
) {
    Box {
        SettingTextRow(
            title = title,
            value = value,
            onClick = { onExpandedChange(true) },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            menu()
        }
    }
}

@Composable
fun SettingTextRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (onClick == null) {
        rowContent()
    } else {
        Surface(onClick = onClick, color = Color.Transparent) { rowContent() }
    }
}

@Composable
fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun SettingsInfoCard(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            modifier = Modifier.padding(18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
