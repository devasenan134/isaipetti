package io.github.devasenan134.isaipetti.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@Serializable data class BugReportRequest(val title: String, val description: String, val deviceInfo: String? = null)
@Serializable data class BugReportResponse(val number: Long, val url: String)

/** Opens an issue on GitHub. Returns its number and web address. */
interface IssueTracker {
    suspend fun open(title: String, body: String): BugReportResponse
}

/**
 * Files issues through GitHub's API with a token that can only touch this repository's issues.
 * The token lives on the server, so the app (which anyone can take apart) never holds it.
 */
class GitHubIssues(private val repo: String, private val token: String) : IssueTracker {
    private val http = HttpClient(CIO)
    private val log = LoggerFactory.getLogger("bug-reports")

    override suspend fun open(title: String, body: String): BugReportResponse {
        val response = http.post("https://api.github.com/repos/$repo/issues") {
            header("Authorization", "Bearer $token")
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(NewIssue.serializer(), NewIssue(title, body, listOf("bug"))))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            log.warn("GitHub refused the issue (${response.status.value}): ${text.take(300)}")
            throw ApiError(HttpStatusCode.BadGateway, "Couldn't reach GitHub, try again later")
        }
        val issue = Json.parseToJsonElement(text).jsonObject
        return BugReportResponse(issue["number"]!!.jsonPrimitive.long, issue["html_url"]!!.jsonPrimitive.content)
    }

    @Serializable private data class NewIssue(val title: String, val body: String, val labels: List<String>)
}

/**
 * Bug reports from the app's settings page become public GitHub issues. Who sent a report stays
 * on this server (in its log); the issue only says it came from the app.
 */
class BugReports(private val tracker: IssueTracker?) {
    private val log = LoggerFactory.getLogger("bug-reports")
    private val recent = ConcurrentHashMap<Long, MutableList<Long>>()

    suspend fun report(user: UserDto, request: BugReportRequest): BugReportResponse {
        val tracker = tracker ?: throw ApiError(HttpStatusCode.ServiceUnavailable, "Bug reports aren't set up on this server")
        val title = request.title.trim()
        val description = request.description.trim()
        if (title.length !in 3..120) throw ApiError(HttpStatusCode.BadRequest, "Give the bug a short title (3–120 characters)")
        if (description.length !in 10..5000) throw ApiError(HttpStatusCode.BadRequest, "Describe what happened (10–5000 characters)")
        // A few reports per hour per person is plenty, and keeps a misbehaving phone from flooding GitHub.
        val t = now()
        val mine = recent.compute(user.id) { _, list -> (list ?: mutableListOf()).apply { removeAll { it < t - 3_600_000 } } }!!
        if (mine.size >= MAX_PER_HOUR) throw ApiError(HttpStatusCode.TooManyRequests, "That's a lot of reports. Try again in an hour")
        mine += t

        val body = buildString {
            appendLine(description)
            request.deviceInfo?.trim()?.takeIf { it.isNotEmpty() }?.let {
                appendLine()
                appendLine("**Device**")
                appendLine("```")
                appendLine(it.take(1000))
                appendLine("```")
            }
            appendLine()
            append("_Reported from the app._")
        }
        return tracker.open(title, body).also { log.info("Bug report #${it.number} from ${user.username}") }
    }

    private companion object {
        const val MAX_PER_HOUR = 5
    }
}
