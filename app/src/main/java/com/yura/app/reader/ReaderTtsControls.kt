package com.yura.app.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yura.app.reader.tts.SimpleTtsController
import com.yura.app.ui.icons.YuraIcons
import kotlin.math.roundToInt

@Composable
fun DraggableTtsFloatingButton(
    onClick: () -> Unit,
    controlsVisible: Boolean,
    loadPosition: () -> Pair<Float, Float>,
    savePosition: (Pair<Float, Float>) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        var position by remember { mutableStateOf(loadPosition()) }
        val maxX = with(density) { (maxWidth - 104.dp).toPx().coerceAtLeast(0f) }
        val bottomReserve = if (controlsVisible) 116.dp else 28.dp
        val maxY = with(density) { (maxHeight - bottomReserve - 48.dp).toPx().coerceAtLeast(0f) }

        TtsFloatingButton(
            onClick = onClick,
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (position.first.coerceIn(0f, 1f) * maxX).roundToInt(),
                        y = (position.second.coerceIn(0f, 1f) * maxY).roundToInt(),
                    )
                }
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragEnd = { savePosition(position) },
                        onDragCancel = { savePosition(position) },
                    ) { change, dragAmount ->
                        change.consume()
                        val currentX = position.first.coerceIn(0f, 1f) * maxX
                        val currentY = position.second.coerceIn(0f, 1f) * maxY
                        val nextX = (currentX + dragAmount.x).coerceIn(0f, maxX)
                        val nextY = (currentY + dragAmount.y).coerceIn(0f, maxY)
                        position = Pair(
                            if (maxX <= 0f) 0f else nextX / maxX,
                            if (maxY <= 0f) 0f else nextY / maxY,
                        )
                    }
                },
        )
    }
}

@Composable
private fun TtsFloatingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(YuraIcons.ReadAloud, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("朗读中", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsPanel(
    controller: SimpleTtsController,
    visible: Boolean,
    chapterTitle: String,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uiState by controller.state.collectAsStateWithLifecycle()
    val playing = uiState.state == SimpleTtsController.State.PLAYING
    val loading = uiState.state == SimpleTtsController.State.LOADING
    val active = uiState.state !in setOf(SimpleTtsController.State.IDLE, SimpleTtsController.State.ERROR)
    val speedIndex = SimpleTtsController.PLAYBACK_SPEEDS
        .indexOf(uiState.playbackSpeed)
        .coerceAtLeast(0)
    val paragraphNumber = (uiState.paragraphIndex + 1).coerceAtLeast(0)
    val paragraphProgress = if (uiState.paragraphTotal > 0) {
        (paragraphNumber.toFloat() / uiState.paragraphTotal).coerceIn(0f, 1f)
    } else {
        0f
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 640.dp),
            contentPadding = PaddingValues(start = 22.dp, top = 4.dp, end = 22.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("朗读控制", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(
                            chapterTitle.ifBlank { "当前章节" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TtsStatusBadge(uiState.state)
                }
            }

            if (uiState.errorMessage != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(uiState.errorMessage.orEmpty(), modifier = Modifier.padding(14.dp))
                    }
                }
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("阅读进度", fontWeight = FontWeight.Bold)
                            Text(
                                if (uiState.paragraphTotal > 0) "段落 $paragraphNumber / ${uiState.paragraphTotal}" else "等待开始",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { paragraphProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp),
                        )
                    }
                }
            }

            item {
                TtsSectionTitle("语音来源")
                LazyRow(
                    contentPadding = PaddingValues(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(SimpleTtsController.Provider.entries, key = { it.name }) { provider ->
                        PreferenceChoice(
                            text = provider.label,
                            selected = uiState.provider == provider,
                            onClick = { controller.selectProvider(provider) },
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TtsSectionTitle("朗读倍速")
                    Text(
                        "${uiState.playbackSpeed.formatSpeed()}x",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Slider(
                    value = speedIndex.toFloat(),
                    onValueChange = { value ->
                        val index = value.roundToInt().coerceIn(0, SimpleTtsController.PLAYBACK_SPEEDS.lastIndex)
                        controller.setPlaybackSpeed(SimpleTtsController.PLAYBACK_SPEEDS[index])
                    },
                    valueRange = 0f..SimpleTtsController.PLAYBACK_SPEEDS.lastIndex.toFloat(),
                    steps = (SimpleTtsController.PLAYBACK_SPEEDS.size - 2).coerceAtLeast(0),
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        YuraIcons.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    TtsSectionTitle("定时关闭")
                }
                LazyRow(
                    contentPadding = PaddingValues(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(listOf(0, 15, 30, 60)) { minutes ->
                        PreferenceChoice(
                            text = if (minutes == 0) "关闭" else "$minutes 分钟",
                            selected = uiState.sleepTimerMinutes == minutes,
                            onClick = { controller.setSleepTimer(minutes) },
                        )
                    }
                }
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(26.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TtsTransportButton(
                            icon = YuraIcons.SkipPrevious,
                            label = "上一句",
                            enabled = !loading && uiState.paragraphTotal > 0,
                            onClick = controller::previous,
                        )
                        TtsPrimaryButton(
                            playing = playing,
                            loading = loading,
                            onClick = {
                                if (playing) controller.pause() else controller.play()
                            },
                        )
                        TtsTransportButton(
                            icon = YuraIcons.SkipNext,
                            label = "下一句",
                            enabled = !loading && uiState.paragraphTotal > 0,
                            onClick = controller::next,
                        )
                        TtsTransportButton(
                            icon = YuraIcons.Stop,
                            label = "停止",
                            enabled = active,
                            onClick = {
                                controller.stop()
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TtsStatusBadge(state: SimpleTtsController.State) {
    val label = when (state) {
        SimpleTtsController.State.IDLE -> "准备朗读"
        SimpleTtsController.State.LOADING -> "生成语音中"
        SimpleTtsController.State.PLAYING -> "正在朗读"
        SimpleTtsController.State.PAUSED -> "已暂停"
        SimpleTtsController.State.ERROR -> "朗读异常"
    }
    val containerColor = if (state == SimpleTtsController.State.ERROR) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (state == SimpleTtsController.State.ERROR) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(color = containerColor, contentColor = contentColor, shape = RoundedCornerShape(999.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TtsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TtsTransportButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.72f else 0.34f),
            contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(23.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TtsPrimaryButton(
    playing: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val label = when {
        loading -> "正在加载"
        playing -> "暂停"
        else -> "播放"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            onClick = onClick,
            enabled = !loading,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 4.dp,
            modifier = Modifier.size(64.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(27.dp),
                    )
                } else {
                    Icon(
                        imageVector = if (playing) YuraIcons.Pause else YuraIcons.Play,
                        contentDescription = label,
                        modifier = Modifier.size(31.dp),
                    )
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}
