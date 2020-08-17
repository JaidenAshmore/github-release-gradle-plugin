package com.jashmore.gradle.github.notes

import org.eclipse.egit.github.core.Issue
import org.eclipse.egit.github.core.client.GitHubClient
import org.eclipse.egit.github.core.service.IssueService
import org.eclipse.egit.github.core.service.MilestoneService
import org.eclipse.egit.github.core.service.RepositoryService
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

@DslMarker
annotation class ReleaseNotesDsl

@ReleaseNotesDsl
open class GroupingDsl {
    /**
     * The required header for this group of issues, e.g. `## Enhancements`.
     */
    lateinit var heading: String

    /**
     * The renderer that will be able to render the release note information for the issue in the group.
     */
    lateinit var renderer: IssueRenderer

    /**
     * The filter to determine if the issue should be present in this group.
     *
     * Note that each issue will only display in a single group and will prioritise the first group that it exists in.
     */
    lateinit var filter: (issue: Issue) -> Boolean
}

@ReleaseNotesDsl
class GroupingsDsl {
    var groups = mutableListOf<IssueGrouping>()

    /**
     * Add a new group into the groupings.
     */
    fun group(init: GroupingDsl.() -> Unit) {
        val grouping = GroupingDsl()
        grouping.init()
        groups.add(IssueGrouping(
                grouping.heading,
                grouping.renderer,
                grouping.filter
        ))
    }
}

private const val NO_MILESTONE: String = "No milestone version set"
/**
 * A task to generate the release notes from GitHub issues matching a milestone and creating a release report for
 * the milestone.
 */
open class GithubReleaseNotesTask : DefaultTask() {
    /**
     * The username of the GitHub user that owns the repository, e.g. JaneDoe.
     */
    @Input
    lateinit var gitHubUser: String

    /**
     * The name of the repository, e.g. java-dynamic-sqs-listener.
     */
    @Input
    lateinit var repositoryName: String

    /**
     * The name of the milestone to obtain issues for, e.g. 4.0.0.
     *
     * <p>This is given a default value because this will likely be set using a property and it should only need
     * to be set if a release notes is actually being generated.
     */
    @Input
    var milestoneVersion: String = NO_MILESTONE

    /**
     * The client that should be used to communicate with the GitHub API.
     */
    @Internal
    var gitHubClient: GitHubClient? = null

    /**
     * Defines the groups of issues and how to render them.
     *
     * For example, this can be used to group enhancements or bug fixes into separate groups and for each issue render the issue in some way.
     */
    @Input
    lateinit var groupings: (GroupingsDsl.() -> Unit)

    /**
     * The file to generate the release notes for subsequent publishing to GitHub.
     */
    @OutputFile
    var outputFile: File = project.buildDir.toPath().resolve("github").resolve("release-notes.md").toFile()

    @TaskAction
    fun generate() {
        if (milestoneVersion === NO_MILESTONE) {
            throw IllegalArgumentException("No milestone version has been set")
        }

        val client = gitHubClient ?: GitHubClient()

        val repositoryService = RepositoryService(client)
        val milestoneService = MilestoneService(client)
        val issueService = IssueService(client)

        val repository = repositoryService.getRepository(gitHubUser, repositoryName)

        val groupingsDsl = GroupingsDsl()
        groupingsDsl.groupings()

        val releaseNotes = GithubReleaseNotesService(repository, milestoneService, issueService)
                .createReleaseNotes(milestoneVersion, groupingsDsl.groups)

        logger.debug("Printing release notes to ${outputFile.toPath()}")
        outputFile.writeText(releaseNotes);
    }
}