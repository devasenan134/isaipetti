package io.github.devasenan134.isaipetti.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pretends to be Navidrome: a login works when the token is "ok-<username>". */
private class FakeNavidrome : Navidrome(Config(0, "", "http://unused", "", "")) {
    val created = mutableListOf<String>()
    /** What Navidrome's user list returns; null means "Navidrome unreachable". */
    var existing: Set<String>? = null
    override suspend fun userNames() = existing
    override suspend fun checkLogin(username: String, salt: String, token: String) = token == "ok-$username"
    override suspend fun createUser(username: String, displayName: String, password: String) {
        if (username in created) throw ApiError(HttpStatusCode.Conflict, "That username is taken")
        created += username
    }
}

class FlowTest {
    private fun dbFile() = File.createTempFile("isaipetti-social", ".db").apply { delete(); deleteOnExit() }.path

    @Test
    fun `invite, sign up, friends, presence and chat`() = testApplication {
        val navidrome = FakeNavidrome()
        application { isaipettiSocial(Config(0, dbFile(), "http://unused", "", ""), navidrome) }
        val client = createClient {
            install(ContentNegotiation) { json(eventJson) }
            install(WebSockets)
        }

        // Alice already has a Navidrome account and logs in.
        val alice = client.login("alice")
        assertEquals(HttpStatusCode.Unauthorized, client.postJson("/auth/login", LoginRequest("alice", "s", "wrong")).status)

        // She invites Bob, who signs up with the code (typed in lowercase, without the dash).
        val invite = client.postJson("/invites", Unit, alice.sessionToken).body<InviteDto>()
        assertTrue(Regex("[A-Z2-9]{4}-[A-Z2-9]{4}").matches(invite.code))
        val bob = client.postJson(
            "/auth/signup", SignupRequest(invite.code.lowercase().replace("-", ""), "bob", "longpassword", "Bob"),
        ).body<SessionResponse>()
        assertEquals(listOf("bob"), navidrome.created)
        // The same code can't be used twice.
        val reused = client.postJson("/auth/signup", SignupRequest(invite.code, "eve", "longpassword"))
        assertEquals(HttpStatusCode.BadRequest, reused.status)

        // Bob and Alice are friends automatically; the invite shows who used it.
        assertEquals(listOf("alice"), client.friends(bob).map { it.user.username })
        assertEquals("bob", client.getJson<List<InviteDto>>("/invites", alice).single().usedBy?.username)

        // Carol logs in and asks Alice to be friends; Alice accepts.
        val carol = client.login("carol")
        assertEquals("requested", client.postJson("/friends/requests", AddFriendRequest("alice"), carol.sessionToken).body<AddFriendResponse>().status)
        assertEquals(listOf("carol"), client.getJson<FriendRequestsDto>("/friends/requests", alice).incoming.map { it.username })
        client.postJson("/friends/requests/${carol.user.id}/accept", Unit, alice.sessionToken)
        assertEquals(setOf("bob", "carol"), client.friends(alice).map { it.user.username }.toSet())

        // Live: Alice is connected; Bob comes online and starts a song; Alice hears about both.
        val aliceWs = client.webSocketSession("/ws?token=${alice.sessionToken}")
        eventually { client.friends(bob).single().online }
        val bobWs = client.webSocketSession("/ws?token=${bob.sessionToken}")
        val online = aliceWs.nextEvent() as PresenceEvent
        assertEquals(bob.user.id to true, online.userId to online.online)
        val song = SongRef("song1", "Nee Tholaindhaayo", "Sid Sriram", "Kavalai Vendam")
        bobWs.send(Frame.Text(eventJson.encodeToString(ClientEvent.serializer(), NowPlayingUpdate(song))))
        assertEquals(song, (aliceWs.nextEvent() as PresenceEvent).nowPlaying)
        assertEquals(song, client.friends(alice).first { it.user.username == "bob" }.nowPlaying)

        // Alice messages Bob with a song; Bob receives it live and sees it as unread.
        val dm = client.postJson("/conversations/dm", NewDmRequest(bob.user.id), alice.sessionToken).body<ConversationDto>()
        client.postJson("/conversations/${dm.id}/messages", SendMessageRequest("listen to this!", song), alice.sessionToken)
        val received = (bobWs.nextEvent() as MessageEvent).message
        assertEquals("listen to this!" to song, received.body to received.song)
        assertEquals(1, client.getJson<List<ConversationDto>>("/conversations", bob).single().unread)
        client.postJson("/conversations/${dm.id}/read", MarkReadRequest(received.id), bob.sessionToken)
        assertEquals(0, client.getJson<List<ConversationDto>>("/conversations", bob).single().unread)
        // Opening the DM again returns the same conversation.
        assertEquals(dm.id, client.postJson("/conversations/dm", NewDmRequest(alice.user.id), bob.sessionToken).body<ConversationDto>().id)

        // Group chat with all three; Carol can read it, and strangers can't message non-friends.
        val group = client.postJson("/conversations/group", NewGroupRequest("Ilaiyaraaja night", listOf(bob.user.id, carol.user.id)), alice.sessionToken)
            .body<ConversationDto>()
        client.postJson("/conversations/${group.id}/messages", SendMessageRequest("8pm?"), bob.sessionToken)
        assertEquals(listOf("8pm?"), client.getJson<List<MessageDto>>("/conversations/${group.id}/messages", carol).map { it.body })
        assertEquals(HttpStatusCode.Forbidden, client.postJson("/conversations/dm", NewDmRequest(bob.user.id), carol.sessionToken).status)

        // Bob disconnects; Alice sees him go offline.
        bobWs.close()
        val offline = aliceWs.nextPresence(bob.user.id)
        assertEquals(false, offline.online)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/friends") { bearerAuth("nope") }.status)
    }

