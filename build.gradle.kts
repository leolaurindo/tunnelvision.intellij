import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "io.github.leolaurindo"
version = "0.1.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 2025.2.x is the last IntelliJ IDEA Community build: from 2025.3 the `intellijIdea`
        // accessor resolves to Ultimate, whose sandbox cannot start without a license.
        intellijIdea("2025.2.6.2")

        // Java PSI and the word source need nothing beyond the platform. Kotlin and
        // JavaScript/TypeScript stay optional dependencies in the descriptors; their plugins are
        // only needed here to compile and test the structural adapters.
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("JavaScript")

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
        // There is no Kotlin test framework artifact for build 252: the bundled Kotlin plugin
        // above is what makes .kt fixtures parse in tests.
        testFramework(TestFrameworkType.Plugin.JavaScript)
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test-junit"))
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "io.github.leolaurindo.tunnelvision"
        name = "TunnelVision"
        version = project.version.toString()

        description = """
            Focus on one thing at a time.

            TunnelVision dims everything but the symbol under the caret and the lines that
            reference it, scoped to the enclosing function by default.
        """.trimIndent()

        vendor {
            name = "Leonardo Laurindo"
            url = "https://github.com/leolaurindo"
        }

        changeNotes = """
            <ul>
              <li>Fixed dimming being overridden by semantic syntax colors.</li>
              <li>Default focus now retains matching lines and symbols; statement and scope-head context remain optional.</li>
              <li>Hide the function-scope control in whole-file word mode.</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = "252"
            untilBuild = provider { null }
        }
    }

    // Indexes the settings and color pages for the IDE's Settings search. It boots a headless IDE
    // for about a minute, so it is not part of `test`.
    buildSearchableOptions = true

    publishing {
        token = providers.gradleProperty("publishToken")
    }

    instrumentCode = false
}

tasks {
    test {
        systemProperty("idea.log.path", layout.buildDirectory.dir("idea-log").get().asFile.path)
    }
}
