package com.namsan.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.namsan.player.R
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    state: PlayerUi,
    onCollapse: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onJumpTo: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onDownload: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.background,
                        ),
                        endY = 900f,
                    ),
                )
                .padding(horizontal = 20.dp),
        ) {
            // top bar
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.close))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDownload, enabled = state.detail != null) {
                    Icon(Icons.Default.Download,
                        contentDescription = stringResource(R.string.download))
                }
            }

            Spacer(Modifier.height(16.dp))

            // the backend serves no artwork — a spinning vinyl stands in for it
            VinylDisc(playing = state.playing, modifier = Modifier.align(Alignment.CenterHorizontally))

            Spacer(Modifier.height(16.dp))

            Text(
                state.currentTitle ?: "",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().basicMarquee(),
            )

            state.detail?.let { d ->
                val credits = listOfNotNull(
                    d.lyricist?.let { stringResource(R.string.credits_lyricist, it) },
                    d.composer?.let { stringResource(R.string.credits_composer, it) },
                ).joinToString("   ")
                if (credits.isNotEmpty()) {
                    Text(
                        credits,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // seek bar — preview locally while dragging, seek once on release
            var dragFraction by remember { mutableStateOf<Float?>(null) }
            val fraction = dragFraction
                ?: if (state.durationMs > 0)
                    (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                else 0f
            Slider(
                value = fraction,
                onValueChange = { dragFraction = it },
                onValueChangeFinished = {
                    dragFraction?.let { onSeek((it * state.durationMs).toLong()) }
                    dragFraction = null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    formatMs(dragFraction?.let { (it * state.durationMs).toLong() } ?: state.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.weight(1f))
                Text(formatMs(state.durationMs), style = MaterialTheme.typography.labelSmall)
            }

            if (state.playbackError) {
                Text(
                    stringResource(R.string.playback_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }

            // transport controls, flanked by shuffle / repeat
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = stringResource(R.string.shuffle),
                        tint = if (state.shuffleOn) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = onPrevious, enabled = state.queueIndex > 0) {
                    Icon(Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.previous),
                        modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(16.dp))
                FilledIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(64.dp),
                ) {
                    if (state.buffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(
                                if (state.playing) R.string.pause else R.string.play
                            ),
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                IconButton(
                    onClick = onNext,
                    enabled = state.queueIndex >= 0 && state.queueIndex < state.queue.size - 1,
                ) {
                    Icon(Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.next),
                        modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE)
                            Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = stringResource(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE)
                                R.string.repeat_one else R.string.repeat
                        ),
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF)
                            MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            // player hero stays fixed; queue / lyrics live in swipeable tabs below
            val pagerState = rememberPagerState(pageCount = { 2 })
            val pagerScope = rememberCoroutineScope()
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { pagerScope.launch { pagerState.animateScrollToPage(0) } },
                    text = {
                        Text(
                            stringResource(R.string.queue) + " · ${state.queue.size}",
                            maxLines = 1,
                        )
                    },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { pagerScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.lyrics), maxLines = 1) },
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                when (page) {
                    0 -> QueueList(state, onJumpTo, onRemoveAt)
                    else -> LyricsPane(state)
                }
            }
        }
    }
}

@Composable
private fun QueueList(
    state: PlayerUi,
    onJumpTo: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
) {
    if (state.queue.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.queue_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(state.queue, key = { _, e -> e.mediaId }) { i, entry ->
            val current = i == state.queueIndex
            Row(
                Modifier
                    .fillMaxWidth()
                    .alpha(if (i < state.queueIndex) 0.45f else 1f)
                    .clickable { onJumpTo(i) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${i + 1}.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                )
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    color = if (current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemoveAt(i) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete,
                        contentDescription = stringResource(R.string.remove_from_queue),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun LyricsPane(state: PlayerUi) {
    val lyrics = state.detail?.lyrics
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (lyrics.isNullOrBlank()) {
            Text(
                stringResource(R.string.no_lyrics),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            Text(
                lyrics,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

/** Spinning vinyl standing in for album art (the backend serves none). */
@Composable
private fun VinylDisc(playing: Boolean, modifier: Modifier = Modifier) {
    var angle by remember { mutableStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                angle = (angle + (now - last) * 40f / 1_000_000_000f) % 360f
                last = now
            }
        }
    }
    val groove = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val label = MaterialTheme.colorScheme.primary
    val hub = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier.size(180.dp).rotate(angle)) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Color(0xFF1E1E1E), radius = r, center = c)
        for (i in 1..4) {
            drawCircle(groove, radius = r * (0.45f + i * 0.11f), center = c, style = Stroke(2f))
        }
        drawCircle(label, radius = r * 0.30f, center = c)
        drawCircle(hub, radius = r * 0.07f, center = c)
        // off-center marker so the spin is visible
        drawCircle(hub.copy(alpha = 0.9f), radius = r * 0.04f,
            center = Offset(c.x + r * 0.16f, c.y))
    }
}
