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
class Chat(private val db: Db, private val friends: Friends, private val hub: Hub, private val groupPictures: PictureFolder) {
    /** Who is listening together in a chat (set once listen-together is running). */
    var listenersOf: (Long) -> List<Long> = { emptyList() }
    var listenOwnerOf: (Long) -> Long? = { null }

    /** Called when someone leaves a group, and when a group is deleted (to end listen-together there). */
    var onLeft: suspend (userId: Long, conversationId: Long) -> Unit = { _, _ -> }
    var onRemoved: suspend (conversationId: Long, members: List<Long>) -> Unit = { _, _ -> }

    /** Called with the message and the members who don't have the app on screen (for push notifications). */
    var onUnseen: suspend (MessageDto, ConversationDto, List<Long>) -> Unit = { _, _, _ -> }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun conversations(userId: Long): List<ConversationDto> = db.tx {
        val ids = query("SELECT conversation_id FROM conversation_members WHERE user_id = ? AND hidden = 0", userId) { it.getLong(1) }
        ids.map { conversation(it, userId) }
            .sortedByDescending { it.lastMessage?.createdAt ?: 0 }
    }

    /** Opens the DM with a friend, creating it the first time. */
    suspend fun openDm(me: UserDto, otherId: Long): ConversationDto {
        if (!friends.areFriends(me.id, otherId)) throw ApiError(HttpStatusCode.Forbidden, "You can only message friends")
        return db.tx {
            val key = listOf(me.id, otherId).sorted().joinToString(":")
            val existing = queryOne("SELECT id FROM conversations WHERE dm_key = ?", key) { it.getLong(1) }
            // Opening a DM you deleted earlier brings it back (its old history stays cleared).
            existing?.let { update("UPDATE conversation_members SET hidden = 0 WHERE conversation_id = ? AND user_id = ?", it, me.id) }
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
            "$MESSAGE_SELECT WHERE m.conversation_id = ? AND m.id < ? AND m.id > ? ORDER BY m.id DESC LIMIT ?",
            conversationId, before ?: Long.MAX_VALUE, clearedId(conversationId, userId), limit.coerceIn(1, 100),
        ) { it.toMessage() }.reversed()
    }

    /** A listener asks the session's owner for a song. It shows in the chat with Accept/Decline for the owner. */
    suspend fun requestSong(me: UserDto, conversationId: Long, song: SongRef): MessageDto =
        send(me, conversationId, SendMessageRequest(song = song), songRequest = true)

    /** The owner accepts or declines a pending request; everyone in the chat sees the answer. */
    suspend fun answerRequest(conversationId: Long, messageId: Long, accept: Boolean): MessageDto {
        val (message, members) = db.tx {
            val changed = update(
                "UPDATE messages SET request = ? WHERE id = ? AND conversation_id = ? AND request = 'pending'",
                if (accept) "accepted" else "declined", messageId, conversationId,
            )
            if (changed == 0) throw ApiError(HttpStatusCode.Conflict, "That request was already answered")
            queryOne("$MESSAGE_SELECT WHERE m.id = ?", messageId) { it.toMessage() }!! to memberIds(conversationId)
        }
        hub.send(members, MessageUpdatedEvent(message))
        return message
    }

    /** A listening session ended: its unanswered requests can't be played anymore. */
    suspend fun expireRequests(conversationId: Long) {
        val (expired, members) = db.tx {
            val ids = query("SELECT id FROM messages WHERE conversation_id = ? AND request = 'pending'", conversationId) { it.getLong(1) }
            if (ids.isEmpty()) return@tx emptyList<MessageDto>() to emptyList()
            update("UPDATE messages SET request = 'expired' WHERE conversation_id = ? AND request = 'pending'", conversationId)
            ids.mapNotNull { id -> queryOne("$MESSAGE_SELECT WHERE m.id = ?", id) { it.toMessage() } } to memberIds(conversationId)
        }
        expired.forEach { hub.send(members, MessageUpdatedEvent(it)) }
    }

