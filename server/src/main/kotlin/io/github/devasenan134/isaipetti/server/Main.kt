package io.github.devasenan134.isaipetti.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val log = LoggerFactory.getLogger("isaipetti-social")
private const val MAX_BODY_BYTES = 1024 * 1024L

fun main() {
    val config = Config.fromEnv()
    embeddedServer(Netty, port = config.port) { isaipettiSocial(config) }.start(wait = true)
}

/** Everything the server does, as one Ktor module (tests start it the same way). */
fun Application.isaipettiSocial(
    config: Config,
    navidrome: Navidrome = Navidrome(config),
    pushSender: PushSender = config.firebaseKeyFile?.let { FcmSender(it) } ?: NoPush,
    issueTracker: IssueTracker? = config.githubToken?.let { token -> config.githubRepo?.let { GitHubIssues(it, token) } },
    pushConfig: PushConfig? = config.firebaseAppConfigFile?.let { PushConfig.fromGoogleServices(it) },
    music: MusicSource? = config.navidromeDb?.let { NavidromeLibrary(it, config.featuresDb) },
) {
    val db = Db(config.dbPath)
    val friends = Friends(db)
    val hub = Hub(friends::friendIds)
    friends.hub = hub
    val accounts = Accounts(db, navidrome, onFriendsAdded = friends::announceFriendship)
    val chat = Chat(db, friends, hub, PictureFolder(config.dbPath, "group-pictures"))
    val listen = ListenTogether(hub, chat::members)
    chat.listenersOf = listen::listeners
    chat.onLeft = listen::leftChat
    chat.onRemoved = listen::ended
    val push = Push(db, pushSender)
    friends.push = push
    chat.onUnseen = push::newMessage
    listen.onStarted = { userId, conversationId ->
        val recipients = chat.members(conversationId).filter { it != userId && !hub.isVisible(it) }
        push.listenStarted(userId, conversationId, recipients)
    }
    val bugReports = BugReports(issueTracker)
    val playlistLikes = PlaylistLikes(db)
    val pictures = Pictures(db, navidrome, config.dbPath)
    val mixes = music?.let { MixService(db, it, java.time.ZoneId.of(config.timeZone)) }
    val limiter = RateLimiter(maxPerMinute = 10)
    val cleanup = Cleanup(db, navidrome, hub)
    // Every 10 minutes, remove people whose Navidrome account is gone.
    launch {
        delay(30.seconds)
        while (isActive) {
            runCatching { cleanup.run() }.onFailure { log.warn("Cleanup failed", it) }
            delay(10.minutes)
        }
    }

    install(ContentNegotiation) { json(eventJson) }
    install(CallLogging)
    install(WebSockets) {
        pingPeriod = 20.seconds
        timeout = 45.seconds
        maxFrameSize = 4L * 1024 * 1024 // a listen-together queue of a few thousand songs fits
        contentConverter = KotlinxWebsocketSerializationConverter(eventJson)
    }
    install(StatusPages) {
        exception<ApiError> { call, e -> call.respond(e.status, ErrorResponse(e.message)) }
        exception<SerializationException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("Malformed request")) }
        exception<Throwable> { call, e ->
            log.error("Unhandled error", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Something went wrong on the server"))
        }
    }
    // Refuse oversized requests before reading them (nothing legitimate comes close to 1 MB).
    install(createApplicationPlugin("BodySizeLimit") {
        onCall { call ->
            val length = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (length != null && length > MAX_BODY_BYTES) call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("Request is too large"))
        }
    })
    install(Authentication) {
        bearer("session") {
            authenticate { credential -> accounts.userForToken(credential.token) }
        }
    }

    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }

        route("/auth") {
            post("/login") {
                limiter.check(call)
                call.respond(accounts.login(call.receive()))
            }
            post("/signup") {
                limiter.check(call)
                call.respond(accounts.signup(call.receive()))
            }
        }

        // The WebSocket authenticates with ?token=, since not every client can set headers on it.
        webSocket("/ws") {
            val user = call.request.queryParameters["token"]?.let { accounts.userForToken(it) }
            if (user == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Not logged in"))
                return@webSocket
            }
            hub.connected(user.id, this)
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val event = runCatching { eventJson.decodeFromString(ClientEvent.serializer(), frame.readText()) }.getOrNull()
                    when (event) {
                        null -> Unit
                        is ListenStart, is ListenJoin, is ListenLeave, is ListenUpdate -> listen.handle(user.id, event)
                        else -> hub.handle(user.id, this, event)
                    }
                }
            } finally {
                hub.disconnected(user.id, this)
                // Offline on every device: leave any listen-together session.
                if (!hub.isOnline(user.id)) listen.leaveAll(user.id)
            }
        }

        authenticate("session") {
            get("/me") { call.respond(call.me()) }
            patch("/me") { call.respond(accounts.rename(call.me().id, call.receive<RenameRequest>().displayName)) }
            // Profile pictures: the body is the image itself.
            put("/me/avatar") { call.respond(pictures.setAvatar(call.me().id, Picture(call.receive<ByteArray>()))) }
            delete("/me/avatar") { call.respond(pictures.removeAvatar(call.me().id)) }
            get("/users/{id}/avatar") {
                val file = pictures.avatar(call.longParam("id")) ?: throw ApiError(HttpStatusCode.NotFound, "No picture")
                // The app asks with ?v=<when it was set>, so a new picture has a new address and this can be cached.
                call.response.headers.append(HttpHeaders.CacheControl, "private, max-age=2592000")
                call.respondFile(file)
            }
            // A cover for a playlist you made (stored in Navidrome, which the server changes as admin after checking).
            put("/playlists/{id}/cover") {
                pictures.setPlaylistCover(call.me(), call.parameters["id"].orEmpty(), Picture(call.receive<ByteArray>()))
                call.respond(HttpStatusCode.NoContent)
            }
            delete("/playlists/{id}/cover") {
                pictures.setPlaylistCover(call.me(), call.parameters["id"].orEmpty(), null)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/auth/logout-others") {
                accounts.logoutOthers(call.me().id, call.bearerToken())
                call.respond(HttpStatusCode.NoContent)
            }
            post("/auth/logout") {
                accounts.logout(call.bearerToken())
                call.respond(HttpStatusCode.NoContent)
            }

            // Firebase settings for the app, so that nothing about this server's project is built into it.
            get("/push/config") {
                if (pushConfig == null || pushSender is NoPush) call.respond(HttpStatusCode.NotFound, ErrorResponse("Push notifications are off"))
                else call.respond(pushConfig)
            }

            route("/devices") {
                post {
                    push.register(call.me().id, call.receive<DeviceRequest>().token)
                    call.respond(HttpStatusCode.NoContent)
                }
                post("/remove") {
                    push.unregister(call.me().id, call.receive<DeviceRequest>().token)
                    call.respond(HttpStatusCode.NoContent)
                }
            }

            route("/likes/playlists") {
                get { call.respond(playlistLikes.list(call.me().id)) }
                put {
                    playlistLikes.like(call.me().id, call.receive())
                    call.respond(HttpStatusCode.NoContent)
                }
                delete("/{id}") {
                    playlistLikes.unlike(call.me().id, call.parameters["id"].orEmpty())
                    call.respond(HttpStatusCode.NoContent)
                }
            }

            // Mixes, playlists and stations by Isai Pettai.
            route("/mixes") {
                fun mixesOn() = mixes ?: throw ApiError(HttpStatusCode.NotFound, "Mixes are off on this server")
                get { call.respond(mixesOn().home(call.me())) }
                get("/followed") { call.respond(mixesOn().followed(call.me())) }
                post("/radio") { call.respond(mixesOn().radio(call.me(), call.receive())) }
                post("/recommend") { call.respond(mixesOn().recommend(call.me(), call.receive())) }
                get("/{id}") { call.respond(mixesOn().mix(call.me(), call.parameters["id"].orEmpty())) }
                put("/{id}/follow") {
                    mixesOn().follow(call.me(), call.parameters["id"].orEmpty())
                    call.respond(HttpStatusCode.NoContent)
                }
                delete("/{id}/follow") {
                    mixesOn().unfollow(call.me(), call.parameters["id"].orEmpty())
                    call.respond(HttpStatusCode.NoContent)
                }
            }
            // What the app played and skipped; mixes learn from it. Kept even when mixes are off.
            post("/plays") {
                mixes?.recordPlays(call.me(), call.receive<PlaysRequest>().events)
                call.respond(HttpStatusCode.NoContent)
            }

            post("/bug-reports") { call.respond(bugReports.report(call.me(), call.receive())) }

            route("/invites") {
                get { call.respond(accounts.invites(call.me().id)) }
                post { call.respond(accounts.createInvite(call.me().id)) }
                delete("/{code}") {
                    accounts.deleteInvite(call.me().id, call.parameters["code"].orEmpty())
                    call.respond(HttpStatusCode.NoContent)
                }
            }

            route("/friends") {
                get { call.respond(friends.list(call.me().id)) }
                get("/requests") { call.respond(friends.requests(call.me().id)) }
                post("/requests") { call.respond(friends.request(call.me(), call.receive<AddFriendRequest>().username)) }
                post("/requests/{userId}/accept") {
                    friends.accept(call.me(), call.longParam("userId"))
                    call.respond(HttpStatusCode.NoContent)
                }
                post("/requests/{userId}/decline") {
                    friends.decline(call.me(), call.longParam("userId"))
                    call.respond(HttpStatusCode.NoContent)
                }
                delete("/{userId}") {
                    friends.remove(call.me(), call.longParam("userId"))
                    call.respond(HttpStatusCode.NoContent)
                }
            }

            route("/conversations") {
                get { call.respond(chat.conversations(call.me().id)) }
                post("/dm") { call.respond(chat.openDm(call.me(), call.receive<NewDmRequest>().userId)) }
                post("/group") { call.respond(chat.createGroup(call.me(), call.receive())) }
                get("/{id}/messages") {
                    val before = call.request.queryParameters["before"]?.toLongOrNull()
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    call.respond(chat.messages(call.me().id, call.longParam("id"), before, limit))
                }
                post("/{id}/messages") { call.respond(chat.send(call.me(), call.longParam("id"), call.receive())) }
                delete("/{id}") {
                    chat.delete(call.me().id, call.longParam("id"))
                    call.respond(HttpStatusCode.NoContent)
                }
                put("/{id}/picture") { call.respond(chat.setGroupPicture(call.me(), call.longParam("id"), Picture(call.receive<ByteArray>()))) }
                delete("/{id}/picture") { call.respond(chat.setGroupPicture(call.me(), call.longParam("id"), null)) }
                get("/{id}/picture") {
                    val file = chat.groupPicture(call.me().id, call.longParam("id")) ?: throw ApiError(HttpStatusCode.NotFound, "No photo")
                    call.response.headers.append(HttpHeaders.CacheControl, "private, max-age=2592000")
                    call.respondFile(file)
                }
                post("/{id}/leave") {
                    chat.leave(call.me(), call.longParam("id"))
                    call.respond(HttpStatusCode.NoContent)
                }
                delete("/{id}/everyone") {
                    chat.deleteForEveryone(call.me(), call.longParam("id"))
                    call.respond(HttpStatusCode.NoContent)
                }
                post("/{id}/read") {
                    chat.markRead(call.me().id, call.longParam("id"), call.receive<MarkReadRequest>().messageId)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
    log.info("isaipetti-social ready on port ${config.port}, Navidrome at ${config.navidromeUrl}, push ${if (pushSender is NoPush || pushConfig == null) "off" else "on"}, feedback ${if (issueTracker == null) "off" else "on"}, mixes ${if (mixes == null) "off" else "on"}")
}

private fun ApplicationCall.me(): UserDto = principal<UserDto>() ?: throw ApiError(HttpStatusCode.Unauthorized, "Not logged in")

private fun ApplicationCall.bearerToken(): String =
    request.headers["Authorization"]?.removePrefix("Bearer ")?.trim() ?: throw ApiError(HttpStatusCode.Unauthorized, "Not logged in")

private fun ApplicationCall.longParam(name: String): Long =
    parameters[name]?.toLongOrNull() ?: throw ApiError(HttpStatusCode.BadRequest, "Bad $name")

/** Slows down password guessing: at most [maxPerMinute] login/sign-up attempts per IP address per minute. */
class RateLimiter(private val maxPerMinute: Int) {
    private val hits = ConcurrentHashMap<String, MutableList<Long>>()

    fun check(call: ApplicationCall) {
        // Behind Cloudflare, the real client address is in this header.
        val ip = call.request.headers["CF-Connecting-IP"] ?: call.request.local.remoteAddress
        val t = now()
        val recent = hits.compute(ip) { _, list -> (list ?: mutableListOf()).apply { removeAll { it < t - 60_000 }; add(t) } }!!
        if (recent.size > maxPerMinute) throw ApiError(HttpStatusCode.TooManyRequests, "Too many attempts, try again in a minute")
    }
}
