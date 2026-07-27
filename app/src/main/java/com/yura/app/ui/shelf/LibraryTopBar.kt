package com.yura.app.ui.shelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yura.app.ui.icons.YuraIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTopBar(
    searchExpanded: Boolean,
    searchQuery: String,
    onSearchExpandedChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    sort: ShelfSort,
    sortMenuVisible: Boolean,
    onSortMenuVisibleChange: (Boolean) -> Unit,
    onSortChange: (ShelfSort) -> Unit,
    importing: Boolean,
    importCompleted: Int,
    importTotal: Int,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compactTitle = maxWidth < 170.dp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compactTitle) 8.dp else 12.dp),
                ) {
                    if (!searchExpanded || !compactTitle) {
                        Text(
                            text = "Yura",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                    if (importing && importTotal > 1 && !searchExpanded) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Text(
                                text = if (compactTitle) "$importCompleted/$importTotal" else "导入 $importCompleted/$importTotal",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                    if (searchExpanded) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.weight(1f),
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        text = "搜索书名或作者",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                ),
                            )
                        }
                    }
                }
            }
        },
        actions = {
            CompactToolbarButton(
                icon = if (searchExpanded) YuraIcons.Close else YuraIcons.Search,
                contentDescription = if (searchExpanded) "关闭搜索" else "搜索书籍",
                onClick = {
                    val expanded = !searchExpanded
                    onSearchExpandedChange(expanded)
                    if (!expanded) onSearchQueryChange("")
                },
            )
            Box {
                CompactToolbarButton(icon = YuraIcons.Sort, contentDescription = "书架排序", onClick = { onSortMenuVisibleChange(true) })
                DropdownMenu(
                    expanded = sortMenuVisible,
                    onDismissRequest = { onSortMenuVisibleChange(false) },
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.width(176.dp),
                ) {
                    Text(
                        text = "书架排序",
                        modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ShelfSort.entries.forEach { option ->
                        val selected = sort == option
                        DropdownMenuItem(
                            text = {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                ) {
                                    Text(
                                        text = option.label,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            },
                            onClick = {
                                onSortChange(option)
                                onSortMenuVisibleChange(false)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            if (importing) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                }
            } else {
                CompactToolbarButton(
                    icon = YuraIcons.Add,
                    contentDescription = "批量导入书籍",
                    onClick = onImport,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        modifier = modifier,
    )
}

@Composable
private fun CompactToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
