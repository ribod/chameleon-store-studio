package dev.ribod.storestudio.core.ci

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.ktor.getApiResponse
import com.skydoves.sandwich.test.assertError
import com.skydoves.sandwich.test.assertExceptionMessage
import com.skydoves.sandwich.test.assertSuccess
import com.skydoves.sandwich.test.fakeError
import com.skydoves.sandwich.test.fakeException
import com.skydoves.sandwich.test.fakeSuccess
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress

class PrCommentClientTest {

    private val fakeApi = FakePrCommentApi()
    private val client = PrCommentClient(fakeApi)

    private val gitHubTarget = PrCommentTarget(
        platform = CiPlatform.GITHUB,
        token = "fake-token",
        repoOrProjectId = "octocat/hello",
        prNumber = 7,
    )

    private val gitLabTarget = PrCommentTarget(
        platform = CiPlatform.GITLAB,
        token = "fake-gitlab-token",
        repoOrProjectId = "12345",
        prNumber = 12,
    )

    @Test
    fun gitHubCreatesNewCommentWhenNoneExists() = runBlocking {
        fakeApi.getCommentsResponse = ApiResponse.fakeSuccess(emptyList())
        fakeApi.createCommentResponse = ApiResponse.fakeSuccess(
            RemoteComment(id = 42L, body = "new", url = "https://github.com/octo/pr#42"),
        )

        val response = client.postOrUpdateComment(gitHubTarget, "### Visual Diff Summary")

        response.assertSuccess {
            assertEquals(CommentAction.CREATED, data.action)
            assertEquals(42L, data.commentId)
            assertEquals("https://github.com/octo/pr#42", data.url)
        }
        assertEquals(1, fakeApi.createCommentCount)
        assertEquals(0, fakeApi.updateCommentCount)
        assertEquals(
            "### Visual Diff Summary\n\n<!-- STORE_STUDIO_DIFF_COMMENT -->",
            fakeApi.lastCreatedBody,
        )
    }

    @Test
    fun gitHubUpdatesExistingCommentWhenMarkerMatches() = runBlocking {
        val existingComment = RemoteComment(
            id = 99L,
            body = "old <!-- STORE_STUDIO_DIFF_COMMENT -->",
            url = "https://github.com/pr#99",
        )
        fakeApi.getCommentsResponse = ApiResponse.fakeSuccess(listOf(existingComment))
        fakeApi.updateCommentResponse = ApiResponse.fakeSuccess(
            RemoteComment(id = 99L, body = "updated", url = "https://github.com/pr#99"),
        )

        val response = client.postOrUpdateComment(gitHubTarget, "### Updated Diff Summary")

        response.assertSuccess {
            assertEquals(CommentAction.UPDATED, data.action)
            assertEquals(99L, data.commentId)
            assertEquals("https://github.com/pr#99", data.url)
        }
        assertEquals(0, fakeApi.createCommentCount)
        assertEquals(1, fakeApi.updateCommentCount)
        assertEquals(99L, fakeApi.lastUpdatedCommentId)
        assertEquals(
            "### Updated Diff Summary\n\n<!-- STORE_STUDIO_DIFF_COMMENT -->",
            fakeApi.lastUpdatedBody,
        )
    }

    @Test
    fun gitLabUpdatesExistingNoteWhenMarkerMatches() = runBlocking {
        val existingNote = RemoteComment(
            id = 555L,
            body = "note <!-- STORE_STUDIO_DIFF_COMMENT -->",
        )
        fakeApi.getCommentsResponse = ApiResponse.fakeSuccess(listOf(existingNote))
        fakeApi.updateCommentResponse = ApiResponse.fakeSuccess(
            RemoteComment(id = 555L, body = "updated note"),
        )

        val response = client.postOrUpdateComment(gitLabTarget, "### GitLab Diff Summary")

        response.assertSuccess {
            assertEquals(CommentAction.UPDATED, data.action)
            assertEquals(555L, data.commentId)
        }
        assertEquals(1, fakeApi.updateCommentCount)
        assertEquals(555L, fakeApi.lastUpdatedCommentId)
    }

    @Test
    fun collectPagesFollowsFullPagesAndStopsOnShortOne(): Unit = runBlocking {
        val requested = mutableListOf<Int>()
        val result = collectPages { page ->
            requested += page
            ApiResponse.fakeSuccess(List(if (page < 3) 100 else 5) { page })
        }

        result.assertSuccess { assertEquals(205, data.size) }
        assertEquals(listOf(1, 2, 3), requested)
    }

