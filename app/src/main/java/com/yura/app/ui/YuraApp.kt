@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.yura.app.ui

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


private enum class RootTab(val label: String, val icon: String) {
    Library("\u4e66\u67b6", "\u2302"),
    Settings("\u8bbe\u7f6e", "\u2699"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YuraApp() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(RootTab.Library) }
    val libraryViewModel: LibraryViewModel = viewModel()
    val libraryState by libraryViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val ttsController = remember { SimpleTtsController(context.applicationContext) }
    var coverTargetBook by remember { mutableStateOf<Book?>(null) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var shelfSort by remember { mutableStateOf(ShelfSort.RecentlyRead) }
    var sortMenuVisible by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            libraryViewModel.importPublication(uri)
        }
    }
    val coverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val book = coverTargetBook
        coverTargetBook = null
        if (uri != null && book != null) {
            libraryViewModel.changeCover(book, uri)
        }
    }

    LaunchedEffect(libraryState.message) {
        libraryState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            libraryViewModel.clearMessage()
        }
    }

    BackHandler(enabled = tab == RootTab.Library && sortMenuVisible) {
        sortMenuVisible = false
    }
    BackHandler(enabled = tab == RootTab.Library && searchExpanded && !sortMenuVisible) {
        searchExpanded = false
        searchQuery = ""
    }

    DisposableEffect(Unit) {
        onDispose { ttsController.shutdown() }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 88.dp),
            ) { data ->
                YuraSnackbar(data)
            }
        },
        topBar = {
            if (tab == RootTab.Library) {
                LibraryTopBar(
                    searchExpanded = searchExpanded,
                    searchQuery = searchQuery,
                    onSearchExpandedChange = { searchExpanded = it },
                    onSearchQueryChange = { searchQuery = it },
                    sort = shelfSort,
                    sortMenuVisible = sortMenuVisible,
                    onSortMenuVisibleChange = { sortMenuVisible = it },
                    onSortChange = { shelfSort = it },
                    importing = libraryState.isImporting,
                    onImport = {
                        importLauncher.launch(arrayOf(
                            "application/epub+zip",
                            "text/plain",
                            "application/octet-stream",
                            "application/zip",
                        ))
                    },
                    modifier = Modifier.statusBarsPadding(),
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier.statusBarsPadding(),
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            RootTab.entries.forEach { item ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (tab == item) 1f else 0f }
                        .zIndex(if (tab == item) 1f else 0f)
                        .then(
                            if (tab == item) Modifier else Modifier.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent().changes.forEach { it.consume() }
                                    }
                                }
                            },
                        ),
                ) {
                    when (item) {
                        RootTab.Library -> LibraryScreen(
                            state = libraryState,
                            query = searchQuery,
                            sort = shelfSort,
                            onOpenReader = { book ->
                                context.startActivity(ReaderActivity.intent(context, book.id))
                            },
                            onRemoveFromDevice = { books -> books.forEach(libraryViewModel::removeLocalBook) },
                            onDeleteEverywhere = { books -> books.forEach(libraryViewModel::deleteBookEverywhere) },
                            onChangeCover = { book ->
                                coverTargetBook = book
                                coverLauncher.launch(arrayOf("image/*"))
                            },
                        )
                        RootTab.Settings -> SettingsHubScreen(
                            ttsController = ttsController,
                            active = tab == item,
                        )
                    }
                }
            }
            FloatingPillNavigation(
                selected = tab,
                onSelected = { tab = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(10f)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun FloatingPillNavigation(
    selected: RootTab,
    onSelected: (RootTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 10.dp,
        shadowElevation = 14.dp,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
            .width(320.dp)
            .height(68.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RootTab.entries.forEach { item ->
                val isSelected = selected == item
                Surface(
                    onClick = { onSelected(item) },
                    modifier = Modifier.weight(1f),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                    },
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .height(52.dp)
                            .padding(horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            item.icon,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YuraSnackbar(data: SnackbarData) {
    val message = data.visuals.message
    val isError = remember(message) {
        listOf("失败", "无法", "错误", "异常").any { message.contains(it) }
    }
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = contentColor.copy(alpha = 0.12f),
                contentColor = contentColor,
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isError) "!" else "\u2713",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BookCard(
    book: Book,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("\u5220\u9664\u4e66\u7c4d") },
            text = { Text("\u786e\u5b9a\u8981\u5220\u9664\u300a${book.title}\u300b\u5417\uff1f") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消")
                }
            },
        )
    }

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.height(232.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ),
                    ),
            ) {
                AsyncImage(
                    model = File(book.cover),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Surface(
                    onClick = { confirmDelete = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    contentColor = MaterialTheme.colorScheme.error,
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("\u00d7", fontWeight = FontWeight.Black)
                    }
                }
            }
            Column(Modifier.padding(14.dp)) {
                Text(
                    book.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    book.author.ifBlank { "\u672a\u77e5\u4f5c\u8005" },
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                AssistChip(onClick = onClick, label = { Text("\u7ee7\u7eed\u9605\u8bfb") })
            }
        }
    }
}

private enum class SettingsDetail {
    Tts,
    Reading,
    WebDav,
    About,
}

@Composable
private fun SettingsHubScreen(
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
            SettingsEntryRow("\u6717\u8bfb\u8bbe\u7f6e", "\u4e91\u7aef\u8bed\u97f3\u3001\u97f3\u8272\u3001API Key \u548c\u6d4b\u8bd5\u6717\u8bfb", "\u266a") {
                onOpen(SettingsDetail.Tts)
            }
        }
        item {
            SettingsEntryRow("\u9605\u8bfb\u8bbe\u7f6e", "\u5b57\u53f7\u3001\u884c\u9ad8\u3001\u6bb5\u95f4\u8ddd\u3001\u7f29\u8fdb\u3001\u5b57\u95f4\u8ddd\u548c\u7248\u5f0f", "Aa") {
                onOpen(SettingsDetail.Reading)
            }
        }
        item {
            SettingsEntryRow("\u540c\u6b65\u8bbe\u7f6e", "WebDAV", "\u21c4") {
                onOpen(SettingsDetail.WebDav)
            }
        }
        item {
            SettingsEntryRow("\u5173\u4e8e", "\u7248\u672c\u548c\u9879\u76ee\u4fe1\u606f", "i") {
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
private fun ReadingSettingsPage(
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

@Composable
private fun CleanTtsSettingsPage(controller: SimpleTtsController) {
    val uiState by controller.state.collectAsState()
    var providerMenuOpen by remember { mutableStateOf(false) }
    var mimoVoiceMenuOpen by remember { mutableStateOf(false) }
    var microsoftVoiceMenuOpen by remember { mutableStateOf(false) }
    var mimoKey by remember { mutableStateOf(controller.currentMimoApiKey()) }
    var microsoftKey by remember { mutableStateOf(controller.currentMicrosoftApiKey()) }
    var microsoftRegion by remember(uiState.microsoftRegion) { mutableStateOf(uiState.microsoftRegion) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsGroup(title = "\u8bed\u97f3\u670d\u52a1") {
                SettingDropdownRow(
                    title = "\u9ed8\u8ba4\u670d\u52a1",
                    value = uiState.provider.label,
                    expanded = providerMenuOpen,
                    onExpandedChange = { providerMenuOpen = it },
                ) {
                    SimpleTtsController.Provider.entries.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.label) },
                            onClick = {
                                controller.selectProvider(provider)
                                providerMenuOpen = false
                            },
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                when (uiState.provider) {
                    SimpleTtsController.Provider.SYSTEM -> {
                        SettingTextRow("\u7cfb\u7edf\u6717\u8bfb", uiState.engineName.ifBlank { "\u7cfb\u7edf\u9ed8\u8ba4\u6717\u8bfb\u5f15\u64ce" })
                    }
                    SimpleTtsController.Provider.MIMO -> {
                        SettingDropdownRow(
                            title = "MiMo \u97f3\u8272",
                            value = uiState.mimoVoice,
                            expanded = mimoVoiceMenuOpen,
                            onExpandedChange = { mimoVoiceMenuOpen = it },
                        ) {
                            SimpleTtsController.MIMO_VOICES.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text(voice) },
                                    onClick = {
                                        controller.setMimoVoice(voice)
                                        mimoVoiceMenuOpen = false
                                    },
                                )
                            }
                        }
                        SettingsTextField(
                            value = mimoKey,
                            onValueChange = {
                                mimoKey = it
                                controller.setMimoApiKey(it)
                            },
                            label = "MiMo API Key",
                            placeholder = if (uiState.hasMimoApiKey) "\u5df2\u4fdd\u5b58\uff0c\u8f93\u5165\u65b0 key \u53ef\u8986\u76d6" else "\u8bf7\u8f93\u5165 MiMo API key",
                            password = true,
                        )
                    }
                    SimpleTtsController.Provider.MICROSOFT -> {
                        val selectedMicrosoftVoice = uiState.microsoftVoices
                            .firstOrNull { it.shortName == uiState.microsoftVoice }
                            ?.displayName
                            ?: uiState.microsoftVoice
                        SettingDropdownRow(
                            title = "Microsoft \u97f3\u8272",
                            value = selectedMicrosoftVoice,
                            expanded = microsoftVoiceMenuOpen,
                            onExpandedChange = { microsoftVoiceMenuOpen = it },
                        ) {
                            uiState.microsoftVoices.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text(voice.displayName) },
                                    onClick = {
                                        controller.setMicrosoftVoice(voice.shortName)
                                        microsoftVoiceMenuOpen = false
                                    },
                                )
                            }
                        }
                        SettingsTextField(
                            value = microsoftRegion,
                            onValueChange = {
                                microsoftRegion = it
                                controller.setMicrosoftRegion(it)
                            },
                            label = "Azure Region",
                            placeholder = "\u4f8b\u5982 eastasia / japaneast",
                        )
                        SettingsTextField(
                            value = microsoftKey,
                            onValueChange = {
                                microsoftKey = it
                                controller.setMicrosoftApiKey(it)
                            },
                            label = "Azure Speech Key",
                            placeholder = if (uiState.hasMicrosoftApiKey) "\u5df2\u4fdd\u5b58\uff0c\u8f93\u5165\u65b0 key \u53ef\u8986\u76d6" else "\u8bf7\u8f93\u5165 Azure Speech key",
                            password = true,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                enabled = !uiState.microsoftVoicesLoading,
                                onClick = { controller.refreshMicrosoftVoices() },
                            ) {
                                Text(if (uiState.microsoftVoicesLoading) "\u5237\u65b0\u4e2d" else "\u5237\u65b0\u97f3\u8272")
                            }
                            Text(
                                text = "\u5df2\u52a0\u8f7d ${uiState.microsoftVoices.size} \u4e2a\u97f3\u8272",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
        item {
            SettingsGroup(title = "\u6d4b\u8bd5") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = { controller.testVoice() }) {
                            Text(if (uiState.state == SimpleTtsController.State.LOADING) "\u51c6\u5907\u4e2d" else "\u6d4b\u8bd5\u8bed\u97f3")
                        }
                        TextButton(onClick = { controller.stop() }) {
                            Text("\u505c\u6b62")
                        }
                    }
                    Text(
                        text = when {
                            uiState.errorMessage != null -> uiState.errorMessage.orEmpty()
                            uiState.state == SimpleTtsController.State.PLAYING -> "\u6b63\u5728\u64ad\u653e\uff1a${uiState.currentSentence}"
                            uiState.state == SimpleTtsController.State.LOADING -> "\u6b63\u5728\u51c6\u5907\u97f3\u9891..."
                            uiState.state == SimpleTtsController.State.PAUSED -> "\u5df2\u6682\u505c"
                            else -> "\u5f53\u524d\u670d\u52a1\uff1a${uiState.provider.label}\u3002\u70b9\u51fb\u6d4b\u8bd5\u8bed\u97f3\u540e\u5e94\u8be5\u542c\u5230\u58f0\u97f3\u6216\u770b\u5230\u9519\u8bef\u4fe1\u606f\u3002"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.errorMessage != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WebDavSettingsPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(WebDavSettingsStore.load(context)) }
    var testing by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    val workInfo by produceState<WorkInfo?>(initialValue = null, context) {
        while (true) {
            value = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WebDavSyncWorker.WORK_NAME)
                .get()
                .firstOrNull()
            delay(750)
        }
    }
    val syncing = workInfo?.state == WorkInfo.State.RUNNING || workInfo?.state == WorkInfo.State.ENQUEUED
    val syncMessage = when (workInfo?.state) {
        WorkInfo.State.RUNNING -> "正在后台同步…"
        WorkInfo.State.ENQUEUED -> "等待网络后自动同步…"
        WorkInfo.State.FAILED -> "同步失败：${workInfo?.outputData?.getString(WebDavSyncWorker.KEY_ERROR) ?: "请重试"}"
        WorkInfo.State.SUCCEEDED -> "同步完成"
        else -> null
    }
    val syncOk = workInfo?.state == WorkInfo.State.SUCCEEDED
    var lastSyncAt by remember { mutableStateOf(WebDavSettingsStore.lastSyncAt(context)) }

    fun update(updated: WebDavSettings) {
        settings = updated
        WebDavSettingsStore.save(context, updated)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsGroup(title = "\u540c\u6b65") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            enabled = !syncing,
                            onClick = { WebDavSyncWorker.enqueue(context) },
                        ) {
                            Text(if (syncing) "同步中" else "立即同步")
                        }
                        Text(
                            text = syncMessage ?: "同步书籍文件和阅读进度，不会删除本地数据。",
                            color = when {
                                syncMessage == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                syncOk -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.error
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (workInfo?.state == WorkInfo.State.FAILED) {
                        TextButton(onClick = { WebDavSyncWorker.enqueue(context) }) { Text("重试") }
                    }
                    Text(
                        text = if (lastSyncAt > 0L) {
                            "\u4e0a\u6b21\u540c\u6b65\uff1a${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastSyncAt))}"
                        } else {
                            "\u8fd8\u6ca1\u6709\u6210\u529f\u540c\u6b65\u8fc7"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            SettingsGroup(title = "\u540c\u6b65\u670d\u52a1") {
                AppPreferenceSwitch(
                    title = "\u542f\u7528 WebDAV",
                    subtitle = "\u5f00\u542f\u540e\u53ef\u540c\u6b65\u4e66\u7c4d\u6587\u4ef6\u548c\u9605\u8bfb\u8fdb\u5ea6",
                    checked = settings.enabled,
                    onCheckedChange = { update(settings.copy(enabled = it)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsTextField(
                    value = settings.serverUrl,
                    onValueChange = { update(settings.copy(serverUrl = it)) },
                    label = "WebDAV URL",
                    placeholder = "https://example.com/dav",
                )
                SettingsTextField(
                    value = settings.remotePath,
                    onValueChange = { update(settings.copy(remotePath = it)) },
                    label = "\u8fdc\u7aef\u76ee\u5f55",
                    placeholder = "/Yura",
                )
            }
        }
        item {
            SettingsGroup(title = "\u8d26\u53f7") {
                SettingsTextField(
                    value = settings.username,
                    onValueChange = { update(settings.copy(username = it)) },
                    label = "\u7528\u6237\u540d",
                    placeholder = "\u53ef\u7559\u7a7a",
                )
                SettingsTextField(
                    value = settings.password,
                    onValueChange = { update(settings.copy(password = it)) },
                    label = "\u5bc6\u7801",
                    placeholder = "\u53ef\u7559\u7a7a",
                    password = true,
                )
            }
        }
        item {
            SettingsGroup(title = "\u8fde\u63a5\u6d4b\u8bd5") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            enabled = !testing,
                            onClick = {
                                testing = true
                                testMessage = null
                                scope.launch {
                                    val result = WebDavClient().testConnection(settings)
                                    testing = false
                                    testOk = result.isSuccess
                                    testMessage = result.fold(
                                        onSuccess = { "\u8fde\u63a5\u6210\u529f" },
                                        onFailure = { it.message ?: "\u8fde\u63a5\u5931\u8d25" },
                                    )
                                }
                            },
                        ) {
                            Text(if (testing) "\u6d4b\u8bd5\u4e2d" else "\u6d4b\u8bd5\u8fde\u63a5")
                        }
                        Text(
                            text = testMessage ?: "\u4f7f\u7528 PROPFIND \u68c0\u67e5\u8fdc\u7aef\u76ee\u5f55\u662f\u5426\u53ef\u8bbf\u95ee\u3002",
                            color = when {
                                testMessage == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                testOk -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.error
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsEntryRow(title: String, subtitle: String, icon: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("\u203a", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun AppPreferenceSlider(
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
private fun AppPreferenceSwitch(
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
private fun AppPreferenceChoice(
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

private data class PreferenceOption(
    val text: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun AppPreferenceChoiceRow(choices: List<PreferenceOption>) {
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

private fun roundToStep(value: Float, step: Float): Double =
    (kotlin.math.round(value / step) * step).toDouble()

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
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
private fun SettingDropdownRow(
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
private fun SettingTextRow(title: String, value: String, onClick: (() -> Unit)? = null) {
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
private fun SettingsTextField(
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
private fun SettingsInfoCard(text: String) {
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
