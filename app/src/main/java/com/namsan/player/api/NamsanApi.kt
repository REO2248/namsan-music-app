package com.namsan.player.api

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Client for the 인민대학습당 《남산》 rmenjoy backend (www.gpsh.edu.kp).
 *
 * All endpoints live under `{base}/index.php/{lang}/` and are unauthenticated.
 * The site serves plain HTTP only.
 */
class NamsanApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val lang: String = "ko",
    private val client: OkHttpClient = defaultClient(),
) {
    companion object {
        const val DEFAULT_BASE_URL = "http://www.gpsh.edu.kp"

        /** The site rejects these characters in search input (see validateSearchText). */
        private val prohibitedChars = Regex("""["'<>/()+$%:;?@&*#\\]""")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val siteUrl: String get() = "$baseUrl/index.php/$lang/"

    /** `POST rmenjoy/loadsongs` — empty catcode returns the root categories. */
    suspend fun browse(catId: String = ""): List<BrowseItem> = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("catcode", catId).build()
        val xml = post("${siteUrl}rmenjoy/loadsongs", body)
        RmenjoyParser.parseItems(xml)
    }

    /** `POST rmenjoy/searchsong` — empty query returns every song. */
    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        val cleaned = query.replace(prohibitedChars, "").trim()
        val body = FormBody.Builder().add("search", cleaned).build()
        val xml = post("${siteUrl}rmenjoy/searchsong", body)
        RmenjoyParser.parseItems(xml).filterIsInstance<Song>()
    }

    /** New songs exist only inside the `rmenjoy/room` HTML, not as an API. */
    suspend fun newSongs(): List<Song> = withContext(Dispatchers.IO) {
        RmenjoyParser.parseNewSongs(get("${siteUrl}rmenjoy/room"))
    }

    /** `GET rmenjoy/play?id=` — resolves the real media URL, credits and lyrics. */
    suspend fun songDetail(id: String): SongDetail = withContext(Dispatchers.IO) {
        RmenjoyParser.parseSongDetail(get(playPageUrl(id)), id, baseUrl)
    }

    fun playPageUrl(id: String) = "${siteUrl}rmenjoy/play?id=$id"

    fun downloadUrl(id: String) = "${siteUrl}rmenjoy/download?id=$id"

    private fun get(url: String): String =
        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException(resp.code)
            resp.body?.string() ?: throw ApiException(resp.code)
        }

    private fun post(url: String, body: FormBody): String =
        client.newCall(Request.Builder().url(url).post(body).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException(resp.code)
            resp.body?.string() ?: throw ApiException(resp.code)
        }

    class ApiException(val code: Int) : Exception("HTTP $code")
}
