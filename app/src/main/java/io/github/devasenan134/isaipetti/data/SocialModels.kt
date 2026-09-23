package io.github.devasenan134.isaipetti.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// These mirror the companion server's JSON (server/src/.../Models.kt and Hub.kt).

@Serializable
data class SocialUser(val id: Long, val username: String, val displayName: String)

/** A song as shared between friends. Navidrome ids are the same for everyone, so anyone can play it. */
@Serializable
data class SongRef(
    val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
) {
    fun toSong() = Song(id = id, title = title, album = album, albumId = albumId, artist = artist, duration = duration, coverArt = coverArt)
}

fun Song.toRef() = SongRef(id, title, artist, album, albumId, coverArt, duration)

@Serializable data class SessionResponse(val sessionToken: String, val user: SocialUser)
@Serializable data class Invite(val code: String, val expiresAt: Long, val usedBy: SocialUser? = null)
@Serializable data class Friend(val user: SocialUser, val online: Boolean, val nowPlaying: SongRef? = null)
@Serializable data class FriendRequests(val incoming: List<SocialUser> = emptyList(), val outgoing: List<SocialUser> = emptyList())
@Serializable data class AddFriendResponse(val status: String)

@Serializable
data class ChatMessage(
    val id: Long,
    val conversationId: Long,
    val sender: SocialUser,
    val body: String,
    val song: SongRef? = null,
    val createdAt: Long,
)

@Serializable
data class Conversation(
    val id: Long,
    val kind: String,
    val name: String? = null,
    val members: List<SocialUser>,
    val lastMessage: ChatMessage? = null,
    val unread: Int = 0,
) {
    val isGroup get() = kind == "group"

    /** Group name, or the other person's name for a DM. */
    fun title(me: Long?): String = if (isGroup) name.orEmpty() else members.firstOrNull { it.id != me }?.displayName ?: "Chat"
}

/** Live events from the server's WebSocket. */
@Serializable
sealed interface SocialEvent

@Serializable @SerialName("presence")
data class PresenceEvent(val userId: Long, val online: Boolean, val nowPlaying: SongRef? = null) : SocialEvent

@Serializable @SerialName("message")
data class MessageEvent(val message: ChatMessage) : SocialEvent

@Serializable @SerialName("friendRequest")
data class FriendRequestEvent(val from: SocialUser) : SocialEvent

@Serializable @SerialName("friendAdded")
data class FriendAddedEvent(val friend: Friend) : SocialEvent

@Serializable @SerialName("friendRemoved")
data class FriendRemovedEvent(val userId: Long) : SocialEvent

/** What the app sends over the WebSocket. */
@Serializable
sealed interface ClientEvent

@Serializable @SerialName("nowPlaying")
data class NowPlayingUpdate(val song: SongRef? = null) : ClientEvent

/** Tells the server whether the app is on screen, so it knows when to send push notifications instead. */
@Serializable @SerialName("appState")
data class AppStateUpdate(val visible: Boolean) : ClientEvent