    @Test
    fun collectPagesPropagatesFailure(): Unit = runBlocking {
        collectPages<Int> { ApiResponse.fakeError("boom") }.assertError {
            assertEquals("boom", payload)
        }
    }

    @Test
    fun markerQuotedMidBodyIsNotAStickyComment() = runBlocking {
        val quoted = RemoteComment(5L, "see <!-- STORE_STUDIO_DIFF_COMMENT --> in docs")
        fakeApi.getCommentsResponse = ApiResponse.fakeSuccess(listOf(quoted))

        client.postOrUpdateComment(gitHubTarget, "### Summary")

        assertEquals(0, fakeApi.updateCommentCount)
        assertEquals(1, fakeApi.createCommentCount)
    }

    @Test
    fun createsNewCommentWhenUpdateReturns404() = runBlocking {
        val existing = RemoteComment(99L, "old <!-- STORE_STUDIO_DIFF_COMMENT -->")
        fakeApi.getCommentsResponse = ApiResponse.fakeSuccess(listOf(existing))
        fakeApi.updateCommentResponse = realError(404)
        fakeApi.createCommentResponse = ApiResponse.fakeSuccess(RemoteComment(100L, "new"))

        val response = client.postOrUpdateComment(gitHubTarget, "### Summary")

        response.assertSuccess {
            assertEquals(CommentAction.CREATED, data.action)
        }
        assertEquals(1, fakeApi.updateCommentCount)
        assertEquals(1, fakeApi.createCommentCount)
    }

    @Test
    fun gitHubRejectsPlainHttpBaseUrl(): Unit = runBlocking {
        val api = KtorPrCommentApi.create()
        val target = gitHubTarget.copy(baseUrl = "http://example.com/api")
        try {
            api.getComments(target)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("GitHub baseUrl must use https", e.message)
        } finally {
            api.close()
        }
    }

    @Test
    fun returnsSandwichErrorWhenGetCommentsFails() = runBlocking {
        fakeApi.getCommentsResponse = ApiResponse.fakeError("Bad credentials")

        val response = client.postOrUpdateComment(gitHubTarget, "### Summary")

        response.assertError {
            assertEquals("Bad credentials", payload)
        }
        assertEquals(0, fakeApi.createCommentCount)
        assertEquals(0, fakeApi.updateCommentCount)
    }

    @Test
    fun returnsSandwichExceptionOnNetworkFailure() = runBlocking {
        fakeApi.getCommentsResponse = ApiResponse.fakeException(message = "Network timeout")

        val response = client.postOrUpdateComment(gitHubTarget, "### Summary")

        response.assertExceptionMessage("Network timeout")
        assertEquals(0, fakeApi.createCommentCount)
        assertEquals(0, fakeApi.updateCommentCount)
    }
}

/** A Failure.Error carrying a real Ktor response, as the production API returns. */
private suspend fun realError(status: Int): ApiResponse<RemoteComment> {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") {
        it.sendResponseHeaders(status, -1)
        it.close()
    }
    server.start()
    val client = HttpClient(OkHttp)
    try {
        val response = client.getApiResponse<String>("http://127.0.0.1:${server.address.port}/")
        return ApiResponse.Failure.Error((response as ApiResponse.Failure.Error).payload)
    } finally {
        client.close()
        server.stop(0)
    }
}

private class FakePrCommentApi : PrCommentApi {
    var getCommentsResponse: ApiResponse<List<RemoteComment>> =
        ApiResponse.fakeSuccess(emptyList())
    var createCommentResponse: ApiResponse<RemoteComment> =
        ApiResponse.fakeSuccess(RemoteComment(id = 1L, body = ""))
    var updateCommentResponse: ApiResponse<RemoteComment> =
        ApiResponse.fakeSuccess(RemoteComment(id = 1L, body = ""))

    var lastCreatedBody: String? = null
    var lastUpdatedBody: String? = null
    var lastUpdatedCommentId: Long? = null
    var createCommentCount = 0
    var updateCommentCount = 0

    override suspend fun getComments(target: PrCommentTarget): ApiResponse<List<RemoteComment>> =
        getCommentsResponse

    override suspend fun createComment(
        target: PrCommentTarget,
        body: String,
    ): ApiResponse<RemoteComment> {
        createCommentCount++
        lastCreatedBody = body
        return createCommentResponse
    }

    override suspend fun updateComment(
        target: PrCommentTarget,
        commentId: Long,
        body: String,
    ): ApiResponse<RemoteComment> {
        updateCommentCount++
        lastUpdatedCommentId = commentId
        lastUpdatedBody = body
        return updateCommentResponse
    }
}
