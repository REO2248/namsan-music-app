package com.namsan.player.api

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/**
 * Pure parsers for the rmenjoy backend responses.
 * JVM-only (Jsoup), so everything here is unit-testable without Android.
 */
object RmenjoyParser {

    private val durationRegex = Regex("""(\d{1,3}):(\d{2})""")
    private val songUrlRegex = Regex("""song_url\s*=\s*"([^"]+)"""")
    private val songTitleRegex = Regex("""song_title\s*=\s*"([^"]+)"""")
    private val brRegex = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)

    /**
     * Parses `<result><item>…</item>…</result>` XML returned by
     * `rmenjoy/loadsongs` and `rmenjoy/searchsong`.
     * `<item>` fields: type(song|catalogue), id, label, duration, songsize.
     */
    fun parseItems(xml: String): List<BrowseItem> {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        return doc.select("result > item").mapNotNull { it.toItem() }
    }

    private fun Element.toItem(): BrowseItem? {
        val id = firstText("id") ?: return null
        val label = firstText("label") ?: return null
        return when (firstText("type")?.lowercase()) {
            "catalogue" -> Category(
                id = id,
                title = label,
                songCount = firstText("songsize")?.trim()?.toIntOrNull() ?: 0,
            )
            else -> Song(
                id = id,
                title = label,
                durationMs = parseDuration(firstText("duration")),
            )
        }
    }

    /**
     * Parses the "새 노래" (new songs) list embedded in the `rmenjoy/room`
     * HTML page: `ul#newsongs li` rows with checkbox value=id,
     * `.rmenjoy_song_title` and `.badge.songtime`.
     */
    fun parseNewSongs(html: String): List<Song> {
        val doc = Jsoup.parse(html)
        return doc.select("ul#newsongs li.rmenjoy_song_item").mapNotNull { li ->
            val id = li.selectFirst("input.check.song")?.attr("value")
                ?: li.selectFirst(".rmenjoy_song_title")?.id()
                ?: return@mapNotNull null
            val title = li.selectFirst(".rmenjoy_song_title")?.text() ?: return@mapNotNull null
            Song(
                id = id,
                title = title,
                durationMs = parseDuration(li.selectFirst(".badge.songtime")?.text()),
            )
        }
    }

    /**
     * Parses the `rmenjoy/play?id=N` player page:
     * media URL (`<source src>` or `song_url`), title, credits and lyrics.
     */
    fun parseSongDetail(html: String, songId: String, baseUrl: String): SongDetail {
        val doc = Jsoup.parse(html)

        val source = doc.selectFirst("audio source, video source")?.attr("src")
        val songUrl = doc.select("script").asSequence()
            .mapNotNull { songUrlRegex.find(it.data())?.groupValues?.get(1) }
            .firstOrNull()
        val mediaPath = (source ?: songUrl)
            ?: throw IllegalStateException("no media source for song $songId")
        val mediaUrl = normalizeMediaUrl(baseUrl, mediaPath)

        val title = doc.selectFirst("h3.song-title")?.text()
            ?: doc.select("script").asSequence()
                .mapNotNull { songTitleRegex.find(it.data())?.groupValues?.get(1) }
                .firstOrNull()
            ?: songId

        var lyricist: String? = null
        var composer: String? = null
        doc.select(".rmenjoy_meta_wraper nobr, .acm-td nobr").forEach { el ->
            val text = el.text().replace(' ', ' ').trim()
            when {
                text.startsWith("작사") -> lyricist = text.substringAfter(':').trim()
                text.startsWith("작곡") -> composer = text.substringAfter(':').trim()
            }
        }

        val lyrics = doc.selectFirst("#songGasa #words")?.let { wordsEl ->
            val withBreaks = brRegex.replace(wordsEl.html(), "\n")
            Jsoup.parse("<p>$withBreaks</p>").body().wholeText()
                .replace(' ', ' ')
                .lines()
                .joinToString("\n") { it.trim() }
                .trim()
                .ifBlank { null }
        }

        val isVideo = doc.selectFirst("video source, .video_frame") != null ||
            mediaPath.endsWith(".mp4", ignoreCase = true)

        return SongDetail(
            id = songId,
            title = title,
            mediaUrl = mediaUrl,
            isVideo = isVideo,
            lyricist = lyricist,
            composer = composer,
            lyrics = lyrics,
        )
    }

    /** Turns `/./data/…` or `music/…` paths into an absolute URL on the site. */
    fun normalizeMediaUrl(baseUrl: String, path: String): String {
        val trimmed = path.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        val clean = trimmed.removePrefix("/./").removePrefix("./")
        return when {
            clean.startsWith("/") -> baseUrl.trimEnd('/') + clean
            clean.startsWith("data/") -> baseUrl.trimEnd('/') + "/" + clean
            else -> baseUrl.trimEnd('/') + "/data/rmenjoy/" + clean
        }
    }

    fun parseDuration(text: String?): Long {
        if (text == null) return 0
        val m = durationRegex.find(text.trim()) ?: return 0
        val min = m.groupValues[1].toLongOrNull() ?: return 0
        val sec = m.groupValues[2].toLongOrNull() ?: return 0
        return (min * 60 + sec) * 1000
    }

    private fun Element.firstText(tag: String): String? =
        selectFirst(tag)?.text()?.takeIf { it.isNotBlank() }
}