    suspend fun send(me: UserDto, conversationId: Long, request: SendMessageRequest, songRequest: Boolean = false): MessageDto {
        val body = request.body.trim()
        if (body.isEmpty() && request.song == null) throw ApiError(HttpStatusCode.BadRequest, "Message is empty")
        if (body.length > 4000) throw ApiError(HttpStatusCode.BadRequest, "Message is too long")
        request.song?.let(::checkClip)
        val (message, members, conversation) = db.tx {
            requireMember(conversationId, me.id)
            // A DM only works while you're still friends (and they still have an account).
            val dmPartner = queryOne(
                """SELECT cm.user_id FROM conversations c JOIN conversation_members cm ON cm.conversation_id = c.id
                   WHERE c.id = ? AND c.kind = 'dm' AND cm.user_id != ?""",
                conversationId, me.id,
            ) { it.getLong(1) }
            if (dmPartner != null && queryOne("SELECT 1 FROM friendships WHERE user_id = ? AND friend_id = ?", me.id, dmPartner) { true } == null) {
                throw ApiError(HttpStatusCode.Forbidden, "You can't message this person anymore")
            }
            val songJson = request.song?.let { json.encodeToString(SongRef.serializer(), it) }
            val id = insert(
                "INSERT INTO messages (conversation_id, sender_id, body, song_json, created_at, request) VALUES (?, ?, ?, ?, ?, ?)",
                conversationId, me.id, body, songJson, now(), if (songRequest) "pending" else null,
            )
            // A new message brings the chat back for anyone who had deleted it.
            update("UPDATE conversation_members SET hidden = 0 WHERE conversation_id = ?", conversationId)
            // Your own message counts as read.
            update("UPDATE conversation_members SET last_read_id = ? WHERE conversation_id = ? AND user_id = ?", id, conversationId, me.id)
            val message = queryOne("$MESSAGE_SELECT WHERE m.id = ?", id) { it.toMessage() }!!
            Triple(message, memberIds(conversationId), conversation(conversationId, me.id))
        }
        hub.send(members, MessageEvent(message))
        onUnseen(message, conversation, members.filter { it != me.id && !hub.isVisible(it) })
        return message
    }

    suspend fun markRead(userId: Long, conversationId: Long, messageId: Long) = db.tx {
        requireMember(conversationId, userId)
        update(
            "UPDATE conversation_members SET last_read_id = max(last_read_id, ?) WHERE conversation_id = ? AND user_id = ?",
            messageId, conversationId, userId,
        )
    }

    /**
     * Deletes a chat you can't message in anymore (the other person left or is no longer your friend,
     * or everyone else left the group). It disappears for you only; once nobody who's still around
     * has it, it's removed for good.
     */
    suspend fun delete(userId: Long, conversationId: Long) = db.tx {
        requireMember(conversationId, userId)
        if (conversation(conversationId, userId).canMessage) {
            throw ApiError(HttpStatusCode.BadRequest, "You can only delete chats you can't message in anymore")
        }
        val lastId = queryOne("SELECT max(id) FROM messages WHERE conversation_id = ?", conversationId) { it.getLong(1) } ?: 0
        update(
            "UPDATE conversation_members SET hidden = 1, cleared_id = ?, last_read_id = max(last_read_id, ?) WHERE conversation_id = ? AND user_id = ?",
            lastId, lastId, conversationId, userId,
        )
        val stillShown = queryOne(
            """SELECT count(*) FROM conversation_members cm JOIN users u ON u.id = cm.user_id
               WHERE cm.conversation_id = ? AND cm.hidden = 0 AND u.deleted_at IS NULL""",
            conversationId,
        ) { it.getInt(1) } ?: 0
        if (stillShown == 0) update("DELETE FROM conversations WHERE id = ?", conversationId)
    }

    /**
     * Leaves a group. The others see "… left the group". If the owner leaves, the longest-standing
     * member becomes the owner; when the last person leaves, the group is deleted.
     */
    suspend fun leave(me: UserDto, conversationId: Long) {
        val (message, members) = db.tx {
            requireMember(conversationId, me.id)
            requireGroup(conversationId, "You can only leave group chats")
            update("DELETE FROM conversation_members WHERE conversation_id = ? AND user_id = ?", conversationId, me.id)
            val remaining = memberIds(conversationId)
            if (remaining.isEmpty()) {
                update("DELETE FROM conversations WHERE id = ?", conversationId)
                return@tx null to emptyList()
            }
            update(
                """UPDATE conversations SET created_by = (SELECT user_id FROM conversation_members WHERE conversation_id = ? ORDER BY rowid LIMIT 1)
                   WHERE id = ? AND created_by = ?""",
                conversationId, conversationId, me.id,
            )
            val id = insert(
                "INSERT INTO messages (conversation_id, sender_id, body, created_at, system) VALUES (?, ?, 'left the group', ?, 1)",
                conversationId, me.id, now(),
            )
            queryOne("$MESSAGE_SELECT WHERE m.id = ?", id) { it.toMessage() } to remaining
        }
        onLeft(me.id, conversationId)
        message?.let { hub.send(members, MessageEvent(it)) }
    }