    @Test
    fun `people deleted from Navidrome are cleaned up`() = testApplication {
        val navidrome = FakeNavidrome()
        val path = dbFile()
        application { isaipettiSocial(Config(0, path, "http://unused", "", ""), navidrome) }
        val client = createClient { install(ContentNegotiation) { json(eventJson) } }

        val alice = client.login("alice")
        val carol = client.login("carol")
        val code = client.postJson("/invites", Unit, alice.sessionToken).body<InviteDto>().code
        val bob = client.postJson("/auth/signup", SignupRequest(code, "bob", "longpassword", "Bob")).body<SessionResponse>()
        client.postJson("/friends/requests", AddFriendRequest("carol"), alice.sessionToken)
        client.postJson("/friends/requests/${alice.user.id}/accept", Unit, carol.sessionToken)
        val dm = client.postJson("/conversations/dm", NewDmRequest(bob.user.id), alice.sessionToken).body<ConversationDto>()
        client.postJson("/conversations/${dm.id}/messages", SendMessageRequest("hi alice"), bob.sessionToken)
        val group = client.postJson("/conversations/group", NewGroupRequest("gang", listOf(bob.user.id, carol.user.id)), alice.sessionToken)
            .body<ConversationDto>()

        // The cleanup job runs against the same database file (the app's own copy runs on a timer).
        val cleanup = Cleanup(Db(path), navidrome, Hub { emptyList() })

        // Navidrome unreachable, or a list that would remove most people: do nothing.
        navidrome.existing = null
        assertEquals(emptyList(), cleanup.run())
        navidrome.existing = setOf("alice")
        assertEquals(emptyList(), cleanup.run())

        // Bob's account is deleted in Navidrome.
        navidrome.existing = setOf("alice", "carol")
        assertEquals(listOf("bob"), cleanup.run())
        assertEquals(listOf("carol"), client.friends(alice).map { it.user.username })
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me") { bearerAuth(bob.sessionToken) }.status)
        // His old message is still there, marked as left, but the DM is closed.
        val history = client.getJson<List<MessageDto>>("/conversations/${dm.id}/messages", alice)
        assertEquals("Bob (left)" to "hi alice", history.single().sender.displayName to history.single().body)
        assertEquals(HttpStatusCode.Forbidden, client.postJson("/conversations/${dm.id}/messages", SendMessageRequest("hello?"), alice.sessionToken).status)
        // He's gone from the group.
        val groupNow = client.getJson<List<ConversationDto>>("/conversations", alice).first { it.id == group.id }
        assertEquals(setOf("alice", "carol"), groupNow.members.map { it.username }.toSet())

        // If "bob" is created again later, it's a brand-new person with no friends.
        val newBob = client.login("bob")
        assertTrue(newBob.user.id != bob.user.id)
        assertEquals(emptyList(), client.friends(newBob))
    }

    /** Retries [condition] for up to 5 seconds (the server registers WebSockets asynchronously). */
    private suspend fun eventually(condition: suspend () -> Boolean) = withTimeout(5_000) {
        while (!condition()) kotlinx.coroutines.delay(20)
    }

    private suspend fun HttpClient.login(username: String) =
        postJson("/auth/login", LoginRequest(username, "salt", "ok-$username")).body<SessionResponse>()

    private suspend fun HttpClient.friends(session: SessionResponse) = getJson<List<FriendDto>>("/friends", session)

    private suspend inline fun <reified T> HttpClient.getJson(path: String, session: SessionResponse): T =
        get(path) { bearerAuth(session.sessionToken) }.body()

    private suspend inline fun <reified B> HttpClient.postJson(path: String, body: B, token: String? = null): HttpResponse =
        post(path) {
            token?.let { bearerAuth(it) }
            if (body !is Unit) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

    private suspend fun DefaultWebSocketSession.nextEvent(): Event = withTimeout(5_000) {
        val frame = incoming.receive() as Frame.Text
        eventJson.decodeFromString(Event.serializer(), frame.readText())
    }

    /** Skips other events until a presence update for [userId] arrives. */
    private suspend fun DefaultWebSocketSession.nextPresence(userId: Long): PresenceEvent {
        while (true) {
            val event = nextEvent()
            if (event is PresenceEvent && event.userId == userId) return event
        }
    }
}
