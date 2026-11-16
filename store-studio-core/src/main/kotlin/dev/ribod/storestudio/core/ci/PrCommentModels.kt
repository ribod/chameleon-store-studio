package dev.ribod.storestudio.core.ci

import com.skydoves.sandwich.ApiResponse
import kotlinx.serialization.Serializable

/**
 * Supported Continuous Integration hosting platforms for pull request commenting.
 */
public enum class CiPlatform {
    GITHUB,
    GITLAB,
}

/**
 * Resulting mutation performed on the PR comment.
 */
public enum class CommentAction {
    CREATED,
    UPDATED,
    SKIPPED,
}

/**
 * Target pull request or merge request configuration for posting visual diff summaries.
 */
public data class PrCommentTarget(
    public val platform: CiPlatform,
    public val token: String,
    public val repoOrProjectId: String,
    public val prNumber: Int,
    public val commentMarker: String = "<!-- STORE_STUDIO_DIFF_COMMENT -->",
    public val baseUrl: String? = null,
) {
    override fun toString(): String =
        "PrCommentTarget(platform=$platform, token=***, repoOrProjectId=$repoOrProjectId, prNumber=$prNumber)"
}

/**
 * Outcome of a pull request comment post or update operation.
 */
public data class PrCommentResult(
    public val action: CommentAction,
    public val commentId: Long,
    public val url: String? = null,
)

/**
 * Remote comment representation returned by hosting platform APIs.
 */
public data class RemoteComment(
    public val id: Long,
    public val body: String,
    public val url: String? = null,
)

/**
 * Low-level API abstraction for reading and mutating pull request comments.
 */
public interface PrCommentApi : AutoCloseable {
    public suspend fun getComments(target: PrCommentTarget): ApiResponse<List<RemoteComment>>

    public suspend fun createComment(
        target: PrCommentTarget,
        body: String,
    ): ApiResponse<RemoteComment>

    public suspend fun updateComment(
        target: PrCommentTarget,
        commentId: Long,
        body: String,
    ): ApiResponse<RemoteComment>

    override fun close() {}
}

@Serializable
internal data class GitHubComment(val id: Long, val body: String, val html_url: String? = null)

@Serializable
internal data class GitHubCommentRequest(val body: String)

@Serializable
internal data class GitLabNote(val id: Long, val body: String)

@Serializable
internal data class GitLabNoteRequest(val body: String)
