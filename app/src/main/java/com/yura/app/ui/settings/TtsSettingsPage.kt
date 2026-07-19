package com.yura.app.ui.settings

import com.yura.tts.core.TtsState
import com.yura.tts.core.TtsProvider
import com.yura.tts.core.MicrosoftVoice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yura.tts.SimpleTtsController
import com.yura.app.ui.icons.YuraIcons
import java.util.Locale

@Composable
fun CleanTtsSettingsPage(controller: SimpleTtsController) {
    val uiState by controller.state.collectAsStateWithLifecycle()
    var providerDialogOpen by remember { mutableStateOf(false) }
    var mimoVoiceDialogOpen by remember { mutableStateOf(false) }
    var microsoftVoiceDialogOpen by remember { mutableStateOf(false) }
    var microsoftLocale by remember { mutableStateOf<String?>(null) }
    var mimoKey by remember { mutableStateOf(controller.currentMimoApiKey()) }
    var microsoftKey by remember { mutableStateOf(controller.currentMicrosoftApiKey()) }
    var microsoftRegion by remember(uiState.microsoftRegion) { mutableStateOf(uiState.microsoftRegion) }

    if (providerDialogOpen) {
        ProviderPickerDialog(
            selectedProvider = uiState.provider,
            onSelect = { provider ->
                controller.selectProvider(provider)
                providerDialogOpen = false
            },
            onDismiss = { providerDialogOpen = false },
        )
    }

    if (mimoVoiceDialogOpen) {
        SimpleVoicePickerDialog(
            title = "选择 MiMo 音色",
            subtitle = "选择用于云端朗读的声音",
            voices = SimpleTtsController.MIMO_VOICES,
            selectedVoice = uiState.mimoVoice,
            onSelect = { voice ->
                controller.setMimoVoice(voice)
                mimoVoiceDialogOpen = false
            },
            onDismiss = { mimoVoiceDialogOpen = false },
        )
    }

    if (microsoftVoiceDialogOpen) {
        MicrosoftVoicePickerDialog(
            voices = uiState.microsoftVoices,
            selectedVoice = uiState.microsoftVoice,
            selectedLocale = microsoftLocale,
            onLocaleChange = { microsoftLocale = it },
            onSelect = { voice ->
                controller.setMicrosoftVoice(voice.shortName)
                microsoftLocale = null
                microsoftVoiceDialogOpen = false
            },
            onDismiss = {
                microsoftLocale = null
                microsoftVoiceDialogOpen = false
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsGroup(title = "语音服务") {
                SettingTextRow(
                    title = "默认服务",
                    value = uiState.provider.label,
                    onClick = { providerDialogOpen = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                when (uiState.provider) {
                    TtsProvider.SYSTEM -> {
                        SettingTextRow("系统朗读", uiState.engineName.ifBlank { "系统默认朗读引擎" })
                    }
                    TtsProvider.MIMO -> {
                        SettingTextRow(
                            title = "MiMo 音色",
                            value = uiState.mimoVoice,
                            onClick = { mimoVoiceDialogOpen = true },
                        )
                        SettingsTextField(
                            value = mimoKey,
                            onValueChange = {
                                mimoKey = it
                                controller.setMimoApiKey(it)
                            },
                            label = "MiMo API Key",
                            placeholder = if (uiState.hasMimoApiKey) "已保存，输入新 key 可覆盖" else "请输入 MiMo API key",
                            password = true,
                        )
                    }
                    TtsProvider.MICROSOFT -> {
                        val selectedMicrosoftVoice = uiState.microsoftVoices
                            .firstOrNull { it.shortName == uiState.microsoftVoice }
                            ?.compactDisplayName()
                            ?: uiState.microsoftVoice
                        SettingTextRow(
                            title = "Microsoft 音色",
                            value = selectedMicrosoftVoice,
                            onClick = {
                                microsoftLocale = null
                                microsoftVoiceDialogOpen = true
                            },
                        )
                        SettingsTextField(
                            value = microsoftRegion,
                            onValueChange = {
                                microsoftRegion = it
                                controller.setMicrosoftRegion(it)
                            },
                            label = "Azure Region",
                            placeholder = "例如 eastasia / japaneast",
                        )
                        SettingsTextField(
                            value = microsoftKey,
                            onValueChange = {
                                microsoftKey = it
                                controller.setMicrosoftApiKey(it)
                            },
                            label = "Azure Speech Key",
                            placeholder = if (uiState.hasMicrosoftApiKey) "已保存，输入新 key 可覆盖" else "请输入 Azure Speech key",
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
                                Text(if (uiState.microsoftVoicesLoading) "刷新中" else "刷新音色")
                            }
                            Text(
                                text = "已加载 ${uiState.microsoftVoices.size} 个音色",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
        item {
            SettingsGroup(title = "测试") {
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
                            Text(if (uiState.state == TtsState.LOADING) "准备中" else "测试语音")
                        }
                        TextButton(onClick = { controller.stop() }) {
                            Text("停止")
                        }
                    }
                    Text(
                        text = when {
                            uiState.errorMessage != null -> uiState.errorMessage.orEmpty()
                            uiState.state == TtsState.PLAYING -> "正在播放：${uiState.currentSentence}"
                            uiState.state == TtsState.LOADING -> "正在准备音频..."
                            uiState.state == TtsState.PAUSED -> "已暂停"
                            else -> "当前服务：${uiState.provider.label}。点击测试语音后应该听到声音或看到错误信息。"
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
private fun ProviderPickerDialog(
    selectedProvider: TtsProvider,
    onSelect: (TtsProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsPickerDialog(
        title = "选择默认服务",
        subtitle = "新开始的朗读将使用所选服务",
        onDismiss = onDismiss,
    ) {
        items(TtsProvider.entries, key = { it.name }) { provider ->
            val subtitle = when (provider) {
                TtsProvider.SYSTEM -> "使用手机已安装的朗读引擎"
                TtsProvider.MIMO -> "小米云端自然语音"
                TtsProvider.MICROSOFT -> "Microsoft Azure Speech 云端音色"
            }
            PickerOptionRow(
                title = provider.label,
                subtitle = subtitle,
                selected = provider == selectedProvider,
                onClick = { onSelect(provider) },
            )
        }
    }
}

@Composable
private fun SimpleVoicePickerDialog(
    title: String,
    subtitle: String,
    voices: List<String>,
    selectedVoice: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsPickerDialog(title = title, subtitle = subtitle, onDismiss = onDismiss) {
        items(voices, key = { it }) { voice ->
            PickerOptionRow(
                title = voice,
                selected = voice == selectedVoice,
                onClick = { onSelect(voice) },
            )
        }
    }
}

@Composable
private fun MicrosoftVoicePickerDialog(
    voices: List<MicrosoftVoice>,
    selectedVoice: String,
    selectedLocale: String?,
    onLocaleChange: (String?) -> Unit,
    onSelect: (MicrosoftVoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val groups = remember(voices) { groupMicrosoftVoices(voices) }
    val activeGroup = groups.firstOrNull { it.locale == selectedLocale }

    SettingsPickerDialog(
        title = activeGroup?.label ?: "选择语言",
        subtitle = if (activeGroup == null) {
            "${groups.size} 个语言与地区 · ${voices.size} 个音色"
        } else {
            "${activeGroup.voices.size} 个可用音色"
        },
        onBack = activeGroup?.let { { onLocaleChange(null) } },
        onDismiss = onDismiss,
    ) {
        if (activeGroup == null) {
            if (groups.isEmpty()) {
                item {
                    PickerEmptyState("暂无可用音色，请先填写 Azure 配置并刷新音色。")
                }
            } else {
                items(groups, key = { it.locale }) { group ->
                    val containsSelectedVoice = group.voices.any { it.shortName == selectedVoice }
                    PickerOptionRow(
                        title = group.label,
                        subtitle = buildString {
                            if (containsSelectedVoice) append("当前音色 · ")
                            append("${group.voices.size} 个音色 · ${group.locale}")
                        },
                        trailingChevron = true,
                        onClick = { onLocaleChange(group.locale) },
                    )
                }
            }
        } else {
            items(activeGroup.voices, key = { it.shortName }) { voice ->
                PickerOptionRow(
                    title = voice.displayName.substringBeforeLast(" · ${voice.locale}"),
                    subtitle = voice.shortName,
                    selected = voice.shortName == selectedVoice,
                    onClick = { onSelect(voice) },
                )
            }
        }
    }
}

@Composable
private fun SettingsPickerDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 520.dp)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(YuraIcons.Back, contentDescription = "返回")
                        }
                    } else {
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(YuraIcons.Close, contentDescription = "关闭")
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun PickerOptionRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    selected: Boolean = false,
    trailingChevron: Boolean = false,
) {
    Surface(
        onClick = onClick,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        } else {
            Color.Transparent
        },
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        YuraIcons.Check,
                        contentDescription = "已选择",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else if (trailingChevron) {
                Icon(
                    YuraIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PickerEmptyState(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun MicrosoftVoice.compactDisplayName(): String =
    displayName
        .substringBeforeLast(" · $locale")
        .removePrefix("Microsoft ")
        .trim()
        .ifBlank { shortName }
internal data class MicrosoftVoiceGroup(
    val locale: String,
    val label: String,
    val voices: List<MicrosoftVoice>,
)

internal fun microsoftVoiceGroupLocaleTag(rawLocale: String): String {
    val normalized = rawLocale.trim().replace('_', '-').ifBlank { return "und" }
    val locale = Locale.forLanguageTag(normalized)
    val language = locale.language.ifBlank { return "und" }
    return when {
        locale.country.isNotBlank() -> "$language-${locale.country}"
        locale.script.isNotBlank() -> "$language-${locale.script}"
        else -> language
    }
}
internal fun groupMicrosoftVoices(
    voices: List<MicrosoftVoice>,
    displayLocale: Locale = Locale.SIMPLIFIED_CHINESE,
): List<MicrosoftVoiceGroup> = voices
    .groupBy { voice -> microsoftVoiceGroupLocaleTag(voice.locale) }
    .map { (localeTag, localeVoices) ->
        val locale = Locale.forLanguageTag(localeTag)
        val language = locale.getDisplayLanguage(displayLocale).ifBlank { "其他语言" }
        val country = locale.getDisplayCountry(displayLocale)
        MicrosoftVoiceGroup(
            locale = localeTag,
            label = if (country.isBlank()) language else "$language（$country）",
            voices = localeVoices.sortedBy { it.displayName.lowercase(displayLocale) },
        )
    }
    .sortedWith(
        compareBy<MicrosoftVoiceGroup> {
            when (Locale.forLanguageTag(it.locale).language) {
                "zh" -> 0
                "en" -> 1
                else -> 2
            }
        }.thenBy { it.label },
    )

@Composable
fun SettingsEntryRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
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
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(YuraIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}