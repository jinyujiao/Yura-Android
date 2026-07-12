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
fun CleanTtsSettingsPage(controller: SimpleTtsController) {
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
fun SettingsEntryRow(title: String, subtitle: String, icon: String, onClick: () -> Unit) {
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

