
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
version = "0.0.1-SNAPSHOT"

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
