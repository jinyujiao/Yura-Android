@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.yura.app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    preferences: EpubPreferences,
    autoTheme: Boolean,
    onDismiss: () -> Unit,
    onAutoThemeChange: (Boolean) -> Unit,
    onPreferencesChange: (EpubPreferences) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var previewPreferences by remember(preferences) { mutableStateOf(preferences) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 680.dp)
                .overscroll(null),
            contentPadding = PaddingValues(start = 22.dp, top = 4.dp, end = 22.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text(
                    "阅读设置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
            }

            item {
                PreferenceSectionTitle("主题")
                LazyRow(
                    contentPadding = PaddingValues(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        PreferenceChoice(
                            text = "自动",
                            selected = autoTheme,
                            onClick = { onAutoThemeChange(true) },
                        )
                    }
                    item {
                        PreferenceChoice(
                            text = "浅色",
                            selected = !autoTheme && preferences.theme == Theme.LIGHT,
                            onClick = {
                                onAutoThemeChange(false)
                                onPreferencesChange(preferences.copy(theme = Theme.LIGHT))
                            },
                        )
                    }
                    item {
                        PreferenceChoice(
                            text = "深色",
                            selected = !autoTheme && preferences.theme == Theme.DARK,
                            onClick = {
                                onAutoThemeChange(false)
                                onPreferencesChange(preferences.copy(theme = Theme.DARK))
                            },
                        )
                    }
                    item {
                        PreferenceChoice(
                            text = "米色",
                            selected = !autoTheme && preferences.theme == Theme.SEPIA,
                            onClick = {
                                onAutoThemeChange(false)
                                onPreferencesChange(preferences.copy(theme = Theme.SEPIA))
                            },
                        )
                    }
                }
            }

            item {
                ReadingPreview(previewPreferences)
            }

            item {
                PreferenceCard(title = "文字") {
                    PreferenceSlider(
                        title = "字号",
                        valueLabel = "${((preferences.fontSize ?: 1.0) * 100).toInt()}%",
                        value = (preferences.fontSize ?: 1.0).toFloat(),
                        valueRange = 0.8f..2.0f,
                        steps = 11,
                        onValueChange = { value -> previewPreferences = previewPreferences.copy(fontSize = value.toDouble()) },
                        onValueChangeFinished = { value ->
                            onPreferencesChange(preferences.copy(fontSize = roundToStep(value, 0.1f), publisherStyles = false))
                        },
                    )
                    PreferenceSlider(
                        title = "行高",
                        valueLabel = String.format(Locale.ROOT, "%.1f", preferences.lineHeight ?: 1.5),
                        value = (preferences.lineHeight ?: 1.5).toFloat(),
                        valueRange = 1.0f..2.2f,
                        steps = 5,
                        onValueChange = { value -> previewPreferences = previewPreferences.copy(lineHeight = value.toDouble()) },
                        onValueChangeFinished = { value ->
                            onPreferencesChange(preferences.copy(lineHeight = roundToStep(value, 0.2f), publisherStyles = false))
                        },
                    )
                    PreferenceSlider(
                        title = "字间距",
                        valueLabel = "${((preferences.letterSpacing ?: 0.0) * 100).toInt()}%",
                        value = (preferences.letterSpacing ?: 0.0).toFloat(),
                        valueRange = 0f..1f,
                        steps = 9,
                        onValueChange = { value -> previewPreferences = previewPreferences.copy(letterSpacing = value.toDouble()) },
                        onValueChangeFinished = { value ->
                            onPreferencesChange(preferences.copy(letterSpacing = roundToStep(value, 0.1f), publisherStyles = false))
                        },
                    )
                }
            }

            item {
                PreferenceCard(title = "段落") {
                    PreferenceSlider(
                        title = "段首缩进",
                        valueLabel = "${(preferences.paragraphIndent ?: 0.0).toInt()} 字",
                        value = (preferences.paragraphIndent ?: 0.0).toFloat(),
                        valueRange = 0f..4f,
                        steps = 3,
                        onValueChange = { value -> previewPreferences = previewPreferences.copy(paragraphIndent = value.toDouble()) },
                        onValueChangeFinished = { value ->
                            onPreferencesChange(preferences.copy(paragraphIndent = value.roundToInt().toDouble(), publisherStyles = false))
                        },
                    )
                    PreferenceSlider(
                        title = "段间距",
                        valueLabel = "${((preferences.paragraphSpacing ?: 0.0) * 100).toInt()}%",
                        value = (preferences.paragraphSpacing ?: 0.0).toFloat(),
                        valueRange = 0f..2f,
                        steps = 9,
                        onValueChange = { value -> previewPreferences = previewPreferences.copy(paragraphSpacing = value.toDouble()) },
                        onValueChangeFinished = { value ->
                            onPreferencesChange(preferences.copy(paragraphSpacing = roundToStep(value, 0.2f), publisherStyles = false))
                        },
                    )
                }
            }

            item {
                PreferenceCard(title = "阅读模式") {
                    PreferenceSwitch(
                        title = "滚动阅读",
                        subtitle = "关闭后使用分页阅读",
                        checked = preferences.scroll == true,
                        onCheckedChange = { checked -> onPreferencesChange(preferences.copy(scroll = checked)) },
                    )
                    PreferenceSwitch(
                        title = "使用书籍自带版式",
                        subtitle = "开启后，字号、行高和段落样式可能由 EPUB 原始样式决定",
                        checked = preferences.publisherStyles != false,
                        onCheckedChange = { checked -> onPreferencesChange(preferences.copy(publisherStyles = checked)) },
                    )
                    if (preferences.publisherStyles != false) {
                        Text(
                            text = "调整自定义排版时，将自动关闭书籍自带版式。",
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    PreferenceSectionTitle("分页栏数")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("自动", "单栏", "双栏")) { label ->
                            val selected = when (label) {
                                "自动" -> preferences.columnCount == null || preferences.columnCount == ColumnCount.AUTO
                                "单栏" -> preferences.columnCount == ColumnCount.ONE
                                else -> preferences.columnCount == ColumnCount.TWO
                            }
                            PreferenceChoice(
                                text = label,
                                selected = selected,
                                onClick = {
                                    val updated = when (label) {
                                        "自动" -> preferences.copy(columnCount = ColumnCount.AUTO, spread = Spread.NEVER, scroll = false)
                                        "单栏" -> preferences.copy(columnCount = ColumnCount.ONE, spread = Spread.NEVER, scroll = false)
                                        else -> preferences.copy(columnCount = ColumnCount.TWO, spread = Spread.ALWAYS, scroll = false)
                                    }
                                    onPreferencesChange(updated)
                                },
                            )
                        }
                    }
                }
            }

            item {
                TextButton(
                    onClick = {
                        onPreferencesChange(ReaderPreferencesStore.defaults.copy(theme = preferences.theme))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("恢复默认排版", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReadingPreview(preferences: EpubPreferences) {
    val scale = (preferences.fontSize ?: 1.0).toFloat().coerceIn(0.8f, 2f)
    val fontSize = 16f * scale
    val lineHeight = fontSize * (preferences.lineHeight ?: 1.5).toFloat().coerceIn(1f, 2.2f)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "排版预览",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "阅读让时间慢下来，也让遥远的世界变得触手可及。",
                fontSize = fontSize.sp,
                lineHeight = lineHeight.sp,
                letterSpacing = (preferences.letterSpacing ?: 0.0).toFloat().em,
            )
        }
    }
}

@Composable
private fun PreferenceCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PreferenceSectionTitle(title)
            content()
        }
    }
}

@Composable
private fun PreferenceSlider(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                when (title) {
                    "字号" -> "${(sliderValue * 100).toInt()}%"
                    "行高" -> String.format(Locale.ROOT, "%.1f", sliderValue)
                    "段首缩进" -> "${sliderValue.roundToInt()} 字"
                    "段间距", "字间距" -> "${(sliderValue * 100).toInt()}%"
                    else -> valueLabel
                },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { value ->
                sliderValue = value
                onValueChange(value)
            },
            onValueChangeFinished = { onValueChangeFinished(sliderValue) },
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Composable
private fun PreferenceSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
fun PreferenceChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.background(
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
            },
            shape = RoundedCornerShape(18.dp),
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun PreferenceSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun roundToStep(value: Float, step: Float): Double =
    (kotlin.math.round(value / step) * step).toDouble()

fun Float.formatSpeed(): String =
    if (this % 1f == 0f) toInt().toString() else String.format(Locale.ROOT, "%.1f", this)
