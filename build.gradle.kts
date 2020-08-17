
repositories {
    jcenter()
}

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.12.0"
}

version = "0.0.1-SNAPSHOT"

dependencies {
    implementation(gradleKotlinDsl())
    api("org.eclipse.mylyn.github:org.eclipse.egit.github.core:2.1.5")

    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
    testImplementation("org.assertj:assertj-core:3.16.1")
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.27.0")
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
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
            version = "0.0.1"
        }
    }
}
