package dev.ribod.storestudio.gradle.dsl

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Configuration options governing Continuous Integration (CI) quality gates and PR comments.
 */
public abstract class CiOptions @Inject constructor(objects: ObjectFactory) {

    /** Whether the CI quality gate task fails when visual changes / drift are detected. */
    public val failOnDrift: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    /** Whether the CI quality gate task fails when store compliance validation errors are found. */
    public val failOnValidationErrors: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)

    /** Whether to post or update sticky comments on pull requests. */
    public val postPrComment: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    /**
     * Unique sticky comment marker used to locate and update existing PR comments. Defaults to one
     * per project, so several apps in the same repository keep separate comments.
     */
    public val commentMarker: Property<String> = objects.property(String::class.java)
        .convention("<!-- STORE_STUDIO_DIFF_COMMENT -->")

    /** Optional explicit pull request or merge request number. Autodetected if empty. */
    public val prNumber: Property<String> = objects.property(String::class.java)

    /** GitHub Token. If empty, falls back to GITHUB_TOKEN environment variable. */
    public val githubToken: Property<String> = objects.property(String::class.java)

    /** GitLab Token. If empty, falls back to GITLAB_TOKEN environment variable. */
    public val gitlabToken: Property<String> = objects.property(String::class.java)
}
