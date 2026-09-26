package com.namsan.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.namsan.player.R
import com.namsan.player.api.BrowseItem
import com.namsan.player.api.Category
import com.namsan.player.api.Song

fun formatMs(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
fun BrowseList(
    state: BrowseState,
    onSongClick: (Song, List<Song>) -> Unit,
    onEnqueue: (List<Song>) -> Unit,
    onCategoryClick: (Category) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    when {
        state.loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Warning, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.load_failed))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
        state.items.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.empty_list), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> {
            val songs = state.items.filterIsInstance<Song>()
            LazyColumn(modifier.fillMaxSize()) {
                header?.let { item { it() } }
                if (songs.isNotEmpty()) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.songs_count, songs.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { onEnqueue(songs) }) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.add_to_queue))
                            }
                        }
                        HorizontalDivider()
                    }
                }
                items(state.items) { item ->
                    when (item) {
                        is Category -> CategoryRow(
                            item, expanded = false, depth = 0,
                            onClick = { onCategoryClick(item) },
                        )
                        is Song -> SongRow(
                            song = item,
                            onClick = { onSongClick(item, songs) },
                            onEnqueue = { onEnqueue(listOf(item)) },
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private sealed interface CatalogRow {
    val key: String

    data class CategoryNode(val category: Category, val depth: Int) : CatalogRow {
        override val key get() = "cat:${category.id}"
    }

    data class SongNode(val song: Song, val depth: Int, val siblings: List<Song>) : CatalogRow {
        override val key get() = "song:${song.id}"
    }

    data class StatusNode(val catId: String, val depth: Int, val failed: Boolean) : CatalogRow {
        override val key get() = "status:$catId"
    }
}

private fun flattenCatalog(
    items: List<BrowseItem>,
    depth: Int,
    expanded: Set<String>,
    children: Map<String, BrowseState>,
    out: MutableList<CatalogRow>,
) {
    val songs = items.filterIsInstance<Song>()
    for (item in items) {
        when (item) {
            is Category -> {
                out += CatalogRow.CategoryNode(item, depth)
                if (item.id in expanded) {
                    val child = children[item.id]
                    when {
                        child == null || child.loading ->
                            out += CatalogRow.StatusNode(item.id, depth + 1, failed = false)
                        child.error ->
                            out += CatalogRow.StatusNode(item.id, depth + 1, failed = true)
                        else -> flattenCatalog(child.items, depth + 1, expanded, children, out)
                    }
                }
            }
            is Song -> out += CatalogRow.SongNode(item, depth, songs)
        }
    }
}

/** Accordion-style catalog: categories expand in place, nesting supported. */
@Composable
fun CatalogList(
    state: BrowseState,
    expanded: Set<String>,
    children: Map<String, BrowseState>,
    onToggle: (Category) -> Unit,
    onRetryCategory: (String) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onEnqueue: (List<Song>) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Warning, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.load_failed))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
        state.items.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.empty_list), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> {
            val rows = remember(state.items, expanded, children) {
                mutableListOf<CatalogRow>().also {
                    flattenCatalog(state.items, 0, expanded, children, it)
                }
            }
            LazyColumn(modifier.fillMaxSize()) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is CatalogRow.CategoryNode -> CategoryRow(
                            row.category,
                            expanded = row.category.id in expanded,
                            depth = row.depth,
                            onClick = { onToggle(row.category) },
                        )
                        is CatalogRow.SongNode -> SongRow(
                            song = row.song,
                            depth = row.depth,
                            onClick = { onSongClick(row.song, row.siblings) },
                            onEnqueue = { onEnqueue(listOf(row.song)) },
                        )
                        is CatalogRow.StatusNode -> CategoryStatusRow(
                            depth = row.depth,
                            failed = row.failed,
                            onRetry = { onRetryCategory(row.catId) },
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun CategoryStatusRow(depth: Int, failed: Boolean, onRetry: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = (16 + depth * 24).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (failed) {
            Text(
                stringResource(R.string.load_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        } else {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CategoryRow(category: Category, expanded: Boolean, depth: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (16 + depth * 24).dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Default.KeyboardArrowDown
            else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Default.Folder, contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(12.dp))
        Text(
            category.title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.songs_count, category.songCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SongRow(song: Song, onClick: () -> Unit, onEnqueue: () -> Unit, depth: Int = 0) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (16 + depth * 24).dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.MusicNote, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (song.durationMs > 0) {
                Text(formatMs(song.durationMs), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onEnqueue) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = stringResource(R.string.add_to_queue))
        }
    }
}
