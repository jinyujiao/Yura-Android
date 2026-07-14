@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.yura.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.yura.app.data.Book
import com.yura.app.ui.icons.YuraIcons
import com.yura.app.ui.notes.NotesScreen
import com.yura.app.library.LibraryUiState
import com.yura.app.notes.NotesViewModel
import com.yura.app.library.LibraryViewModel
import com.yura.app.reader.ReaderActivity
import com.yura.app.reader.ReaderPreferencesStore
import com.yura.app.reader.tts.SimpleTtsController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import com.yura.app.sync.WebDavClient
import com.yura.app.ui.shelf.LibraryScreen
import com.yura.app.ui.shelf.LibraryTopBar
import com.yura.app.ui.settings.SettingsHubScreen
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


private enum class RootTab(val label: String, val icon: ImageVector) {
    Library("书架", YuraIcons.Library),
    Notes("笔记", YuraIcons.Note),
    Settings("设置", YuraIcons.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YuraApp(
    externalPublicationUri: Uri? = null,
    onExternalPublicationHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(RootTab.Library) }
    val libraryViewModel: LibraryViewModel = viewModel()
    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val notesViewModel: NotesViewModel = viewModel()
    val notesState by notesViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val navigationHazeState = rememberHazeState()
    val ttsController = remember { SimpleTtsController(context.applicationContext) }
    var coverTargetBook by remember { mutableStateOf<Book?>(null) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var shelfSort by remember { mutableStateOf(ShelfSort.RecentlyRead) }
    var sortMenuVisible by remember { mutableStateOf(false) }
    var selectedNotesBookId by remember { mutableStateOf<Long?>(null) }
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
    LaunchedEffect(notesState.message) {
        notesState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            notesViewModel.clearMessage()
        }
    }
    LaunchedEffect(externalPublicationUri) {
        externalPublicationUri?.let { uri ->
            tab = RootTab.Library
            libraryViewModel.importPublication(uri)
            onExternalPublicationHandled()
        }
    }

    BackHandler(enabled = tab == RootTab.Notes && selectedNotesBookId != null) {
        selectedNotesBookId = null
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
                val notesBook = notesState.groups.firstOrNull { it.book.id == selectedNotesBookId }?.book
                TopAppBar(
                    title = {
                        Text(
                            text = notesBook?.title ?: tab.label,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                        )
                    },
                    navigationIcon = {
                        if (tab == RootTab.Notes && selectedNotesBookId != null) {
                            IconButton(onClick = { selectedNotesBookId = null }) {
                                Icon(YuraIcons.Back, contentDescription = "返回笔记列表")
                            }
                        }
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(navigationHazeState),
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
                            RootTab.Notes -> NotesScreen(
                                state = notesState,
                                selectedBookId = selectedNotesBookId,
                                onSelectBook = { selectedNotesBookId = it },
                                onBackToBooks = { selectedNotesBookId = null },
                                onDeleteAnnotation = notesViewModel::deleteAnnotation,
                            )
                            RootTab.Settings -> SettingsHubScreen(
                                ttsController = ttsController,
                                active = tab == item,
                            )
                        }
                    }
                }
                }
            FloatingPillNavigation(
                selected = tab,
                onSelected = { tab = it },
                hazeState = navigationHazeState,
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
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val glassShape = RoundedCornerShape(999.dp)
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 12.dp,
        shape = glassShape,
        modifier = modifier
            .fillMaxWidth(0.78f)
            .widthIn(max = 340.dp)
            .height(64.dp),
    ) {
        Box(
            modifier = Modifier
                .hazeEffect(
                    state = hazeState,
                    style = HazeMaterials.thin(),
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.44f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
                        ),
                    ),
                )
                .padding(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RootTab.entries.forEach { item ->
                    val isSelected = selected == item
                    val indicatorColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            Color.Transparent
                        },
                        label = "glass-tab-indicator",
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                        },
                        label = "glass-tab-icon",
                    )
                    val labelColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        },
                        label = "glass-tab-label",
                    )
                    Surface(
                        onClick = { onSelected(item) },
                        modifier = Modifier.weight(1f),
                        color = Color.Transparent,
                        contentColor = iconColor,
                        shape = glassShape,
                    ) {
                        Row(
                            modifier = Modifier
                                .height(52.dp)
                                .padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(indicatorColor, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = iconColor,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                item.label,
                                color = labelColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
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

