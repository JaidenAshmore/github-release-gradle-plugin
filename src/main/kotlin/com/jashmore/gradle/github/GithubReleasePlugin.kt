package com.jashmore.gradle.github

import com.jashmore.gradle.github.notes.GithubReleaseNotesTask
import com.jashmore.gradle.github.notes.GroupingsDsl
import org.eclipse.egit.github.core.client.GitHubClient
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import java.io.File

open class GithubReleaseExtension {
    /**
     * The username of the GitHub user that owns the repository, e.g. JaneDoe.
     */
    var gitHubUser: String? = null

    /**
     * The name of the repository, e.g. java-dynamic-sqs-listener.
     */
    var repositoryName: String? = null

    /**
     * The name of the milestone to obtain issues for, e.g. 4.0.0.
     */
    var milestoneVersion: String? = null

    /**
     * The client that should be used to communicate with the GitHub API.
     */
    var gitHubClient: GitHubClient? = null

    /**
     * Defines the groups of issues and how to render them.
     *
     * For example, this can be used to group enhancements or bug fixes into separate groups and for each issue render the issue in some way.
     */
    var groupings: (GroupingsDsl.() -> Unit) = { }

    /**
     * The file to generate the release notes for subsequent publishing to GitHub.
     */
    var outputFile: File? = null
}

class GithubReleasePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<GithubReleaseExtension>("gitHubRelease")
        project.afterEvaluate {
            project.tasks.register<GithubReleaseNotesTask>("createReleaseNotes") {
                gitHubUser = extension.gitHubUser ?: throw IllegalArgumentException("Required field 'gitHubUser' is not set")
                repositoryName = extension.repositoryName ?: throw IllegalArgumentException("Required field 'repositoryName' is not set")
                gitHubClient = extension.gitHubClient
                extension.milestoneVersion?.apply {
                    milestoneVersion = this
                }
                groupings = extension.groupings
                extension.outputFile?.apply {
                    outputFile = this
                }
            }
        }
    }
}

/**
 * Extension function for configuring the release information.
 *
 * Usage:
 *
 * ```
 * gitHubRelease {
 *     gitHubUser = "someUser"
 *     // other configuration properties here
 * }
 * ```
 */
fun Project.gitHubRelease(configure: GithubReleaseExtension.() -> Unit): Unit
        = (this as ExtensionAware).extensions.configure("gitHubRelease", configure)
