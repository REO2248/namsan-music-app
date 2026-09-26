package com.namsan.player

import com.namsan.player.api.Category
import com.namsan.player.api.RmenjoyParser
import com.namsan.player.api.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RmenjoyParserTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!
            .bufferedReader().use { it.readText() }

    @Test
    fun `root loadsongs returns the 10 top categories`() {
        val items = RmenjoyParser.parseItems(fixture("loadsongs_root.xml"))
        assertEquals(10, items.size)
        assertTrue(items.all { it is Category })

        val first = items.first() as Category
        assertEquals("A", first.id)
        assertEquals("송가", first.title)
        assertEquals(7, first.songCount)

        val film = items.filterIsInstance<Category>().first { it.id == "FILM" }
        assertEquals("영화가요", film.title)
        assertEquals(18, film.songCount)
    }

    @Test
    fun `catalogue loadsongs returns flat song list`() {
        val items = RmenjoyParser.parseItems(fixture("loadsongs_C.xml"))
        assertEquals(433, items.size)
        assertTrue(items.all { it is Song })

        val first = items.first() as Song
        assertEquals("7", first.id)
        assertEquals("김일성대원수님 만세", first.title)
        assertEquals((2 * 60 + 19) * 1000L, first.durationMs)
    }

    @Test
    fun `search response parses songs only`() {
        val items = RmenjoyParser.parseItems(fixture("search.xml"))
        assertEquals(2, items.size)
        val songs = items.filterIsInstance<Song>()
        assertEquals("2164", songs[0].id)
        assertEquals("감나무마을", songs[0].title)
        assertEquals(3 * 60_000L + 40_000L, songs[0].durationMs)
    }

    @Test
    fun `play page resolves media url credits and lyrics`() {
        val d = RmenjoyParser.parseSongDetail(
            fixture("play2164.html"), "2164", "http://www.gpsh.edu.kp")

        assertEquals("2164", d.id)
        assertEquals("감나무마을", d.title)
        assertEquals(
            "http://www.gpsh.edu.kp/data/rmenjoy/music/People's/Moranbong/2164.mp3",
            d.mediaUrl,
        )
        assertFalse(d.isVideo)
        assertEquals("정성환", d.lyricist)
        assertEquals("우정희", d.composer)
        assertNotNull(d.lyrics)
        assertTrue(d.lyrics!!.contains("감나무마을입니다"))
        assertTrue(d.lyrics!!.lines().size > 5)
    }

    @Test
    fun `instrumental play page has no lyrics or credits`() {
        val d = RmenjoyParser.parseSongDetail(
            fixture("play_2145.html"), "2145", "http://www.gpsh.edu.kp")

        assertEquals("결전의 길로", d.title)
        assertTrue(d.mediaUrl.endsWith(".mp3"))
        assertNull(d.lyricist)
        assertNull(d.composer)
    }

    @Test
    fun `newsongs block parses embedded song rows`() {
        val songs = RmenjoyParser.parseNewSongs(fixture("room_newsongs.html"))
        assertEquals(8, songs.size)
        assertEquals("2164", songs[0].id)
        assertEquals("감나무마을", songs[0].title)
        assertEquals(3 * 60_000L + 40_000L, songs[0].durationMs)
        assertEquals("조국에 대한 노래", songs.last().title)
    }

    @Test
    fun `empty result parses to empty list`() {
        assertTrue(RmenjoyParser.parseItems(
            """<?xml version="1.0" encoding="UTF-8"?><result></result>""").isEmpty())
    }

    @Test
    fun `media url normalization handles all path shapes`() {
        val base = "http://www.gpsh.edu.kp"
        assertEquals(
            "$base/data/rmenjoy/music/A/1.mp3",
            RmenjoyParser.normalizeMediaUrl(base, "/./data/rmenjoy/music/A/1.mp3"))
        assertEquals(
            "$base/data/rmenjoy/music/A/1.mp3",
            RmenjoyParser.normalizeMediaUrl(base, "music/A/1.mp3"))
        assertEquals(
            "$base/data/x.mp3",
            RmenjoyParser.normalizeMediaUrl(base, "/data/x.mp3"))
        assertEquals(
            "http://other/f.mp3",
            RmenjoyParser.normalizeMediaUrl(base, "http://other/f.mp3"))
    }
}
