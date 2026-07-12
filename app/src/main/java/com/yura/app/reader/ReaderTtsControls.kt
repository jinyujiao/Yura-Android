package com.yura.app.reader

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yura.app.reader.tts.SimpleTtsController
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
        val maxX = with(density) { (maxWidth - 116.dp).toPx().coerceAtLeast(0f) }
        val bottomReserve = if (controlsVisible) 116.dp else 28.dp
        val maxY = with(density) { (maxHeight - bottomReserve - 56.dp).toPx().coerceAtLeast(0f) }

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
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("朗读中", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
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
    val uiState by controller.state.collectAsState()
    val playing = uiState.state == SimpleTtsController.State.PLAYING
    val speedIndex = SimpleTtsController.PLAYBACK_SPEEDS
        .indexOf(uiState.playbackSpeed)
        .coerceAtLeast(0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("\u6717\u8bfb\u63a7\u5236", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                chapterTitle.ifBlank { "\u5f53\u524d\u7ae0\u8282" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                uiState.currentSentence.ifBlank { uiState.errorMessage ?: "\u51c6\u5907\u6717\u8bfb" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.errorMessage == null) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.error
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SimpleTtsController.Provider.entries.forEach { provider ->
                    PreferenceChoice(
                        text = provider.label,
                        selected = uiState.provider == provider,
                        onClick = { controller.selectProvider(provider) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\u500d\u901f", fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${uiState.playbackSpeed.formatSpeed()}x", color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = speedIndex.toFloat(),
                onValueChange = { value ->
                    val index = value.toInt().coerceIn(0, SimpleTtsController.PLAYBACK_SPEEDS.lastIndex)
                    controller.setPlaybackSpeed(SimpleTtsController.PLAYBACK_SPEEDS[index])
                },
                valueRange = 0f..SimpleTtsController.PLAYBACK_SPEEDS.lastIndex.toFloat(),
                steps = (SimpleTtsController.PLAYBACK_SPEEDS.size - 2).coerceAtLeast(0),
            )

            Text(
                "\u53e5\u5b50 ${(uiState.sentenceIndex + 1).coerceAtLeast(0)} / ${uiState.sentenceTotal}  \u6bb5\u843d ${(uiState.paragraphIndex + 1).coerceAtLeast(0)} / ${uiState.paragraphTotal}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 15, 30).forEach { minutes ->
                    PreferenceChoice(
                        text = if (minutes == 0) "Timer off" else "$minutes min",
                        selected = uiState.sleepTimerMinutes == minutes,
                        onClick = { controller.setSleepTimer(minutes) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = controller::previous) { Text("\u4e0a\u4e00\u53e5") }
                TextButton(
                    onClick = {
                        if (playing) {
                            controller.pause()
                        } else {
                            controller.play()
                        }
                    },
                ) {
                    Text(if (playing) "\u6682\u505c" else "\u64ad\u653e")
                }
                TextButton(onClick = controller::next) { Text("\u4e0b\u4e00\u53e5") }
                TextButton(
                    onClick = {
                        controller.stop()
                        onDismiss()
                    },
                ) {
                    Text("\u505c\u6b62")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