    /**
     * Sets (or with null, removes) a group's photo. Anyone in the group can, like in WhatsApp; the chat
     * shows who did it.
     */
    suspend fun setGroupPicture(me: UserDto, conversationId: Long, picture: Picture?): ConversationDto {
        db.tx {
            requireMember(conversationId, me.id)
            requireGroup(conversationId, "Only group chats have a photo")
        }
        if (picture == null) groupPictures.remove(conversationId) else groupPictures.save(conversationId, picture)
        val (message, members, conversation) = db.tx {
            update("UPDATE conversations SET picture_at = ? WHERE id = ?", picture?.let { now() }, conversationId)
            val id = insert(
                "INSERT INTO messages (conversation_id, sender_id, body, created_at, system) VALUES (?, ?, ?, ?, 1)",
                conversationId, me.id, if (picture == null) "removed the group photo" else "changed the group photo", now(),
            )
            Triple(queryOne("$MESSAGE_SELECT WHERE m.id = ?", id) { it.toMessage() }!!, memberIds(conversationId), conversation(conversationId, me.id))
        }
        hub.send(members, MessageEvent(message))
        return conversation
    }

    /** A group's photo file, for its members only; null if it has none. */
    suspend fun groupPicture(userId: Long, conversationId: Long): java.io.File? {
        db.tx { requireMember(conversationId, userId) }
        return groupPictures.get(conversationId)
    }

    /** Deletes a group and all its messages for every member. Only its owner can. */
    suspend fun deleteForEveryone(me: UserDto, conversationId: Long) {
        val members = db.tx {
            requireMember(conversationId, me.id)
            requireGroup(conversationId, "Only group chats can be deleted for everyone")
            val owner = queryOne("SELECT created_by FROM conversations WHERE id = ?", conversationId) { it.getLong(1) }
            if (owner != me.id) throw ApiError(HttpStatusCode.Forbidden, "Only the group's owner can delete it for everyone")
            memberIds(conversationId).also { update("DELETE FROM conversations WHERE id = ?", conversationId) }
        }
        onRemoved(conversationId, members)
        hub.send(members, ConversationRemovedEvent(conversationId))
    }

    private fun Connection.requireGroup(conversationId: Long, error: String) {
        val kind = queryOne("SELECT kind FROM conversations WHERE id = ?", conversationId) { it.getString(1) }
        if (kind != "group") throw ApiError(HttpStatusCode.BadRequest, error)
    }

    private fun checkClip(song: SongRef) {
        if (song.clipStartMs == null && song.clipEndMs == null) return
        val start = song.clipStartMs ?: -1
        val end = song.clipEndMs ?: -1
        val fits = start >= 0 && end - start >= 1_000 && (song.duration <= 0 || end <= song.duration * 1000L + 1_000)
        if (!fits) throw ApiError(HttpStatusCode.BadRequest, "Pick a part of the song at least a second long")
    }

    private fun Connection.clearedId(conversationId: Long, userId: Long) =
        queryOne("SELECT cleared_id FROM conversation_members WHERE conversation_id = ? AND user_id = ?", conversationId, userId) { it.getLong(1) } ?: 0

    private fun Connection.conversation(id: Long, viewerId: Long): ConversationDto {
        val (kind, name, owner) = queryOne("SELECT kind, name, created_by FROM conversations WHERE id = ?", id) {
            Triple(it.getString(1), it.getString(2), it.getLong(3))
        }!!
        val picture = queryOne("SELECT picture_at FROM conversations WHERE id = ?", id) { rs -> rs.getObject(1)?.let { (it as Number).toLong() } }
        val members = query(
            "SELECT u.* FROM conversation_members cm JOIN users u ON u.id = cm.user_id WHERE cm.conversation_id = ?", id,
        ) { it.toUser() }
        val cleared = clearedId(id, viewerId)
        val last = queryOne("$MESSAGE_SELECT WHERE m.conversation_id = ? AND m.id > ? ORDER BY m.id DESC LIMIT 1", id, cleared) { it.toMessage() }
        val unread = queryOne(
            """SELECT count(*) FROM messages m JOIN conversation_members cm
               ON cm.conversation_id = m.conversation_id AND cm.user_id = ?
               WHERE m.conversation_id = ? AND m.id > cm.last_read_id AND m.sender_id != ?""",
            viewerId, id, viewerId,
        ) { it.getInt(1) } ?: 0
        val others = members.filter { it.id != viewerId }
        val canMessage = if (kind == "dm") {
            others.any { queryOne("SELECT 1 FROM friendships WHERE user_id = ? AND friend_id = ?", viewerId, it.id) { true } != null }
        } else {
            others.isNotEmpty()
        }
        return ConversationDto(id, kind, name, members, last, unread, canMessage, listenersOf(id), listenOwnerOf(id), createdBy = owner, picture = picture)
    }

    /** Who is in a chat (for listen-together). */
    suspend fun members(conversationId: Long): List<Long> = db.tx { memberIds(conversationId) }

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
        system = getInt("system") == 1,
        request = getString("request"),
    )

    private companion object {
        const val MESSAGE_SELECT = """SELECT m.*, u.id AS sender_id, u.username AS sender_username, u.display_name AS sender_display_name, u.avatar_at AS sender_avatar_at
            FROM messages m JOIN users u ON u.id = m.sender_id"""
    }
}
