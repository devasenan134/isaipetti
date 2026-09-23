package io.github.devasenan134.isaipetti.playback

import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.devasenan134.isaipetti.data.Song
import io.github.devasenan134.isaipetti.data.SongRef
import io.github.devasenan134.isaipetti.data.SubsonicApi

// Converting between our Song and Media3's MediaItem (what the player queue holds).
// Navidrome ids ride along in "extras" so a queued song can be shared with friends.

const val EXTRA_ALBUM_ID = "albumId"
private const val EXTRA_COVER_ART = "coverArt"

fun Song.toMediaItem(api: SubsonicApi): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(api.streamUrl(id))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(api.coverUrl(coverArt, 600)?.toUri())
            .setDurationMs(duration * 1000L)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(bundleOf(EXTRA_ALBUM_ID to albumId, EXTRA_COVER_ART to coverArt))
            .build()
    )
    .build()

fun MediaItem.toSongRef(): SongRef {
    val meta = mediaMetadata
    return SongRef(
        id = mediaId,
        title = meta.title?.toString().orEmpty(),
        artist = meta.artist?.toString(),
        album = meta.albumTitle?.toString(),
        albumId = meta.extras?.getString(EXTRA_ALBUM_ID),
        coverArt = meta.extras?.getString(EXTRA_COVER_ART),
        duration = ((meta.durationMs ?: 0) / 1000).toInt(),
    )
}
