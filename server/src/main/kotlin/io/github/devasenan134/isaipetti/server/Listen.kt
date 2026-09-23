package io.github.devasenan134.isaipetti.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a listen-together session is playing: a shared queue, which song in it, where in the song
 * (at [updatedAt], server time) and whether it's playing. [queue] is left out of updates that
 * don't change it; [queueId] says which queue [index] refers to.
 */
@Serializable
data class ListenState(
    val queue: List<SongRef>? = null,
    val queueId: String,
    val index: Int,
    val positionMs: Long,
    val playing: Boolean,
    val updatedAt: Long = 0,
)

// Listen-together events from the app.
@Serializable @SerialName("listenStart")
data class ListenStart(val conversationId: Long, val state: ListenState) : ClientEvent

@Serializable @SerialName("listenJoin")
data class ListenJoin(val conversationId: Long) : ClientEvent

@Serializable @SerialName("listenLeave")
data class ListenLeave(val conversationId: Long) : ClientEvent

@Serializable @SerialName("listenUpdate")
data class ListenUpdate(val conversationId: Long, val state: ListenState) : ClientEvent

// ...and to the app.

/** Who is listening together in a chat. Sent to every member; an empty list means the session ended. */
@Serializable @SerialName("listenSession")
data class ListenSessionEvent(val conversationId: Long, val listeners: List<Long>) : Event

/** The session's playback changed (by [by]), or you just joined. [serverTime] lets the app work out where the song is now. */
@Serializable @SerialName("listenState")
data class ListenStateEvent(val conversationId: Long, val state: ListenState, val by: Long, val serverTime: Long) : Event

/**
 * Listen together: members of a chat play the same music in sync, and anyone in the session can
 * play, pause, skip, seek or change the queue for everyone. Sessions live in memory only; a
 * session ends when its last listener leaves or goes offline.
 */
class ListenTogether(private val hub: Hub, private val membersOf: suspend (Long) -> List<Long>) {
    private class Session(var state: ListenState, val listeners: MutableSet<Long>)

    private val sessions = mutableMapOf<Long, Session>() // guarded by synchronized(sessions)

    fun listeners(conversationId: Long): List<Long> = synchronized(sessions) { sessions[conversationId]?.listeners?.toList().orEmpty() }

    suspend fun handle(userId: Long, event: ClientEvent) {
        when (event) {
            is ListenStart -> join(userId, event.conversationId, startWith = event.state)
            is ListenJoin -> join(userId, event.conversationId, startWith = null)
            is ListenLeave -> leave(userId, event.conversationId)
            is ListenUpdate -> update(userId, event.conversationId, event.state)
            else -> Unit
        }
    }

    /** Joins a session, starting it with [startWith] if there isn't one yet. You can be in one session at a time. */
    private suspend fun join(userId: Long, conversationId: Long, startWith: ListenState?) {
        if (userId !in membersOf(conversationId) || (startWith?.queue?.size ?: 0) > MAX_QUEUE) return
        leaveAll(userId, except = conversationId)
        val state = synchronized(sessions) {
            val session = sessions[conversationId]
                ?: startWith?.let { Session(it.copy(queue = it.queue.orEmpty(), updatedAt = now()), mutableSetOf()) }?.also { sessions[conversationId] = it }
                ?: return
            session.listeners += userId
            session.state
        }
        hub.send(listOf(userId), ListenStateEvent(conversationId, state, by = userId, serverTime = now()))
        announce(conversationId)
    }

    private suspend fun leave(userId: Long, conversationId: Long) {
        val changed = synchronized(sessions) {
            val session = sessions[conversationId] ?: return
            val removed = session.listeners.remove(userId)
            if (session.listeners.isEmpty()) sessions.remove(conversationId)
            removed
        }
        if (changed) announce(conversationId)
    }

    /** Leaves every session (the user went offline, or joined another one). */
    suspend fun leaveAll(userId: Long, except: Long? = null) {
        val ids = synchronized(sessions) { sessions.filter { (id, s) -> id != except && userId in s.listeners }.keys.toList() }
        ids.forEach { leave(userId, it) }
    }

    private suspend fun update(userId: Long, conversationId: Long, update: ListenState) {
        if ((update.queue?.size ?: 0) > MAX_QUEUE) return
        val (others, state) = synchronized(sessions) {
            val session = sessions[conversationId]?.takeIf { userId in it.listeners } ?: return
            // An update without a queue must refer to the current one.
            val queue = update.queue ?: session.state.queue.takeIf { update.queueId == session.state.queueId } ?: return
            session.state = update.copy(queue = queue, updatedAt = now())
            (session.listeners - userId) to update.copy(updatedAt = session.state.updatedAt)
        }
        hub.send(others, ListenStateEvent(conversationId, state, by = userId, serverTime = now()))
    }

    private companion object {
        const val MAX_QUEUE = 5_000
    }

    private suspend fun announce(conversationId: Long) {
        hub.send(membersOf(conversationId), ListenSessionEvent(conversationId, listeners(conversationId)))
    }
}
