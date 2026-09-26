@file:OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.namsan.player.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.namsan.player.AppGraph

/**
 * Foreground media playback service.
 *
 * MediaItems carry a `namsan://song/<id>` URI; [ResolvingDataSource] turns it
 * into the real mp3 URL by fetching the song's `rmenjoy/play` page through
 * [AppGraph.songs] (cached, so repeat plays don't hit the network again).
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val resolvingFactory = ResolvingDataSource.Factory(
            DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
        ) { dataSpec: DataSpec ->
            if (dataSpec.uri.scheme == "namsan") {
                val id = dataSpec.uri.host ?: dataSpec.uri.toString().removePrefix("namsan://")
                val detail = AppGraph.songs.detailBlocking(id)
                dataSpec.withUri(android.net.Uri.parse(detail.mediaUrl))
            } else {
                dataSpec
            }
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.let {
            it.player.release()
            it.release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
