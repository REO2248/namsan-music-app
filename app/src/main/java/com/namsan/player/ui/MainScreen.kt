package com.namsan.player.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.namsan.player.R
import com.namsan.player.api.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val newSongs by vm.newSongs.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val showPlayer by vm.showPlayer.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            category?.title ?: stringResource(R.string.app_name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        if (tab == Tab.Catalog && category != null) {
                            IconButton(onClick = { vm.closeCategory() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back_to_list))
                            }
                        }
                    },
                )
            },
            bottomBar = {
                Column {
                    if (player.currentId != null && !showPlayer) {
                        MiniPlayer(
                            state = player,
                            onClick = { vm.expandPlayer(true) },
                            onTogglePlay = { vm.togglePlayPause() },
                        )
                    }
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == Tab.New,
                            onClick = { vm.selectTab(Tab.New) },
                            icon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                            label = { Text(stringResource(R.string.tab_new)) },
                        )
                        NavigationBarItem(
                            selected = tab == Tab.Catalog,
                            onClick = { vm.selectTab(Tab.Catalog) },
                            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                            label = { Text(stringResource(R.string.tab_catalog)) },
                        )
                        NavigationBarItem(
                            selected = tab == Tab.Search,
                            onClick = { vm.selectTab(Tab.Search) },
                            icon = { Icon(Icons.Default.Search, contentDescription = null) },
                            label = { Text(stringResource(R.string.tab_search)) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    Tab.New -> BrowseList(
                        state = newSongs,
                        onSongClick = { s, list -> vm.playSingle(s, list) },
                        onEnqueue = { vm.enqueue(it) },
                        onCategoryClick = {},
                        onRetry = { vm.refreshNewSongs() },
                    )
                    Tab.Catalog -> BrowseList(
                        state = catalog,
                        onSongClick = { s, list -> vm.playSingle(s, list) },
                        onEnqueue = { vm.enqueue(it) },
                        onCategoryClick = { vm.openCategory(it) },
                        onRetry = {
                            if (category != null) vm.openCategory(category!!)
                            else vm.refreshCatalog()
                        },
                    )
                    Tab.Search -> SearchPane(
                        query = searchQuery,
                        results = searchResults,
                        onQueryChange = { vm.onSearchQueryChange(it) },
                        onSongClick = { s, list -> vm.playSingle(s, list) },
                        onEnqueue = { vm.enqueue(it) },
                    )
                }
            }
        }

        if (showPlayer && player.currentId != null) {
            PlayerScreen(
                state = player,
                onCollapse = { vm.expandPlayer(false) },
                onTogglePlay = { vm.togglePlayPause() },
                onSeek = { vm.seekTo(it) },
                onNext = { vm.next() },
                onPrevious = { vm.previous() },
                onJumpTo = { vm.jumpToQueue(it) },
                onRemoveAt = { vm.removeFromQueue(it) },
                onDownload = { startDownload(context, vm) },
            )
        }
    }
}

private fun startDownload(context: Context, vm: MainViewModel) {
    val (url, title) = vm.downloadCurrent() ?: return
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(title)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "namsan/$title.mp3")
    context.getSystemService(DownloadManager::class.java)?.enqueue(request)
    Toast.makeText(context, R.string.download_started, Toast.LENGTH_SHORT).show()
}

@Composable
private fun MiniPlayer(
    state: PlayerUi,
    onClick: () -> Unit,
    onTogglePlay: () -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column {
            LinearProgressIndicator(
                progress = {
                    if (state.durationMs > 0)
                        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                    else 0f
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    state.currentTitle ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (state.buffering) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onTogglePlay) {
                        Icon(
                            if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(
                                if (state.playing) R.string.pause else R.string.play
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPane(
    query: String,
    results: BrowseState,
    onQueryChange: (String) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onEnqueue: (List<Song>) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )
        if (query.isNotBlank() && !results.loading && !results.error && results.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.empty_search),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            BrowseList(
                state = results,
                onSongClick = onSongClick,
                onEnqueue = onEnqueue,
                onCategoryClick = {},
                onRetry = { onQueryChange(query) },
            )
        }
    }
}
