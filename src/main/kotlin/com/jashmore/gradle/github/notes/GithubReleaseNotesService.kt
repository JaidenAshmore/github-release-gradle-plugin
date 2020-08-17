package com.jashmore.gradle.github.notes

import org.eclipse.egit.github.core.Comment
import org.eclipse.egit.github.core.Issue
import org.eclipse.egit.github.core.Repository
import org.eclipse.egit.github.core.service.IssueService
import org.eclipse.egit.github.core.service.MilestoneService

/**
 * Function to render an issue in the release notes.
 */
typealias IssueRenderer = (issue: Issue, comments: List<Comment>) -> String

data class IssueGrouping(
        /**
         * The required header for this group of issues, e.g. `## Enhancements`.
         */
        val heading: String,

        /**
         * The renderer that will be able to render the release note information for the issue in the group.
         */
        val renderer: IssueRenderer,

        /**
         * The filter to determine if the issue should be present in this group.
         *
         * Note that each issue will only display in a single group and will prioritise the first group that it exists in.
         */
        val filter: (issue: Issue) -> Boolean
)

class GithubReleaseNotesService(private val repository: Repository,
                                private val milestoneService: MilestoneService,
                                private val issueService: IssueService) {

    /**
     * Create a repository's release notes markdown for the given milestone.
     *
     * @param milestoneVersion the milestone to render release notes for
     * @param issueGroups the definitions for groups of issues that should be rendered together, e.g. bug fixes, etc
     * @return the rendered markdown for the release notes
     */
    fun createReleaseNotes(milestoneVersion: String,
                           issueGroups: List<IssueGrouping>): String {
        val milestone = milestoneService.getMilestones(repository, "all")
                .firstOrNull { it.title == milestoneVersion }
                ?: throw IllegalArgumentException("No milestone found with name: $milestoneVersion")

        val issues = issueService.getIssues(repository, mutableMapOf(
                "milestone" to milestone.number.toString(),
                "state" to "closed"
        ))

        val issuesNotRendered: MutableSet<Issue> = issues.toMutableSet()

        return issueGroups
                .mapNotNull { grouping ->
                    val matchingIssues = issuesNotRendered.filter { grouping.filter(it) }

                    if (matchingIssues.isEmpty()) {
                        null
                    } else {
                        val renderedIssues = matchingIssues
                                .onEach { issuesNotRendered.remove(it) }
                                .joinToString("\n") { renderIssueInGroup(grouping, it) }

                        """
                        |${grouping.heading}
                        |
                        |$renderedIssues
                        """.trimMargin()
                    }
                }
                .joinToString("\n")
    }

    private fun renderIssueInGroup(issueGrouping: IssueGrouping, issue: Issue): String {
        val comments = issueService.getComments(repository, issue.number)
        return issueGrouping.renderer(issue, comments)
    }
}
