package io.github.devasenan134.isaipetti.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
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
    /** Navidrome's user list as id -> username; null means "Navidrome unreachable". */
    var accounts: MutableMap<String, String>? = null
    override suspend fun users() = accounts?.map { (id, name) -> NavidromeUser(id, name) }
    override suspend fun checkLogin(username: String, salt: String, token: String) = token == "ok-$username"
    override suspend fun createUser(username: String, displayName: String, password: String): String? {
        if (username in created) throw ApiError(HttpStatusCode.Conflict, "That username is taken")
        created += username
        return null
    }
}

/** Records notifications instead of sending them. */
private class FakePush : PushSender {
    val sent = mutableListOf<Pair<String, Map<String, String>>>()
    override suspend fun send(deviceToken: String, data: Map<String, String>): PushSender.Result {
        sent += deviceToken to data
        return if (deviceToken.startsWith("dead")) PushSender.Result.InvalidToken else PushSender.Result.Sent
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
            "/auth/signup", SignupRequest(invite.code.lowercase().replace("-", ""), "bob", "quiet-river-song", "Bob"),
        ).body<SessionResponse>()
        assertEquals(listOf("bob"), navidrome.created)
        // The same code can't be used twice.
        val reused = client.postJson("/auth/signup", SignupRequest(invite.code, "eve", "quiet-river-song"))
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

