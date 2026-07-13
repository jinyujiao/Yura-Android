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
import androidx.compose.runtime.mutableLongStateOf
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
fun WebDavSettingsPage() {
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
    var lastSyncAt by remember { mutableLongStateOf(WebDavSettingsStore.lastSyncAt(context)) }

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

