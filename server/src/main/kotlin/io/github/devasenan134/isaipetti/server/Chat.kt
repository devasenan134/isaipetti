package io.github.devasenan134.isaipetti.server

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.ResultSet

/**
 * Conversations are either a DM between two friends or a named group of friends.
 * A message is text, a shared song, or both. Messages are saved, so history is there
 * when you open the app; people who are online also get them instantly through the [Hub].
 */
class Chat(private val db: Db, private val friends: Friends, private val hub: Hub) {
    /** Called with the message and the members who were offline, so they can get a push notification. */
    var onOfflineRecipients: suspend (MessageDto, List<Long>) -> Unit = { _, _ -> }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun conversations(userId: Long): List<ConversationDto> = db.tx {
        val ids = query("SELECT conversation_id FROM conversation_members WHERE user_id = ?", userId) { it.getLong(1) }
        ids.map { conversation(it, userId) }
            .sortedByDescending { it.lastMessage?.createdAt ?: 0 }
    }

    /** Opens the DM with a friend, creating it the first time. */
    suspend fun openDm(me: UserDto, otherId: Long): ConversationDto {
        if (!friends.areFriends(me.id, otherId)) throw ApiError(HttpStatusCode.Forbidden, "You can only message friends")
        return db.tx {
            val key = listOf(me.id, otherId).sorted().joinToString(":")
            val existing = queryOne("SELECT id FROM conversations WHERE dm_key = ?", key) { it.getLong(1) }
            val id = existing ?: insert(
                "INSERT INTO conversations (kind, dm_key, created_by, created_at) VALUES ('dm', ?, ?, ?)", key, me.id, now(),
            ).also { id ->
                update("INSERT INTO conversation_members (conversation_id, user_id) VALUES (?, ?), (?, ?)", id, me.id, id, otherId)
            }
            conversation(id, me.id)
        }
    }

    suspend fun createGroup(me: UserDto, request: NewGroupRequest): ConversationDto {
        val name = request.name.trim()
        if (name.isEmpty() || name.length > 50) throw ApiError(HttpStatusCode.BadRequest, "Give the group a name (up to 50 characters)")
        val memberIds = (request.memberIds.toSet() - me.id)
        if (memberIds.isEmpty()) throw ApiError(HttpStatusCode.BadRequest, "Add at least one friend")
        val myFriends = friends.friendIds(me.id).toSet()
        if (!myFriends.containsAll(memberIds)) throw ApiError(HttpStatusCode.Forbidden, "You can only add friends to a group")
        return db.tx {
            val id = insert("INSERT INTO conversations (kind, name, created_by, created_at) VALUES ('group', ?, ?, ?)", name, me.id, now())
            (memberIds + me.id).forEach { update("INSERT INTO conversation_members (conversation_id, user_id) VALUES (?, ?)", id, it) }
            conversation(id, me.id)
        }
    }

    /** Up to [limit] messages older than [before] (or the newest ones), oldest first. */
    suspend fun messages(userId: Long, conversationId: Long, before: Long?, limit: Int): List<MessageDto> = db.tx {
        requireMember(conversationId, userId)
        query(
            "$MESSAGE_SELECT WHERE m.conversation_id = ? AND m.id < ? ORDER BY m.id DESC LIMIT ?",
            conversationId, before ?: Long.MAX_VALUE, limit.coerceIn(1, 100),
        ) { it.toMessage() }.reversed()
    }

    suspend fun send(me: UserDto, conversationId: Long, request: SendMessageRequest): MessageDto {
        val body = request.body.trim()
        if (body.isEmpty() && request.song == null) throw ApiError(HttpStatusCode.BadRequest, "Message is empty")
        if (body.length > 4000) throw ApiError(HttpStatusCode.BadRequest, "Message is too long")
        val (message, members) = db.tx {
            requireMember(conversationId, me.id)
            val songJson = request.song?.let { json.encodeToString(SongRef.serializer(), it) }
            val id = insert(
                "INSERT INTO messages (conversation_id, sender_id, body, song_json, created_at) VALUES (?, ?, ?, ?, ?)",
                conversationId, me.id, body, songJson, now(),
            )
            // Your own message counts as read.
            update("UPDATE conversation_members SET last_read_id = ? WHERE conversation_id = ? AND user_id = ?", id, conversationId, me.id)
            val message = queryOne("$MESSAGE_SELECT WHERE m.id = ?", id) { it.toMessage() }!!
            message to memberIds(conversationId)
        }
        val offline = hub.send(members, MessageEvent(message))
        onOfflineRecipients(message, offline - me.id)
        return message
    }

    suspend fun markRead(userId: Long, conversationId: Long, messageId: Long) = db.tx {
        requireMember(conversationId, userId)
        update(
            "UPDATE conversation_members SET last_read_id = max(last_read_id, ?) WHERE conversation_id = ? AND user_id = ?",
            messageId, conversationId, userId,
        )
    }

    private fun Connection.conversation(id: Long, viewerId: Long): ConversationDto {
        val (kind, name) = queryOne("SELECT kind, name FROM conversations WHERE id = ?", id) { it.getString(1) to it.getString(2) }!!
        val members = query(
            "SELECT u.* FROM conversation_members cm JOIN users u ON u.id = cm.user_id WHERE cm.conversation_id = ?", id,
        ) { it.toUser() }
        val last = queryOne("$MESSAGE_SELECT WHERE m.conversation_id = ? ORDER BY m.id DESC LIMIT 1", id) { it.toMessage() }
        val unread = queryOne(
            """SELECT count(*) FROM messages m JOIN conversation_members cm
               ON cm.conversation_id = m.conversation_id AND cm.user_id = ?
               WHERE m.conversation_id = ? AND m.id > cm.last_read_id AND m.sender_id != ?""",
            viewerId, id, viewerId,
        ) { it.getInt(1) } ?: 0
        return ConversationDto(id, kind, name, members, last, unread)
    }

    private fun Connection.memberIds(conversationId: Long) =
        query("SELECT user_id FROM conversation_members WHERE conversation_id = ?", conversationId) { it.getLong(1) }

    private fun Connection.requireMember(conversationId: Long, userId: Long) {
        queryOne("SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ?", conversationId, userId) { true }
            ?: throw ApiError(HttpStatusCode.NotFound, "Conversation not found")
    }

    private fun ResultSet.toMessage() = MessageDto(
        id = getLong("id"),
        conversationId = getLong("conversation_id"),
        sender = toUser(prefix = "sender_"),
        body = getString("body"),
        song = getString("song_json")?.let { json.decodeFromString(SongRef.serializer(), it) },
        createdAt = getLong("created_at"),
    )

    private companion object {
        const val MESSAGE_SELECT = """SELECT m.*, u.id AS sender_id, u.username AS sender_username, u.display_name AS sender_display_name
            FROM messages m JOIN users u ON u.id = m.sender_id"""
    }
}
