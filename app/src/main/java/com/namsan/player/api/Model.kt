package com.namsan.player.api

/** One row of a rmenjoy list response: either a song or a catalogue (folder). */
sealed interface BrowseItem {
    val id: String
    val title: String
}

data class Category(
    override val id: String,
    override val title: String,
    val songCount: Int,
) : BrowseItem

data class Song(
    override val id: String,
    override val title: String,
    /** Track length in milliseconds, or 0 when unknown. */
    val durationMs: Long = 0,
) : BrowseItem

/** Fully resolved playback information for a single song (from rmenjoy/play). */
data class SongDetail(
    val id: String,
    val title: String,
    /** Absolute URL of the media file (mp3/mp4). */
    val mediaUrl: String,
    val isVideo: Boolean,
    val lyricist: String?,
    val composer: String?,
    /** Plain-text lyrics, one line per source <br/>, or null for instrumentals. */
    val lyrics: String?,
)
