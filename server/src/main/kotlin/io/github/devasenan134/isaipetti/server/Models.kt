package io.github.devasenan134.isaipetti.server

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import java.sql.ResultSet

/** Thrown anywhere to send the app an error with a message it can show. */
class ApiError(val status: HttpStatusCode, override val message: String) : Exception(message)

@Serializable
data class UserDto(val id: Long, val username: String, val displayName: String)

fun ResultSet.toUser(prefix: String = "") = UserDto(
    id = getLong("${prefix}id"),
    username = getString("${prefix}username"),
    displayName = getString("${prefix}display_name"),
)

/** A song as shared between friends. Ids are the same for every Navidrome user, so anyone can play it. */
@Serializable
data class SongRef(
    val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
)

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
)

@Serializable
data class ConversationDto(
    val id: Long,
    val kind: String, // "dm" or "group"
    val name: String?,
    val members: List<UserDto>,
    val lastMessage: MessageDto? = null,
    val unread: Int = 0,
)

@Serializable data class NewDmRequest(val userId: Long)
@Serializable data class NewGroupRequest(val name: String, val memberIds: List<Long>)
@Serializable data class SendMessageRequest(val body: String = "", val song: SongRef? = null)
@Serializable data class MarkReadRequest(val messageId: Long)
@Serializable data class DeviceRequest(val token: String)
@Serializable data class RenameRequest(val displayName: String)
@Serializable data class ErrorResponse(val error: String)
