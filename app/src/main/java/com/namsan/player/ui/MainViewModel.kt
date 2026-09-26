@file:OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.namsan.player.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.namsan.player.AppGraph
import com.namsan.player.api.BrowseItem
import com.namsan.player.api.Category
import com.namsan.player.api.Song
import com.namsan.player.api.SongDetail
import com.namsan.player.player.PlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

enum class Tab { New, Catalog, Search }

data class BrowseState(
    val loading: Boolean = false,
    val error: Boolean = false,
    val items: List<BrowseItem> = emptyList(),
)

data class QueueEntry(val mediaId: String, val title: String)

data class PlayerUi(
    val ready: Boolean = false,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val currentId: String? = null,
    val currentTitle: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queue: List<QueueEntry> = emptyList(),
    val queueIndex: Int = -1,
    val detail: SongDetail? = null,
    val playbackError: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppGraph.songs

    private val _tab = MutableStateFlow(Tab.New)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _newSongs = MutableStateFlow(BrowseState())
    val newSongs: StateFlow<BrowseState> = _newSongs.asStateFlow()

    private val _catalog = MutableStateFlow(BrowseState())
    val catalog: StateFlow<BrowseState> = _catalog.asStateFlow()

    /** Expanded category ids in the catalog accordion. */
    private val _expanded = MutableStateFlow<Set<String>>(emptySet())
    val expanded: StateFlow<Set<String>> = _expanded.asStateFlow()

    /** Children of each fetched category, keyed by category id. */
    private val _categoryChildren = MutableStateFlow<Map<String, BrowseState>>(emptyMap())
    val categoryChildren: StateFlow<Map<String, BrowseState>> = _categoryChildren.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow(BrowseState())
    val searchResults: StateFlow<BrowseState> = _searchResults.asStateFlow()

    private val _player = MutableStateFlow(PlayerUi())
    val player: StateFlow<PlayerUi> = _player.asStateFlow()

    val showPlayer = MutableStateFlow(false)

    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var searchJob: Job? = null
    private var prefetchJob: Job? = null
    private var detailForId: String? = null

    init {
        connectController()
        refreshNewSongs()
        refreshCatalog()
    }

    // ------------------------------------------------------------- controller

    private fun connectController() {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            try {
                controller = future.get()
                controller?.addListener(playerListener)
                syncPlayerState()
                startTicker()
            } catch (_: Exception) {
                // Service failed to bind; leave player UI disabled.
            }
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncPlayerState()
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                val c = controller ?: break
                if (c.isPlaying || c.playbackState == Player.STATE_BUFFERING) {
                    _player.update {
                        it.copy(
                            positionMs = c.currentPosition.coerceAtLeast(0),
                            durationMs = c.duration.coerceAtLeast(0),
                        )
                    }
                }
                delay(500)
            }
        }
    }

    private fun syncPlayerState() {
        val c = controller ?: return
        val current = c.currentMediaItem
        val queue = (0 until c.mediaItemCount).map { i ->
            val item = c.getMediaItemAt(i)
            QueueEntry(item.mediaId, item.mediaMetadata.title?.toString() ?: item.mediaId)
        }
        _player.update {
            it.copy(
                ready = true,
                playing = c.isPlaying,
                buffering = c.playbackState == Player.STATE_BUFFERING,
                currentId = current?.mediaId,
                currentTitle = current?.mediaMetadata?.title?.toString(),
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = c.duration.coerceAtLeast(0),
                queue = queue,
                queueIndex = c.currentMediaItemIndex.takeIf { i -> i >= 0 } ?: -1,
                playbackError = c.playerError != null,
            )
        }
        val id = current?.mediaId
        if (id != null && id != detailForId) {
            detailForId = id
            viewModelScope.launch {
                runCatching { repo.detail(id) }
                    .onSuccess { d -> _player.update { it.copy(detail = d) } }
                    .onFailure { _player.update { it.copy(detail = null) } }
            }
        } else if (id == null) {
            detailForId = null
            _player.update { it.copy(detail = null) }
        }
    }

    // --------------------------------------------------------------- browsing

    fun selectTab(t: Tab) {
        _tab.value = t
    }

    fun refreshNewSongs() {
        _newSongs.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            runCatching { AppGraph.api.newSongs() }
                .onSuccess { list -> _newSongs.value = BrowseState(items = list) }
                .onFailure { _newSongs.update { it.copy(loading = false, error = true) } }
        }
    }

    fun refreshCatalog() {
        _expanded.value = emptySet()
        _categoryChildren.value = emptyMap()
        _catalog.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            runCatching { AppGraph.api.browse("") }
                .onSuccess { list ->
                    _catalog.value = BrowseState(items = list)
                    prefetchCatalog()
                }
                .onFailure { _catalog.update { it.copy(loading = false, error = true) } }
        }
    }

    /** Background BFS over every category so expansion is instant once warm. */
    private fun prefetchCatalog() {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            val queue = ArrayDeque(
                _catalog.value.items.filterIsInstance<Category>().map { it.id }
            )
            while (true) {
                val id = queue.removeFirstOrNull() ?: break
                val existing = _categoryChildren.value[id]
                if (existing != null && (existing.loading || !existing.error)) continue
                loadCategory(id)
                _categoryChildren.value[id]?.items.orEmpty()
                    .filterIsInstance<Category>()
                    .forEach { queue.addLast(it.id) }
            }
        }
    }

    private suspend fun loadCategory(id: String) {
        _categoryChildren.update { it + (id to BrowseState(loading = true)) }
        runCatching { AppGraph.api.browse(id) }
            .onSuccess { list ->
                _categoryChildren.update { it + (id to BrowseState(items = list)) }
            }
            .onFailure {
                _categoryChildren.update { it + (id to BrowseState(error = true)) }
            }
    }

    fun toggleCategory(category: Category) {
        if (category.id in _expanded.value) {
            _expanded.update { it - category.id }
            return
        }
        _expanded.update { it + category.id }
        val child = _categoryChildren.value[category.id]
        if (child == null || child.error) {
            viewModelScope.launch { loadCategory(category.id) }
        }
    }

    fun retryCategory(id: String) {
        viewModelScope.launch { loadCategory(id) }
    }

    fun onSearchQueryChange(q: String) {
        _searchQuery.value = q
        searchJob?.cancel()
        if (q.isBlank()) {
            _searchResults.value = BrowseState()
            return
        }
        searchJob = viewModelScope.launch {
            delay(600)
            _searchResults.update { it.copy(loading = true, error = false) }
            runCatching { AppGraph.api.search(q) }
                .onSuccess { list -> _searchResults.value = BrowseState(items = list) }
                .onFailure { _searchResults.update { it.copy(loading = false, error = true) } }
        }
    }

    // ---------------------------------------------------------------- playing

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(Uri.parse("namsan://song/$id"))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setDurationMs(durationMs.takeIf { it > 0 })
                .build(),
        )
        .build()

    /** Play `songs[startIndex]`, with the whole visible list as the queue. */
    fun playAll(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val c = controller ?: return
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex.coerceIn(songs.indices), 0L)
        c.prepare()
        c.play()
        showPlayer.value = true
    }

    fun playSingle(song: Song, context: List<Song>? = null) {
        val list = context ?: listOf(song)
        val idx = context?.indexOfFirst { it.id == song.id }?.takeIf { it >= 0 } ?: 0
        playAll(list, idx)
    }

    fun enqueue(songs: List<Song>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playAll(songs, 0)
            return
        }
        c.addMediaItems(songs.map { it.toMediaItem() })
        syncPlayerState()
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _player.update { it.copy(positionMs = positionMs) }
    }

    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPreviousMediaItem()

    fun jumpToQueue(index: Int) {
        controller?.seekTo(index, 0L)
        controller?.play()
    }

    fun removeFromQueue(index: Int) {
        controller?.removeMediaItem(index)
        syncPlayerState()
    }

    fun downloadCurrent(): Pair<String, String>? {
        val d = _player.value.detail ?: return null
        return AppGraph.api.downloadUrl(d.id) to d.title
    }

    fun expandPlayer(show: Boolean) {
        showPlayer.value = show
    }

    override fun onCleared() {
        ticker?.cancel()
        controller?.release()
        controller = null
    }
}
