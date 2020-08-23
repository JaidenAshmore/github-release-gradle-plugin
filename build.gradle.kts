
repositories {
    jcenter()
}

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.12.0"
}

group = "com.jashmore.gradle"
version = "0.0.2"

val assertJVersion: String by project
val eclipseGitHubConnectorVersion: String by project
val junitVersion: String by project
val wiremockVersion: String by project

dependencies {
    implementation(gradleKotlinDsl())
    api("org.eclipse.mylyn.github:org.eclipse.egit.github.core:$eclipseGitHubConnectorVersion")

    testImplementation(gradleTestKit())
    testImplementation("org.assertj:assertj-core:$assertJVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("com.github.tomakehurst:wiremock-jre8:$wiremockVersion")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("gitHubReleasePlugin") {
            id = "com.jashmore.gradle.github.release"
            implementationClass = "com.jashmore.gradle.github.GithubReleasePlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/JaidenAshmore"
    vcsUrl = "https://github.com/JaidenAshmore/github-release-gradle-plugin"
    tags = listOf("github")

    (plugins) {
        "gitHubReleasePlugin" {
            displayName = "GitHub Release Gradle Plugin"
            description = "Plugin for handling milestone releases in a GitHub repository."
            tags = listOf("release")
            version = (project.version as String)
        }
    }
}

fun updateBuildVersion(buildFile: File?, from: String, to: String) {
    if (buildFile == null) {
        throw RuntimeException("Required field 'buildFile' is missing")
    }

    val previousText = buildFile.readText()
    val newBuildFileText = previousText.replaceFirst(Regex("version\\s*=\\s*\"$from\""), "version = \"$to\"")
    if (previousText == newBuildFileText) {
        throw RuntimeException("Build file content did not change")
    }
    buildFile.writeText(newBuildFileText)
}

tasks.register("prepareReleaseVersion") {
    group = "Release"
    description = "Remove the SNAPSHOT suffix from the version"

    doLast {
        val projectVersion = project.version as String
        val nonSnapshotVersion = projectVersion.replace("-SNAPSHOT", "")

        println("Changing version $projectVersion to non-snapshot version $nonSnapshotVersion")

        updateBuildVersion(buildFile, from = projectVersion, to = nonSnapshotVersion)
    }
}

tasks.register("prepareNextSnapshotVersion") {
    group = "Release"
    description = "Update the version of the application to be the next SNAPSHOT version"

    doLast {
        val projectVersion = project.version as String
        val nonSnapshotVersion =  projectVersion.replace("-SNAPSHOT", "")
        val deliminator = if (nonSnapshotVersion.contains("-M")) "-M" else "."
        val lastNumber = nonSnapshotVersion.substringAfterLast(deliminator).toInt()
        val versionPrefix = nonSnapshotVersion.substringBeforeLast(deliminator)
        val nextSnapshotVersion = "$versionPrefix$deliminator${lastNumber + 1}-SNAPSHOT"

        println("Changing version $projectVersion to snapshot version $nextSnapshotVersion")

        updateBuildVersion(buildFile, from = projectVersion, to = nextSnapshotVersion)
    }
}

tasks.create("setupPluginUploadFromEnvironment") {
    doLast {
        val key = System.getenv("GRADLE_API_KEY")
        val secret = System.getenv("GRADLE_API_SECRET")

        if (key == null || secret == null) {
            throw GradleException("GRADLE_API_KEY and/or GRADLE_API_SECRET are not defined environment variables")
        }

        System.setProperty("gradle.publish.key", key)
        System.setProperty("gradle.publish.secret", secret)
    }
}

/**
 * Used to print out the current version so it can be saved as an output variable in a GitHub workflow.
 */
tasks.register("saveVersionForGitHub") {
    doLast {
        println("::set-output name=version::${project.version}")
    }
}
