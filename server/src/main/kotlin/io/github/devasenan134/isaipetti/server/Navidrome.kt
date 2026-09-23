package io.github.devasenan134.isaipetti.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The two things the companion server needs from Navidrome: checking logins and creating users. */
open class Navidrome(private val config: Config) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }

    /** True if the Subsonic token login works, i.e. the user proved they know their Navidrome password. */
    open suspend fun checkLogin(username: String, salt: String, token: String): Boolean {
        val response = http.get("${config.navidromeUrl}/rest/ping") {
            parameter("u", username)
            parameter("s", salt)
            parameter("t", token)
            parameter("v", "1.16.1")
            parameter("c", "isaipetti-social")
            parameter("f", "json")
        }
        if (!response.status.isSuccess()) return false
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject["subsonic-response"]?.jsonObject
        return body?.get("status")?.jsonPrimitive?.content == "ok"
    }

    /** Creates a normal (non-admin) Navidrome user through Navidrome's own admin API. */
    open suspend fun createUser(username: String, displayName: String, password: String) {
        if (config.navidromeAdminUser.isBlank()) throw ApiError(HttpStatusCode.ServiceUnavailable, "Sign-up isn't set up on this server yet")
        val adminToken = adminLogin()
        val response = http.post("${config.navidromeUrl}/api/user") {
            header("X-ND-Authorization", "Bearer $adminToken")
            contentType(ContentType.Application.Json)
            setBody(NewUser(userName = username, name = displayName, password = password))
        }
        if (!response.status.isSuccess()) {
            val text = response.bodyAsText()
            if ("unique" in text.lowercase() || "exists" in text.lowercase()) {
                throw ApiError(HttpStatusCode.Conflict, "That username is taken")
            }
            throw ApiError(HttpStatusCode.BadGateway, "Navidrome refused to create the user (${response.status.value})")
        }
    }

    /** Every username in Navidrome (lowercase), or null if the list couldn't be fetched. */
    open suspend fun userNames(): Set<String>? {
        if (config.navidromeAdminUser.isBlank()) return null
        return runCatching {
            val response = http.get("${config.navidromeUrl}/api/user") {
                header("X-ND-Authorization", "Bearer ${adminLogin()}")
                parameter("_start", 0)
                parameter("_end", 10_000)
            }
            if (!response.status.isSuccess()) return null
            response.body<List<JsonObject>>().mapNotNull { it["userName"]?.jsonPrimitive?.content?.lowercase() }.toSet()
        }.getOrNull()
    }

    private suspend fun adminLogin(): String {
        val response = http.post("${config.navidromeUrl}/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(AdminLogin(config.navidromeAdminUser, config.navidromeAdminPassword))
        }
        if (!response.status.isSuccess()) throw ApiError(HttpStatusCode.BadGateway, "The server's Navidrome admin login failed")
        return response.body<JsonObject>()["token"]?.jsonPrimitive?.content
            ?: throw ApiError(HttpStatusCode.BadGateway, "Navidrome didn't return an admin token")
    }

    @Serializable private data class AdminLogin(val username: String, val password: String)

    @Serializable
    private data class NewUser(
        val userName: String,
        val name: String,
        val password: String,
        val email: String = "",
        val isAdmin: Boolean = false,
    )
}
