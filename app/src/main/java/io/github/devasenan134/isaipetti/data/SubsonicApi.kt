package io.github.devasenan134.isaipetti.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom

class SubsonicException(message: String) : Exception(message)

/**
 * Talks to Navidrome through the Subsonic API: `https://<server>/rest/<endpoint>?<auth>&<params>`.
 * Docs: https://opensubsonic.netlify.app/docs/
 */
class SubsonicApi(
    private val http: OkHttpClient,
    private val credentials: () -> Credentials?,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun ping(credentials: Credentials) {
        get("ping", credentials = credentials)
    }

    /** [type] is one of: newest, recent, frequent, random, alphabeticalByName, byYear, starred. */
    suspend fun albumList(type: String, size: Int = 50, offset: Int = 0, extra: Map<String, Any> = emptyMap()): List<Album> =
        get("getAlbumList2", mapOf("type" to type, "size" to size, "offset" to offset) + extra)
            .decode<AlbumList>("albumList2")?.album.orEmpty()

    suspend fun album(id: String): Album =
        get("getAlbum", mapOf("id" to id)).decode<Album>("album") ?: throw SubsonicException("Album not found")

    /** Album artists, which in this library are the music directors. */
    suspend fun artists(): List<Artist> =
        get("getArtists").decode<Artists>("artists")?.index.orEmpty().flatMap { it.artist }

    suspend fun artist(id: String): Artist =
        get("getArtist", mapOf("id" to id)).decode<Artist>("artist") ?: throw SubsonicException("Artist not found")

    suspend fun search(query: String): SearchResult =
        get("search3", mapOf("query" to query, "artistCount" to 10, "albumCount" to 20, "songCount" to 50))
            .decode<SearchResult>("searchResult3") ?: SearchResult()

    suspend fun playlists(): List<Playlist> =
        get("getPlaylists").decode<Playlists>("playlists")?.playlist.orEmpty()

    suspend fun playlist(id: String): Playlist =
        get("getPlaylist", mapOf("id" to id)).decode<Playlist>("playlist") ?: throw SubsonicException("Playlist not found")

    suspend fun lyrics(songId: String): List<StructuredLyrics> =
        get("getLyricsBySongId", mapOf("id" to songId)).decode<LyricsList>("lyricsList")?.structuredLyrics.orEmpty()

    /** Records a play. submission=false means "now playing"; true adds it to play counts and history. */
    suspend fun scrobble(songId: String, submission: Boolean) {
        get("scrobble", mapOf("id" to songId, "submission" to submission, "time" to System.currentTimeMillis()))
    }

    fun streamUrl(songId: String): String = url("stream", mapOf("id" to songId)).toString()

    fun coverUrl(coverArtId: String?, size: Int = 300): String? =
        coverArtId?.let { url("getCoverArt", mapOf("id" to it, "size" to size)).toString() }

    private fun url(endpoint: String, params: Map<String, Any> = emptyMap(), credentials: Credentials? = null): HttpUrl {
        val creds = credentials ?: this.credentials() ?: throw SubsonicException("Not logged in")
        val builder = "${creds.server}/rest/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("u", creds.username)
            .addQueryParameter("t", creds.token)
            .addQueryParameter("s", creds.salt)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_NAME)
            .addQueryParameter("f", "json")
        params.forEach { (key, value) -> builder.addQueryParameter(key, value.toString()) }
        return builder.build()
    }

    /** Makes a request and returns the body of `subsonic-response`, or throws with the server's error message. */
    private suspend fun get(
        endpoint: String,
        params: Map<String, Any> = emptyMap(),
        credentials: Credentials? = null,
    ): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url(endpoint, params, credentials)).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw SubsonicException("Server returned HTTP ${response.code}")
            val body = runCatching {
                json.parseToJsonElement(response.body.string()).jsonObject["subsonic-response"]?.jsonObject
            }.getOrNull() ?: throw SubsonicException("That doesn't look like a Navidrome server")
            if (body["status"]?.jsonPrimitive?.content != "ok") {
                val message = body["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                throw SubsonicException(message ?: "Request failed")
            }
            body
        }
    }

    private inline fun <reified T> JsonObject.decode(key: String): T? =
        this[key]?.let { json.decodeFromJsonElement<T>(it) }

    companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "isaipetti"

        /** Builds login credentials from a password, without keeping the password. */
        fun credentialsFor(server: String, username: String, password: String): Credentials {
            val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }.toHex()
            val token = MessageDigest.getInstance("MD5").digest((password + salt).toByteArray()).toHex()
            return Credentials(normalizeServer(server), username.trim(), salt, token)
        }

        /** "music.example.com/" -> "https://music.example.com" */
        fun normalizeServer(input: String): String {
            var server = input.trim().trimEnd('/')
            if (!server.startsWith("http://") && !server.startsWith("https://")) server = "https://$server"
            return server.removeSuffix("/app")
        }

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}
