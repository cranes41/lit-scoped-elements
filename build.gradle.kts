import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = "io.github.litscoped"
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localIde = providers.gradleProperty("localIdePath").orNull
        if (localIde != null && file(localIde).exists()) {
            local(localIde)
        } else {
            webstorm(providers.gradleProperty("platformVersion"))
        }
        bundledPlugin("JavaScript")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        changeNotes = providers.fileContents(layout.projectDirectory.file("CHANGE_NOTES.html")).asText
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    buildSearchableOptions = false
}

// `./gradlew runIde` opens the sample Lit project in the sandbox IDE.
// With `-Pscreenshots`, a development-only tour (src/screenshots) runs on a copy of it instead, writes the
// Marketplace screenshots to build/screenshots, and exits. The published plugin never contains it.
val screenshots = providers.gradleProperty("screenshots").isPresent
if (screenshots) {
    sourceSets.main {
        kotlin.srcDir("src/screenshots/kotlin")
        resources.srcDir("src/screenshots/resources")
    }
}
// Outside the git repository, so the IDE doesn't report a nested VCS root.
val screenshotProject = provider { File(System.getProperty("java.io.tmpdir"), "lit-components") }
val prepareScreenshotProject by tasks.registering(Exec::class) {
    // rsync never descends into the node_modules link, so the real folder can't be touched.
    val source = layout.projectDirectory.dir("sample-project").asFile.absolutePath
    val target = screenshotProject.get().absolutePath
    commandLine(
        "sh", "-c",
        "mkdir -p '$target' && rsync -a --delete --exclude node_modules '$source/' '$target/' " +
            "&& ln -sfn '$source/node_modules' '$target/node_modules'",
    )
}
tasks.runIde {
    if (screenshots) {
        dependsOn(prepareScreenshotProject)
        args(screenshotProject.get().absolutePath)
        jvmArgs(
            "-Dlit.screenshots.dir=${layout.buildDirectory.dir("screenshots").get().asFile.absolutePath}",
            "-Didea.trust.all.projects=true",
            "-Dide.show.tips.on.startup.default.value=false",
            "-Djb.consents.confirmation.enabled=false",
            "-Djb.privacy.policy.text=<!--999.999-->",
            "-Didea.fatal.error.notification=disabled",
        )
    } else {
        args(layout.projectDirectory.dir("sample-project").asFile.absolutePath)
    }
}
