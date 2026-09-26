package com.namsan.player

import com.namsan.player.api.NamsanApi
import com.namsan.player.api.SongRepository

/** Tiny hand-rolled service locator shared by the UI and the playback service. */
object AppGraph {
    val api: NamsanApi by lazy { NamsanApi() }
    val songs: SongRepository by lazy { SongRepository(api) }
}