        // Renaming shows up for friends; logging out other devices keeps only the current session.
        client.patch("/me") { bearerAuth(alice.sessionToken); contentType(ContentType.Application.Json); setBody(RenameRequest("Alice ✨")) }
        assertEquals("Alice ✨", client.friends(bob).first { it.user.id == alice.user.id }.user.displayName)
        val aliceSecondPhone = client.login("alice")
        client.postJson("/auth/logout-others", Unit, aliceSecondPhone.sessionToken)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me") { bearerAuth(alice.sessionToken) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/me") { bearerAuth(aliceSecondPhone.sessionToken) }.status)

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
        val bob = client.postJson("/auth/signup", SignupRequest(code, "bob", "quiet-river-song", "Bob")).body<SessionResponse>()
        client.postJson("/friends/requests", AddFriendRequest("carol"), alice.sessionToken)
        client.postJson("/friends/requests/${alice.user.id}/accept", Unit, carol.sessionToken)
        val dm = client.postJson("/conversations/dm", NewDmRequest(bob.user.id), alice.sessionToken).body<ConversationDto>()
        client.postJson("/conversations/${dm.id}/messages", SendMessageRequest("hi alice"), bob.sessionToken)
        val group = client.postJson("/conversations/group", NewGroupRequest("gang", listOf(bob.user.id, carol.user.id)), alice.sessionToken)
            .body<ConversationDto>()

        // The cleanup job runs against the same database file (the app's own copy runs on a timer).
        val cleanup = Cleanup(Db(path), navidrome, Hub { emptyList() })

        // Navidrome unreachable, or a list that would remove most people: do nothing.
        navidrome.accounts = null
        assertEquals(emptyList(), cleanup.run().removed)
        navidrome.accounts = mutableMapOf("nd-alice" to "alice")
        assertEquals(emptyList(), cleanup.run().removed)

        // Bob's account is deleted in Navidrome.
        navidrome.accounts = mutableMapOf("nd-alice" to "alice", "nd-carol" to "carol")
        assertEquals(listOf("bob"), cleanup.run().removed)
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

        // An admin renames carol to caroline in Navidrome (same account id): she keeps her friends,
        // and logging in with the new name is still her.
        navidrome.accounts!!["nd-carol"] = "caroline"
        assertEquals(listOf("carol" to "caroline"), cleanup.run().renamed)
        assertEquals(listOf("caroline"), client.friends(alice).map { it.user.username })
        assertEquals(carol.user.id, client.login("caroline").user.id)

        // Alice's account is deleted and a *different* "alice" is created before the cleanup runs:
        // logging in as the new alice must not inherit the old one's friends.
        navidrome.accounts!!.remove("nd-alice")
        navidrome.accounts!!["nd-alice-2"] = "alice"
        val otherAlice = client.login("alice")
        assertTrue(otherAlice.user.id != alice.user.id)
        assertEquals(emptyList(), client.friends(otherAlice))
        assertEquals(emptyList(), client.friends(client.login("caroline")))
    }

    @Test
    fun `password rules`() {
        fun problem(p: String, user: String = "alice") = PasswordRules.check(p, user).problem
        assertEquals("Use at least 10 characters", problem("short1!"))
        assertEquals("That's one of the most commonly used passwords", problem("qwertyuiop"))
        assertEquals("That's one of the most commonly used passwords", problem("Basketball"))
        assertEquals("Don't include your username in the password", problem("alice-rocks-2026"))
        assertEquals("Too repetitive. Mix in more different characters", problem("aaaaaaaaaaab"))
        assertEquals(null, problem("kaatru veliyidai"))
        assertEquals(PasswordRules.Strength.Okay, PasswordRules.check("mellisaimannan", "alice").strength)
        assertEquals(PasswordRules.Strength.Strong, PasswordRules.check("Nila-Kaayudhu 1994", "alice").strength)
    }

    @Test
    fun `push notifications go to people without the app on screen`() = testApplication {
        val navidrome = FakeNavidrome()
        val push = FakePush()
        application { isaipettiSocial(Config(0, dbFile(), "http://unused", "", ""), navidrome, push) }
        val client = createClient {
            install(ContentNegotiation) { json(eventJson) }
            install(WebSockets)
        }
        val alice = client.login("alice")
        val bob = client.login("bob")
        val carol = client.login("carol")
        // Bob also still has an old phone whose token Firebase no longer accepts.
        for ((session, token) in listOf(alice to "phone-alice", bob to "phone-bob", bob to "dead-old-phone-bob", carol to "phone-carol")) {
            client.postJson("/devices", DeviceRequest(token), session.sessionToken)
        }

        // Carol is offline and asks Bob (also offline) to be friends: Bob gets a notification.
        client.postJson("/friends/requests", AddFriendRequest("bob"), carol.sessionToken)
        assertEquals(
            setOf("phone-bob" to "friendRequest", "dead-old-phone-bob" to "friendRequest"),
            push.sent.map { it.first to it.second["type"] }.toSet(),
        )
        push.sent.clear()
        client.postJson("/friends/requests", AddFriendRequest("alice"), bob.sessionToken)
        client.postJson("/friends/requests", AddFriendRequest("alice"), carol.sessionToken)
        client.postJson("/friends/requests/${bob.user.id}/accept", Unit, alice.sessionToken)
        client.postJson("/friends/requests/${carol.user.id}/accept", Unit, alice.sessionToken)
        push.sent.clear()

        // Alice has the app open; Bob is listening with the app in the background; Carol is offline.
        val aliceWs = client.webSocketSession("/ws?token=${alice.sessionToken}")
        aliceWs.send(Frame.Text(eventJson.encodeToString(ClientEvent.serializer(), AppStateUpdate(visible = true))))
        val bobWs = client.webSocketSession("/ws?token=${bob.sessionToken}")
        bobWs.send(Frame.Text(eventJson.encodeToString(ClientEvent.serializer(), AppStateUpdate(visible = false))))
        eventually { client.friends(alice).count { it.online } == 1 }

        val group = client.postJson("/conversations/group", NewGroupRequest("gang", listOf(bob.user.id, carol.user.id)), alice.sessionToken)
            .body<ConversationDto>()
        client.postJson("/conversations/${group.id}/messages", SendMessageRequest("dinner?"), alice.sessionToken)
        // Bob (background) and Carol (offline) are notified; Alice sent it, so she isn't.
        eventually { push.sent.size == 2 }
        // (Bob's dead old phone was forgotten after the first failed delivery.)
        assertEquals(setOf("phone-bob", "phone-carol"), push.sent.map { it.first }.toSet())
        val note = push.sent.first().second
        assertEquals(listOf("message", "gang", "alice", "dinner?"), listOf(note["type"], note["title"], note["sender"], note["body"]))

        // Bob replies; Alice has the app open so she gets nothing, only offline Carol does.
        push.sent.clear()
        client.postJson("/conversations/${group.id}/messages", SendMessageRequest("yes!"), bob.sessionToken)
        eventually { push.sent.isNotEmpty() }
        assertEquals(listOf("phone-carol"), push.sent.map { it.first })
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
