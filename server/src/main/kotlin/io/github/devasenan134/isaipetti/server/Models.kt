package io.github.devasenan134.isaipetti.server

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import java.sql.ResultSet

/** Thrown anywhere to send the app an error with a message it can show. */
class ApiError(val status: HttpStatusCode, override val message: String) : Exception(message)

@Serializable
data class UserDto(
    val id: Long,
    val username: String,
    val displayName: String,
    /** When their profile picture was last set (a version for GET /users/{id}/avatar), or null for none. */
    val avatar: Long? = null,
)

fun ResultSet.toUser(prefix: String = "") = UserDto(
    id = getLong("${prefix}id"),
    username = getString("${prefix}username"),
    displayName = getString("${prefix}display_name"),
    // Not every query selects it.
    avatar = runCatching { getObject("${prefix}avatar_at")?.let { (it as Number).toLong() } }.getOrNull(),
)

/**
 * A song as shared between friends. Ids are the same for every Navidrome user, so anyone can play it.
 * A shared clip also has [clipStartMs] and [clipEndMs]: only that part of the song is meant to be heard.
 */
@Serializable
data class SongRef(
    val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
    val clipStartMs: Long? = null,
    val clipEndMs: Long? = null,
) {
    val isClip get() = clipStartMs != null && clipEndMs != null
}

/** "1:05" */
fun clockTime(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

// Requests and responses

@Serializable data class LoginRequest(val username: String, val salt: String, val token: String)
@Serializable data class SignupRequest(val inviteCode: String, val username: String, val password: String, val displayName: String? = null)
@Serializable data class SessionResponse(val sessionToken: String, val user: UserDto)

@Serializable data class InviteDto(val code: String, val expiresAt: Long, val usedBy: UserDto? = null)

@Serializable data class FriendDto(val user: UserDto, val online: Boolean, val nowPlaying: SongRef? = null)
@Serializable data class FriendRequestsDto(val incoming: List<UserDto>, val outgoing: List<UserDto>)
@Serializable data class AddFriendRequest(val username: String)
@Serializable data class AddFriendResponse(val status: String) // "requested" or "friends"

@Serializable
data class MessageDto(
    val id: Long,
    val conversationId: Long,
    val sender: UserDto,
    val body: String,
    val song: SongRef? = null,
    val createdAt: Long,
    /** A line about the chat itself ("left the group"), from [sender]. */
    val system: Boolean = false,
    /** For a song request in a listening session: "pending", "accepted", "declined" or "expired" (the session ended first). */
    val request: String? = null,
    /** What a song request asks for: "next" (after the current song) or "now" (skip to it). */
    val requestMode: String? = null,
)

@Serializable
data class ConversationDto(
    val id: Long,
    val kind: String, // "dm" or "group"
    val name: String?,
    val members: List<UserDto>,
    val lastMessage: MessageDto? = null,
    val unread: Int = 0,
    /** False for a DM with someone who left or is no longer a friend, or a group everyone else left. Such chats can be deleted. */
    val canMessage: Boolean = true,
    /** Who is listening together in this chat right now (empty if nobody). */
    val listeners: List<Long> = emptyList(),
    /** Who started (and controls) the listening session, if there is one. */
    val listenOwner: Long? = null,
    /** The group's owner: the only one who can delete it for everyone. */
    val createdBy: Long? = null,
    /** When the group's photo was set (a version for GET /conversations/{id}/picture), or null for none. */
    val picture: Long? = null,
)

@Serializable data class NewDmRequest(val userId: Long)
@Serializable data class NewGroupRequest(val name: String, val memberIds: List<Long>)
@Serializable data class AddMembersRequest(val userIds: List<Long>)
@Serializable data class SendMessageRequest(val body: String = "", val song: SongRef? = null)
@Serializable data class MarkReadRequest(val messageId: Long)
@Serializable data class DeviceRequest(val token: String)
@Serializable data class RenameRequest(val displayName: String)
@Serializable data class ErrorResponse(val error: String)
