package io.github.devasenan134.isaipetti.server

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/** Live events the server pushes to the app over the WebSocket. The JSON has a "type" field. */
@Serializable
sealed interface Event

@Serializable @SerialName("presence")
data class PresenceEvent(val userId: Long, val online: Boolean, val nowPlaying: SongRef? = null) : Event

@Serializable @SerialName("message")
data class MessageEvent(val message: MessageDto) : Event

@Serializable @SerialName("friendRequest")
data class FriendRequestEvent(val from: UserDto) : Event

@Serializable @SerialName("friendAdded")
data class FriendAddedEvent(val friend: FriendDto) : Event

@Serializable @SerialName("friendRemoved")
data class FriendRemovedEvent(val userId: Long) : Event

/** Events the app sends to the server. */
@Serializable
sealed interface ClientEvent

@Serializable @SerialName("nowPlaying")
data class NowPlayingUpdate(val song: SongRef? = null) : ClientEvent

val eventJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Keeps track of who is connected right now and what they're playing, and delivers events.
 * A user counts as online while at least one of their devices has the WebSocket open.
 * None of this is saved: after a restart everyone simply reconnects.
 */
class Hub(private val friendsOf: suspend (Long) -> List<Long>) {
    private val connections = ConcurrentHashMap<Long, MutableSet<WebSocketSession>>()
    private val nowPlaying = ConcurrentHashMap<Long, SongRef>()
    private val lock = Mutex()

    fun isOnline(userId: Long) = connections[userId]?.isNotEmpty() == true
    fun nowPlaying(userId: Long): SongRef? = nowPlaying[userId]

    suspend fun connected(userId: Long, session: WebSocketSession) {
        val cameOnline = lock.withLock {
            val set = connections.getOrPut(userId) { mutableSetOf() }
            set.add(session)
            set.size == 1
        }
        if (cameOnline) announcePresence(userId)
    }

    suspend fun disconnected(userId: Long, session: WebSocketSession) {
        val wentOffline = lock.withLock {
            val set = connections[userId] ?: return
            set.remove(session)
            if (set.isEmpty()) {
                connections.remove(userId)
                nowPlaying.remove(userId)
                true
            } else {
                false
            }
        }
        if (wentOffline) announcePresence(userId)
    }

    suspend fun handle(userId: Long, event: ClientEvent) {
        when (event) {
            is NowPlayingUpdate -> {
                if (event.song == null) nowPlaying.remove(userId) else nowPlaying[userId] = event.song
                announcePresence(userId)
            }
        }
    }

    /** Sends [event] to every connected device of [userIds]. Returns the users who were offline. */
    suspend fun send(userIds: Collection<Long>, event: Event): List<Long> {
        val text = eventJson.encodeToString(Event.serializer(), event)
        val offline = mutableListOf<Long>()
        for (id in userIds.toSet()) {
            val sessions = connections[id]?.toList().orEmpty()
            if (sessions.isEmpty()) offline += id
            sessions.forEach { runCatching { it.send(Frame.Text(text)) } }
        }
        return offline
    }

    private suspend fun announcePresence(userId: Long) {
        send(friendsOf(userId), PresenceEvent(userId, isOnline(userId), nowPlaying[userId]))
    }
}
