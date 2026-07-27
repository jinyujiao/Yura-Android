package com.yura.app.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.yura.app.data.Book
import com.yura.app.stats.ReadingBookStat
import com.yura.app.stats.ReadingDayStat
import com.yura.app.stats.ReadingStatsUiState
import com.yura.app.stats.ReadingStatsViewModel
import com.yura.app.ui.components.YuraEmptyState
import com.yura.app.ui.icons.YuraIcons
import com.yura.app.ui.theme.YuraSpacing
import com.yura.app.ui.theme.yuraHighlightSurface
import com.yura.app.ui.theme.yuraSelectedSurface
import java.io.File
import kotlin.math.max

private enum class StatsFilter(val label: String) {
    All("全部"),
    Reading("阅读"),
    Listening("听书"),
}

@Composable
fun ReadingStatsScreen(
    modifier: Modifier = Modifier,
    viewModel: ReadingStatsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(StatsFilter.All) }
    var selectedBookIdentifier by remember { mutableStateOf<String?>(null) }
    val selectedBook = state.books.firstOrNull { it.book.identifier == selectedBookIdentifier }

    BackHandler(enabled = selectedBook != null) { selectedBookIdentifier = null }

    if (state.isLoading) return
    if (selectedBook != null) {
        BookStatsDetail(
            stat = selectedBook,
            onBack = { selectedBookIdentifier = null },
            modifier = modifier,
        )
        return
    }
    if (state.books.isEmpty() && state.recentDays.all { it.totalMs == 0L }) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            YuraEmptyState(
                icon = YuraIcons.Stats,
                title = "还没有阅读统计",
                description = "打开图书阅读或开始朗读后，这里会自动记录你的阅读时光。",
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TodaySummary(state) }
        item { ReadingTrend(state.recentDays) }
        item { StreakSummary(state) }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("按图书统计", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StatsFilter.entries.forEach { item ->
                        FilterChip(
                            selected = filter == item,
                            onClick = { filter = item },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        }
        val visibleBooks = state.books.filter { stat ->
            when (filter) {
                StatsFilter.All -> true
                StatsFilter.Reading -> stat.readingMs > 0L
                StatsFilter.Listening -> stat.listeningMs > 0L
            }
        }
        items(visibleBooks, key = { it.book.id }) { stat ->
            BookStatRow(stat) { selectedBookIdentifier = stat.book.identifier }
        }
    }
}

@Composable
private fun TodaySummary(state: ReadingStatsUiState) {
    val today = state.today ?: return
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("今天", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        formatDuration(today.totalMs),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Surface(color = MaterialTheme.colorScheme.yuraSelectedSurface, shape = RoundedCornerShape(18.dp)) {
                    Icon(YuraIcons.Timer, contentDescription = null, modifier = Modifier.padding(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryMetric("阅读", today.readingMs, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                SummaryMetric("听书", today.listeningMs, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, durationMs: Long, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(9.dp).background(color, CircleShape))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(durationMs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ReadingTrend(days: List<ReadingDayStat>) {
    var dayCount by remember { mutableIntStateOf(7) }
    val visibleDays = days.takeLast(dayCount)
    val maxDuration = max(1L, visibleDays.maxOfOrNull { it.totalMs } ?: 0L)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("阅读趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(7, 30).forEach { count ->
                        FilterChip(
                            selected = dayCount == count,
                            onClick = { dayCount = count },
                            label = { Text("$count 天") },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                visibleDays.forEach { day ->
                    val ratio = day.totalMs.toFloat() / maxDuration.toFloat()
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Surface(
                            color = if (day.totalMs > 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp),
                            modifier = Modifier.fillMaxWidth().height((ratio * 104f).coerceAtLeast(if (day.totalMs > 0L) 8f else 3f).dp),
                        ) {}
                        if (day.date.dayOfMonth == 1 || day == visibleDays.first() || day == visibleDays.last()) {
                            Text(
                                day.date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        } else {
                            Spacer(Modifier.height(17.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakSummary(state: ReadingStatsUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        SmallStatCard("连续阅读", "${state.currentStreak} 天", YuraIcons.Timer, Modifier.weight(1f))
        SmallStatCard("有效阅读日", "${state.activeDays} 天", YuraIcons.Check, Modifier.weight(1f))
        SmallStatCard("累计时长", formatDuration(state.totalReadingMs + state.totalListeningMs), YuraIcons.Stats, Modifier.weight(1f))
    }
}

@Composable
private fun SmallStatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.yuraHighlightSurface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BookStatRow(stat: ReadingBookStat, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = stat.book.cover.takeIf { it.isNotBlank() }?.let(::File),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 48.dp, height = 68.dp).clip(MaterialTheme.shapes.small),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stat.book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stat.book.author.ifBlank { "未知作者" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("阅读 ${formatDuration(stat.readingMs)} · 听书 ${formatDuration(stat.listeningMs)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                formatDuration(stat.totalMs),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BookStatsDetail(stat: ReadingBookStat, onBack: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Surface(onClick = onBack, color = Color.Transparent, contentColor = MaterialTheme.colorScheme.primary) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(YuraIcons.Back, contentDescription = null)
                    Text("返回统计", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = stat.book.cover.takeIf { it.isNotBlank() }?.let(::File), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 72.dp, height = 104.dp).clip(MaterialTheme.shapes.medium))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stat.book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(stat.book.author.ifBlank { "未知作者" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("累计 ${formatDuration(stat.totalMs)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item { SummaryMetric("阅读", stat.readingMs, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth()) }
        item { SummaryMetric("听书", stat.listeningMs, MaterialTheme.colorScheme.secondary, Modifier.fillMaxWidth()) }
        item { ReadingTrend(stat.recentDays) }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}小时${minutes}分"
        minutes > 0 -> "${minutes}分钟"
        durationMs > 0 -> "不到1分钟"
        else -> "0分钟"
    }
}
