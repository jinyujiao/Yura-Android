package com.yura.app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
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
                .heightIn(max = 620.dp)
                .padding(horizontal = 22.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text(
                    "\u9605\u8bfb\u8bbe\u7f6e",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                PreferenceSectionTitle("\u4e3b\u9898")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferenceChoice(
                        text = "\u81ea\u52a8",
                        selected = autoTheme,
                        onClick = { onAutoThemeChange(true) },
                    )
                    PreferenceChoice(
                        text = "\u6d45\u8272",
                        selected = !autoTheme && preferences.theme == Theme.LIGHT,
                        onClick = {
                            onAutoThemeChange(false)
                            onPreferencesChange(preferences.copy(theme = Theme.LIGHT))
                        },
                    )
                    PreferenceChoice(
                        text = "\u6df1\u8272",
                        selected = !autoTheme && preferences.theme == Theme.DARK,
                        onClick = {
                            onAutoThemeChange(false)
                            onPreferencesChange(preferences.copy(theme = Theme.DARK))
                        },
                    )
                    PreferenceChoice(
                        text = "\u7c73\u8272",
                        selected = !autoTheme && preferences.theme == Theme.SEPIA,
                        onClick = {
                            onAutoThemeChange(false)
                            onPreferencesChange(preferences.copy(theme = Theme.SEPIA))
                        },
                    )
                }
            }
            item {
                PreferenceSlider(
                    title = "\u5b57\u53f7",
                    valueLabel = "${((preferences.fontSize ?: 1.0) * 100).toInt()}%",
                    value = (preferences.fontSize ?: 1.0).toFloat(),
                    valueRange = 0.8f..2.0f,
                    steps = 11,
                    onValueChange = { value ->
                        onPreferencesChange(preferences.copy(fontSize = roundToStep(value, 0.1f), publisherStyles = false))
                    },
                )
            }
            item {
                PreferenceSlider(
                    title = "\u884c\u9ad8",
                    valueLabel = String.format(Locale.ROOT, "%.1f", preferences.lineHeight ?: 1.5),
                    value = (preferences.lineHeight ?: 1.5).toFloat(),
                    valueRange = 1.0f..2.2f,
                    steps = 5,
                    onValueChange = { value ->
                        onPreferencesChange(preferences.copy(lineHeight = roundToStep(value, 0.2f), publisherStyles = false))
                    },
                )
            }
            item {
                PreferenceSlider(
                    title = "\u6bb5\u9996\u7f29\u8fdb",
                    valueLabel = "${(preferences.paragraphIndent ?: 0.0).toInt()} \u5b57",
                    value = (preferences.paragraphIndent ?: 0.0).toFloat(),
                    valueRange = 0f..4f,
                    steps = 3,
                    onValueChange = { value ->
                        onPreferencesChange(preferences.copy(paragraphIndent = value.toInt().toDouble(), publisherStyles = false))
                    },
                )
            }
            item {
                PreferenceSlider(
                    title = "\u6bb5\u95f4\u8ddd",
                    valueLabel = "${((preferences.paragraphSpacing ?: 0.0) * 100).toInt()}%",
                    value = (preferences.paragraphSpacing ?: 0.0).toFloat(),
                    valueRange = 0f..2f,
                    steps = 9,
                    onValueChange = { value ->
                        onPreferencesChange(preferences.copy(paragraphSpacing = roundToStep(value, 0.2f), publisherStyles = false))
                    },
                )
            }
            item {
                PreferenceSlider(
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
            item {
                PreferenceSwitch(
                    title = "\u6eda\u52a8\u9605\u8bfb",
                    subtitle = "\u5173\u95ed\u540e\u4f7f\u7528\u5206\u9875\u9605\u8bfb",
                    checked = preferences.scroll == true,
                    onCheckedChange = { checked -> onPreferencesChange(preferences.copy(scroll = checked)) },
                )
            }
            item {
                PreferenceSwitch(
                    title = "\u4f7f\u7528\u4e66\u7c4d\u81ea\u5e26\u7248\u5f0f",
                    subtitle = "\u5f00\u542f\u540e\uff0c\u90e8\u5206\u5b57\u53f7\u3001\u884c\u9ad8\u3001\u7f29\u8fdb\u548c\u5b57\u95f4\u8ddd\u4f1a\u7531 EPUB \u539f\u59cb\u6837\u5f0f\u51b3\u5b9a",
                    checked = preferences.publisherStyles != false,
                    onCheckedChange = { checked -> onPreferencesChange(preferences.copy(publisherStyles = checked)) },
                )
            }
            item {
                PreferenceSectionTitle("\u7248\u5f0f")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferenceChoice(
                        text = "\u81ea\u52a8",
                        selected = preferences.columnCount == null || preferences.columnCount == ColumnCount.AUTO,
                        onClick = {
                            onPreferencesChange(preferences.copy(columnCount = ColumnCount.AUTO, spread = Spread.NEVER, scroll = false, publisherStyles = false))
                        },
                    )
                    PreferenceChoice(
                        text = "\u5355\u680f",
                        selected = preferences.columnCount == ColumnCount.ONE,
                        onClick = {
                            onPreferencesChange(preferences.copy(columnCount = ColumnCount.ONE, spread = Spread.NEVER, scroll = false, publisherStyles = false))
                        },
                    )
                    PreferenceChoice(
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
private fun PreferenceSlider(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PreferenceSectionTitle(title)
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
private fun PreferenceSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        shape = RoundedCornerShape(22.dp),
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

