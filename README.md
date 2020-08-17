# GitHub Release Gradle Plugin
Gradle Plugin to manage documenting the release of a GitHub repository.

## Getting Started

Apply the plugin using the Plugin DSL and configure the GitHub repository with the configuration for generating
the Release Notes.

```kotlin
import com.jashmore.gradle.github.gitHubRelease

plugins {
    id("com.jashmore.gradle.github.release") version "${insert.version}"
}

gitHubRelease {
    gitHubUser = "$gitHubUser"
    repositoryName = "$repoName"
    milestoneVersion = "$milestoneVersion"
    groupings = {
        group {
            heading = "# All issues"
            filter = { true }
            renderer = { issue, comments -> "- ${issue.title}" }
        }
    }
}
```

This will look in the GitHub repository for a milestone with the provided title and will use the closed issues in that
milestone to generate the release notes. In the example above there is only a single issue group and would generate
a report like:

```md
# All issues

- Fix a bug with encoding
- Fix a bug with caching
```

### More complicated example

This example shows creating multiple groups of issues that are divided between features and bugs.

```kotlin
import com.jashmore.gradle.github.gitHubRelease

plugins {
    id("com.jashmore.gradle.github.release") version "${insert.version}"
}

gitHubRelease {
    gitHubUser = "$gitHubUser"
    repositoryName = "$repoName"
    milestoneVersion = "$milestoneVersion"
    groupings = {
        group {
            heading = "## Enhancements"
            filter = { issue -> issue.labels.any { it.name == "enhancement" } }
            renderer = { issue, _ ->
                """
                |### ${issue.title} [GH-${issue.number}]
                |
                |${issue.body.trim()}
                |
                """.trimMargin()
            }
        }
        group {
            heading = "## Bug Fixes"
            filter = { issue -> issue.labels.any { it.name == "bug" } }
            renderer = { issue, _ -> "- [GH-${issue.number}]: ${issue.title}" }
        }
    }
}
```

### Milestone version from application version

You can use the version of the application to determine the milestone version. It can also be used to make it work
when you are running a SNAPSHOT version as well.

```kotlin
import com.jashmore.gradle.github.gitHubRelease

plugins {
    id("com.jashmore.gradle.github.release") version "${insert.version}"
}

version = "1.0.0-SNAPSHOT"

gitHubRelease {
    gitHubUser = "$gitHubUser"
    repositoryName = "$repoName"
    milestoneVersion = (version as String).replace("-SNAPSHOT", "")
    // other configuration...
}
```

### Using a GitHub Auth token

There is a limit to the number of anonymous requests that you can make to the GitHub API, you can also not access
private repositories. Therefore, you can configure your own [GitHub client](https://github.com/eclipse/egit-github) to
be able to communicate GitHub.

```kotlin
import org.eclipse.egit.github.core.client.GitHubClient

gitHubRelease {
    gitHubUser = "$gitHubUser"
    repositoryName = "$repoName"
    milestoneVersion = "$milestoneVersion"
    gitHubClient = GitHubClient().setOAuth2Token("$oauth2Token")
    groupings {
    }
}
```
