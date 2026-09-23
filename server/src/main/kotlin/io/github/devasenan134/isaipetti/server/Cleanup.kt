package io.github.devasenan134.isaipetti.server

import org.slf4j.LoggerFactory

/**
 * Removes people whose Navidrome account was deleted. Runs every few minutes.
 *
 * A removed person disappears from friend lists, requests and group chats, and can't log in.
 * Their user row stays (renamed, marked "(left)") so old messages still show who wrote them,
 * and the username becomes free again if someone creates it in Navidrome later.
 */
class Cleanup(private val db: Db, private val navidrome: Navidrome, private val hub: Hub) {
    private val log = LoggerFactory.getLogger("cleanup")

    /** Returns the usernames that were removed. */
    suspend fun run(): List<String> {
        val existing = navidrome.userNames()
        if (existing.isNullOrEmpty()) {
            log.warn("Skipping cleanup: couldn't get the user list from Navidrome")
            return emptyList()
        }
        val ours = db.tx { query("SELECT id, username FROM users WHERE deleted_at IS NULL") { it.getLong(1) to it.getString(2) } }
        val gone = ours.filter { (_, username) -> username.lowercase() !in existing }
        // Safety net: a half-empty list from Navidrome is more likely a glitch than mass deletion.
        if (gone.size > 1 && gone.size * 2 > ours.size) {
            log.warn("Skipping cleanup: it would remove ${gone.size} of ${ours.size} users, which looks wrong")
            return emptyList()
        }
        gone.forEach { (id, username) -> remove(id, username) }
        return gone.map { it.second }
    }

    private suspend fun remove(userId: Long, username: String) {
        val formerFriends = db.tx {
            val friends = query("SELECT friend_id FROM friendships WHERE user_id = ?", userId) { it.getLong(1) }
            update("DELETE FROM sessions WHERE user_id = ?", userId)
            update("DELETE FROM devices WHERE user_id = ?", userId)
            update("DELETE FROM friendships WHERE user_id = ? OR friend_id = ?", userId, userId)
            update("DELETE FROM friend_requests WHERE from_id = ? OR to_id = ?", userId, userId)
            update("DELETE FROM invites WHERE created_by = ? AND used_by IS NULL", userId)
            // Leave group chats; DMs keep them as a member so the chat still has a name.
            update(
                "DELETE FROM conversation_members WHERE user_id = ? AND conversation_id IN (SELECT id FROM conversations WHERE kind = 'group')",
                userId,
            )
            update(
                "UPDATE users SET username = 'deleted:' || id, display_name = display_name || ' (left)', deleted_at = ? WHERE id = ?",
                now(), userId,
            )
            friends
        }
        hub.kick(userId)
        formerFriends.forEach { hub.send(listOf(it), FriendRemovedEvent(userId)) }
        log.info("Removed $username (deleted from Navidrome)")
    }
}
