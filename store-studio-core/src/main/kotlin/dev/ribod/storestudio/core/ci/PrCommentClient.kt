package dev.ribod.storestudio.core.ci

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.ktor.getApiResponse
import com.skydoves.sandwich.ktor.patchApiResponse
import com.skydoves.sandwich.ktor.postApiResponse
import com.skydoves.sandwich.ktor.putApiResponse
import com.skydoves.sandwich.ktor.statusCode
import com.skydoves.sandwich.suspendOnSuccess
import dev.ribod.storestudio.core.http.installTimeoutAndRetry
import dev.ribod.storestudio.core.publish.map
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.net.URI

/**
 * Universal client for posting and updating sticky PR comments on GitHub & GitLab.
 *
 * Coordinates finding sticky markers and delegating mutations to [PrCommentApi].
 */
public class PrCommentClient(private val api: PrCommentApi) : AutoCloseable {

    public companion object {
        /**
         * Creates a production client backed by [KtorPrCommentApi] and the OkHttp engine.
         */
        public fun create(engine: HttpClientEngine = OkHttp.create()): PrCommentClient =
            PrCommentClient(KtorPrCommentApi.create(engine))
    }

    /**
     * Posts or updates a sticky visual diff report comment on the target pull request.
     */
    public suspend fun postOrUpdateComment(
        target: PrCommentTarget,
        markdownBody: String,
    ): ApiResponse<PrCommentResult> {
        val fullBody = markdownBody.trimEnd() + "\n\n" + target.commentMarker
        val listResponse = api.getComments(target)

        var result: ApiResponse<PrCommentResult>? = null
        listResponse.suspendOnSuccess {
            val existing = data.firstOrNull { it.body.trimEnd().endsWith(target.commentMarker) }
            val updateResponse = existing?.let { api.updateComment(target, it.id, fullBody) }
            // Comment deleted or not editable by this token: post a fresh one instead.
            val gone = updateResponse is ApiResponse.Failure.Error &&
                updateResponse.statusCode.code in setOf(403, 404)
            if (updateResponse != null && !gone) {
                result = updateResponse.map {
                    PrCommentResult(
                        action = CommentAction.UPDATED,
                        commentId = it.id,
                        url = it.url,
                    )
                }
            } else {
                val createResponse = api.createComment(target, fullBody)
                result = createResponse.map {
                    PrCommentResult(
                        action = CommentAction.CREATED,
                        commentId = it.id,
                        url = it.url,
                    )
                }
            }
        }

        return result ?: listResponse.map {
            PrCommentResult(CommentAction.SKIPPED, -1L, null)
        }
    }

    override fun close() {
        api.close()
    }
}

/**
 * Implementation of [PrCommentApi] using Ktor Client with Sandwich for remote API calls.
 */
public class KtorPrCommentApi(private val client: HttpClient) : PrCommentApi {

    public companion object {
        private val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        /**
         * Creates a [KtorPrCommentApi] instance with configured JSON content negotiation.
         */
        public fun create(engine: HttpClientEngine = OkHttp.create()): KtorPrCommentApi {
            val httpClient = HttpClient(engine) {
                install(ContentNegotiation) {
                    json(JSON)
                }
                installTimeoutAndRetry()
            }
            return KtorPrCommentApi(httpClient)
        }
    }

    override suspend fun getComments(target: PrCommentTarget): ApiResponse<List<RemoteComment>> =
        when (target.platform) {
            CiPlatform.GITHUB -> getGitHubComments(target)
            CiPlatform.GITLAB -> getGitLabNotes(target)
        }

    override suspend fun createComment(
        target: PrCommentTarget,
        body: String,
    ): ApiResponse<RemoteComment> = when (target.platform) {
        CiPlatform.GITHUB -> createGitHubComment(target, body)
        CiPlatform.GITLAB -> createGitLabNote(target, body)
    }

    override suspend fun updateComment(
        target: PrCommentTarget,
        commentId: Long,
        body: String,
    ): ApiResponse<RemoteComment> = when (target.platform) {
        CiPlatform.GITHUB -> updateGitHubComment(target, commentId, body)
        CiPlatform.GITLAB -> updateGitLabNote(target, commentId, body)
    }

    private suspend fun getGitHubComments(
        target: PrCommentTarget,
    ): ApiResponse<List<RemoteComment>> {
        val baseUrl = checkedBaseUrl(target.baseUrl, "https://api.github.com", "GitHub")
        val issuePath = "repos/${target.repoOrProjectId}/issues/${target.prNumber}"
        val commentsUrl = "$baseUrl/$issuePath/comments"
        return collectPages { page ->
            client.getApiResponse<List<GitHubComment>>(
                "$commentsUrl?per_page=$PAGE_SIZE&page=$page",
            ) {
                header(HttpHeaders.Authorization, "Bearer ${target.token}")
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
            }.map { list -> list.map { RemoteComment(it.id, it.body, it.html_url) } }
        }
    }

