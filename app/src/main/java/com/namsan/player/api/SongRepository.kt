package com.namsan.player.api

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking

/** Caches [SongDetail]s so the UI and the ExoPlayer resolver share one fetch. */
class SongRepository(private val api: NamsanApi) {

    private val detailCache = ConcurrentHashMap<String, SongDetail>()

    suspend fun detail(id: String): SongDetail =
        detailCache[id] ?: api.songDetail(id).also { detailCache[id] = it }

    /** Called from ExoPlayer's loader thread via ResolvingDataSource. */
    fun detailBlocking(id: String): SongDetail = runBlocking { detail(id) }

    fun cached(id: String): SongDetail? = detailCache[id]
}
