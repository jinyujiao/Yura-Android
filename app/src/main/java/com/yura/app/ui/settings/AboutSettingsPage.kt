package com.yura.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yura.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AboutSettingsPage(onOpenLicenses: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SettingsInfoCard(
                "Yura ${BuildConfig.VERSION_NAME}\n一个专注 EPUB、TXT 阅读与自然朗读的本地优先阅读器。",
            )
        }
        item {
            SettingsGroup(title = "应用信息") {
                SettingTextRow(title = "版本", value = BuildConfig.VERSION_NAME)
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
                SettingTextRow(title = "开源许可", value = "查看", onClick = onOpenLicenses)
            }
        }
        item {
            SettingsInfoCard(
                "Yura 自有代码采用 Apache License 2.0。Readium 和其他第三方组件继续遵循各自的开源许可证。",
            )
        }
    }
}

@Composable
fun OpenSourceLicensesPage() {
    val context = LocalContext.current
    val licenseText = produceState(initialValue = "正在加载许可信息……", context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("licenses/open_source_licenses.txt")
                    .bufferedReader()
                    .use { it.readText() }
            }.getOrElse { error ->
                "无法读取开源许可信息：${error.message ?: "未知错误"}"
            }
        }
    }.value

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "Yura 感谢以下开源项目及其贡献者。许可证原文随应用一同分发。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectionContainer {
                    Text(
                        text = licenseText,
                        modifier = Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
    }
}
