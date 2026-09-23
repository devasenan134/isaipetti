package io.github.devasenan134.isaipetti.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.devasenan134.isaipetti.IsaipettiApp
import io.github.devasenan134.isaipetti.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays music in the background. Android keeps this service alive while music plays,
 * and Media3 shows the notification and lock-screen controls for it.
 * The UI never touches the player directly; it sends commands through [PlayerConnection].
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = MainScope()
    private val api get() = (application as IsaipettiApp).api

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true, // pause for phone calls and other apps
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK) // keep streaming with the screen off
            .build()

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()

        startScrobbling(player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        // Items sent from the UI arrive without their stream URL, so rebuild it from the song id.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.map { it.buildUpon().setUri(api.streamUrl(it.mediaId)).build() }.toMutableList()
        )
    }

    /**
     * Reports plays to Navidrome: "now playing" when a song starts, then a real play
     * after half the song (or 4 minutes). This is the listening history the ML playlists will use.
     */
    private fun startScrobbling(player: ExoPlayer) {
        var submittedId: String? = null
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                submittedId = null
                val id = mediaItem?.mediaId ?: return
                scope.launch { runCatching { api.scrobble(id, submission = false) } }
            }
        })
        scope.launch {
            while (isActive) {
                delay(5_000)
                val id = player.currentMediaItem?.mediaId ?: continue
                val duration = player.duration
                if (id == submittedId || duration <= 0) continue
                if (player.currentPosition >= minOf(duration / 2, 240_000L)) {
                    submittedId = id
                    launch { runCatching { api.scrobble(id, submission = true) } }
                }
            }
        }
    }
}