    private suspend fun createGitHubComment(
        target: PrCommentTarget,
        body: String,
    ): ApiResponse<RemoteComment> {
        val baseUrl = checkedBaseUrl(target.baseUrl, "https://api.github.com", "GitHub")
        val issuePath = "repos/${target.repoOrProjectId}/issues/${target.prNumber}"
        val commentsUrl = "$baseUrl/$issuePath/comments"
        val response = client.postApiResponse<GitHubComment>(commentsUrl) {
            header(HttpHeaders.Authorization, "Bearer ${target.token}")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            contentType(ContentType.Application.Json)
            setBody(GitHubCommentRequest(body))
        }
        return response.map { RemoteComment(it.id, it.body, it.html_url) }
    }

    private suspend fun updateGitHubComment(
        target: PrCommentTarget,
        commentId: Long,
        body: String,
    ): ApiResponse<RemoteComment> {
        val baseUrl = checkedBaseUrl(target.baseUrl, "https://api.github.com", "GitHub")
        val commentsPath = "repos/${target.repoOrProjectId}/issues/comments/$commentId"
        val updateUrl = "$baseUrl/$commentsPath"
        val response = client.patchApiResponse<GitHubComment>(updateUrl) {
            header(HttpHeaders.Authorization, "Bearer ${target.token}")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            contentType(ContentType.Application.Json)
            setBody(GitHubCommentRequest(body))
        }
        return response.map { RemoteComment(it.id, it.body, it.html_url) }
    }

    private suspend fun getGitLabNotes(target: PrCommentTarget): ApiResponse<List<RemoteComment>> {
        val baseUrl = gitLabBaseUrl(target)
        val mrPath = "projects/${gitLabProjectId(target)}/merge_requests/${target.prNumber}"
        val notesUrl = "$baseUrl/$mrPath/notes"
        return collectPages { page ->
            client.getApiResponse<List<GitLabNote>>(
                "$notesUrl?per_page=$PAGE_SIZE&page=$page",
            ) {
                header("PRIVATE-TOKEN", target.token)
            }.map { list -> list.map { RemoteComment(it.id, it.body, null) } }
        }
    }

    private suspend fun createGitLabNote(
        target: PrCommentTarget,
        body: String,
    ): ApiResponse<RemoteComment> {
        val baseUrl = gitLabBaseUrl(target)
        val mrPath = "projects/${gitLabProjectId(target)}/merge_requests/${target.prNumber}"
        val notesUrl = "$baseUrl/$mrPath/notes"
        val response = client.postApiResponse<GitLabNote>(notesUrl) {
            header("PRIVATE-TOKEN", target.token)
            contentType(ContentType.Application.Json)
            setBody(GitLabNoteRequest(body))
        }
        return response.map { RemoteComment(it.id, it.body, null) }
    }

    private suspend fun updateGitLabNote(
        target: PrCommentTarget,
        commentId: Long,
        body: String,
    ): ApiResponse<RemoteComment> {
        val baseUrl = gitLabBaseUrl(target)
        val mrPath = "projects/${gitLabProjectId(target)}/merge_requests/${target.prNumber}"
        val updateUrl = "$baseUrl/$mrPath/notes/$commentId"
        val response = client.putApiResponse<GitLabNote>(updateUrl) {
            header("PRIVATE-TOKEN", target.token)
            contentType(ContentType.Application.Json)
            setBody(GitLabNoteRequest(body))
        }
        return response.map { RemoteComment(it.id, it.body, null) }
    }

    override fun close() {
        client.close()
    }
}

private const val PAGE_SIZE = 100
private const val MAX_PAGES = 20

/** Follows 1-based pages until a short page; the cap bounds runaway PRs (older comments are skipped). */
internal suspend fun <T> collectPages(
    fetch: suspend (page: Int) -> ApiResponse<List<T>>,
): ApiResponse<List<T>> {
    val all = mutableListOf<T>()
    for (page in 1..MAX_PAGES) {
        when (val response = fetch(page)) {
            is ApiResponse.Success -> {
                all += response.data
                if (response.data.size < PAGE_SIZE) break
            }

            is ApiResponse.Failure.Error -> return ApiResponse.Failure.Error(response.payload)

            is ApiResponse.Failure.Exception -> return ApiResponse.Failure.Exception(
                response.throwable,
            )
        }
    }
    return ApiResponse.Success(all)
}

/** Only the default host or a local test server may be used over plain http; the token is sent. */
private fun checkedBaseUrl(baseUrl: String?, default: String, name: String): String {
    val url = (baseUrl ?: default).trimEnd('/')
    val host = URI(url).host
    require(url.startsWith("https://") || host == "localhost" || host == "127.0.0.1") {
        "$name baseUrl must use https"
    }
    return url
}

private fun gitLabBaseUrl(target: PrCommentTarget): String =
    checkedBaseUrl(target.baseUrl, "https://gitlab.com/api/v4", "GitLab")

private fun gitLabProjectId(target: PrCommentTarget): String =
    target.repoOrProjectId.replace("/", "%2F")
