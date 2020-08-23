package it.com.jashmore.gradle.github

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.egit.github.core.Comment
import org.eclipse.egit.github.core.Issue
import org.eclipse.egit.github.core.Label
import org.eclipse.egit.github.core.Milestone
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID

private val objectMapper = ObjectMapper()

class GithubReleaseNotesTaskTest {

    @TempDir
    lateinit var tempDir: File

    lateinit var buildFile: File

    lateinit var wireMockServer: WireMockServer

    @BeforeEach
    fun setup() {
        buildFile = File(tempDir, "build.gradle.kts")
        wireMockServer = WireMockServer()
        wireMockServer.start()
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun basicUseCase() {
        // arrange
        val gitHubUser = UUID.randomUUID().toString()
        val repoName = UUID.randomUUID().toString()

        buildFile.writeText("""
            import com.jashmore.gradle.github.gitHubRelease
            import org.eclipse.egit.github.core.client.GitHubClient
            
            plugins {
                id("com.jashmore.gradle.github.release")
            }
            
            gitHubRelease {
                gitHubUser = "$gitHubUser"
                repositoryName = "$repoName"
                gitHubClient = GitHubClient("localhost", ${wireMockServer.port()}, "http")
                milestoneVersion = "1.0.0"
                groupings = {
                    group {
                        heading = "# All issues"
                        filter = { true }
                        renderer = { issue, comments -> "- ${"$"}{issue.title}" }
                    }
                }
            }
        """.trimIndent())
        mockRepository(gitHubUser, repoName)
        mockMilestones(
                gitHubUser,
                repoName,
                Milestone().apply {
                    number = 15
                    title = "1.0.0"
                },
                Milestone().apply {
                    number = 21
                    title = "2.0.0"
                }
        )
        mockIssues(
                gitHubUser,
                repoName,
                15,
                Issue().apply {
                    number = 12
                    title = "My bug yo"
                    body = "My issue content"
                    labels = listOf()
                },
                Issue().apply {
                    number = 13
                    title = "My Second bug"
                    body = "My issue content"
                    labels = listOf()
                }
        )
        mockIssueComments(
                gitHubUser,
                repoName,
                12,
                Comment().apply {
                    id = 30
                    body = "My comment content"
                }
        )
        mockIssueComments(
                gitHubUser,
                repoName,
                13,
                Comment().apply {
                    id = 40
                    body = "My comment content"
                }
        )

        // act
        GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(tempDir)
                .withDebug(true)
                .withArguments(listOf("createReleaseNotes"))
                .build()

        // assert
        assertThat(tempDir.resolve("build").resolve("github").resolve("release-notes.md").readText())
                .isEqualTo("""
                    # All issues
        
                    - My bug yo
                    - My Second bug
                """.trimIndent())
    }

    @Test
    fun `missing milestone does not matter when not ran`() {
        // arrange
        buildFile.writeText("""
            import com.jashmore.gradle.github.notes.GithubReleaseNotesTask
            import com.jashmore.gradle.github.gitHubRelease
            import org.eclipse.egit.github.core.client.GitHubClient
            
            plugins {
                id("com.jashmore.gradle.github.release")
            }
            
            gitHubRelease {
                gitHubUser = "user"
                repositoryName = "repo"
                gitHubClient = GitHubClient("localhost", ${wireMockServer.port()}, "http")
                milestoneVersion = project.properties["milestone"] as String?
            }
        """.trimIndent())

        // act
        val result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(tempDir)
                .withDebug(true)
                .withArguments(listOf("tasks"))
                .build()

        // assert
        assertThat(result.output).isNotEmpty()
    }

    @Test
    fun complicatedUseCase() {
        // arrange
        val gitHubUser = UUID.randomUUID().toString()
        val repoName = UUID.randomUUID().toString()
        val firstIssueId = 10
        val secondIssueId = 11
        val thirdIssueId = 12

        buildFile.writeText("""
            import com.jashmore.gradle.github.notes.GithubReleaseNotesTask
            import com.jashmore.gradle.github.gitHubRelease
            import org.eclipse.egit.github.core.client.GitHubClient
            
            plugins {
                id("com.jashmore.gradle.github.release")
            }
            
            apply(plugin = "com.jashmore.gradle.github.release")
            
            gitHubRelease {
                gitHubUser = "$gitHubUser"
                repositoryName = "$repoName"
                gitHubClient = GitHubClient("localhost", ${wireMockServer.port()}, "http")
                outputFile = File(project.buildDir.resolve("myfile.md").toString())
                headerRenderer = { milestone -> "Milestone release: ${"$"}{milestone.description}" }
                groupings = {
                    group {
                        heading = ""${'"'}
                            |## Enhancements
                            |New features that have been added
                        ""${'"'}.trimMargin()
                        filter = { issue -> issue.labels.any { it.name == "enhancement" } }
                        renderer = { issue, comments ->
                            val releaseNotes = comments
                                .filter { it.body.contains("### Release Notes") }
                                .map { it.body.substringAfter("### Release Notes") }
                                .firstOrNull() ?: issue.body.substringAfter("### Release Notes")
            
                            ""${'"'}### ${"$"}{issue.title} [GH-${"$"}{issue.number}]
                            |
                            |${"$"}{releaseNotes.trim()}
                            |
                            ""${'"'}.trimMargin()
                        }
                    }
                    group {
                        heading = ""${'"'}
                            |## Bug Fixes
                            |Stomping out the bugs
                        ""${'"'}.trimMargin()
                        filter = { issue -> issue.labels.any { it.name == "bug" } }
                        renderer = { issue, _ -> "- [GH-${"$"}{issue.number}]: ${"$"}{issue.title}" }
                    }
                }
                footerRenderer = { milestone -> "Footer: ${"$"}{milestone.description}" }
                milestoneVersion = project.properties["milestone"] as String
            }
        """.trimIndent())
        mockRepository(gitHubUser, repoName)
        mockMilestones(
                gitHubUser,
                repoName,
                Milestone().apply {
                    number = 20
                    title = "1.0.0"
                    description = "First milestone"
                },
                Milestone().apply {
                    number = 21
                    title = "2.0.0"
                    description = "Second milestone"
                }
        )
        mockIssues(
                gitHubUser,
                repoName,
                20,
                Issue().apply {
                    number = firstIssueId
                    title = "Add ability to do feature"
                    body = """
                        |Let's add this new feature!
                        |
                        |### Release Notes
                        |Added a new feature to do stuff!
                    """.trimMargin()
                    labels = listOf(
                            Label().apply {
                                name = "enhancement"
                            }
                    )
                },
                Issue().apply {
                    number = secondIssueId
                    title = "Add ability to do second feature"
                    body = """
                        |Let's add some more features!
                    """.trimMargin()
                    labels = listOf(
                            Label().apply {
                                name = "enhancement"
                            }
                    )
                },
                Issue().apply {
                    number = thirdIssueId
                    title = "Fix this bug"
                    body = "It's not working"
                    labels = listOf(
                            Label().apply {
                                name = "bug"
                            }
                    )
                }
        )
        mockIssueComments(gitHubUser, repoName, firstIssueId)
        mockIssueComments(
                gitHubUser,
                repoName,
                secondIssueId,
                Comment().apply {
                    id = 40
                    body = """
                        ### Release Notes
                        Added the ability to do more things.
                    """.trimIndent()
                }
        )
        mockIssueComments(
                gitHubUser,
                repoName,
                thirdIssueId
        )

        // act
        GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(tempDir)
                .withDebug(true)
                .withArguments(listOf("createReleaseNotes", "-Pmilestone=1.0.0"))
                .build()

        // assert
        assertThat(tempDir.resolve("build").resolve("myfile.md").readText()).isEqualTo("""
            Milestone release: First milestone
            
            ## Enhancements
            New features that have been added

            ### Add ability to do feature [GH-$firstIssueId]
            
            Added a new feature to do stuff!
            
            ### Add ability to do second feature [GH-$secondIssueId]
            
            Added the ability to do more things.
            
            ## Bug Fixes
            Stomping out the bugs
            
            - [GH-$thirdIssueId]: Fix this bug
            
            Footer: First milestone
            """.trimIndent())
    }

    private fun mockRepository(gitHubUser: String, repoName: String) {
        wireMockServer.addStubMapping(stubFor(
                get(urlEqualTo("/api/v3/repos/$gitHubUser/$repoName")).willReturn(aResponse()
                        .withBody("""
                            {
                                "id": 10,
                                "node_id": "MDEwOlJlcG9zaXRvcnkxMjk2MjY5",
                                "name": "$repoName",
                                "full_name": "$gitHubUser/$repoName",
                                "owner": {
                                    "id": 1,
                                    "login": "$gitHubUser"
                                }
                            }
                        """.trimIndent())
                )
        ))
    }

    private fun mockMilestones(gitHubUser: String, repoName: String, vararg milestones: Milestone) {
        wireMockServer.addStubMapping(stubFor(
                get(urlEqualTo("/api/v3/repos/$gitHubUser/$repoName/milestones?state=all&per_page=100&page=1"))
                        .willReturn(aResponse().withBody(objectMapper.writeValueAsString(milestones)))
        ))
    }

    private fun mockIssues(gitHubUser: String, repoName: String, milestoneId: Int, vararg issues: Issue) {
        wireMockServer.addStubMapping(stubFor(
                get(urlEqualTo("/api/v3/repos/$gitHubUser/$repoName/issues?milestone=$milestoneId&state=closed&per_page=100&page=1"))
                        .willReturn(aResponse().withBody(objectMapper.writeValueAsString(issues)))
        ))
    }

    private fun mockIssueComments(gitHubUser: String, repoName: String, issueId: Int, vararg comments: Comment) {
        wireMockServer.addStubMapping(stubFor(
                get(urlEqualTo("/api/v3/repos/$gitHubUser/$repoName/issues/$issueId/comments?per_page=100&page=1"))
                        .willReturn(aResponse()
                                .withBody(objectMapper.writeValueAsString(comments)))
        ))
    }
}