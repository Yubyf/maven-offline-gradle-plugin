package io.github.yubyf.mavenoffline.fixtures

import java.io.File

internal const val FIXTURE_ROOT_PROJECT_NAME = "maven-offline-fixture"

class AndroidProjectTemplate(
    val script: Int = SCRIPT_TYPE_KTS,
    subProjectCount: Int = 1,
    private val applyPluginTo: Int = APPLY_PLUGIN_TO_ROOT,
    private val rootExtension: String? = null,
    private val snapshot: Boolean = false,
    vararg subExtensions: String?,
) {
    private val kts: Boolean
        get() = script == SCRIPT_TYPE_KTS
    val subProjects: List<AndroidSubProjectTemplate> = List(subProjectCount) { index ->
        if (index == 0) {
            object : AppProjectTemplate() {
                override val script = this@AndroidProjectTemplate.script
                override val applyPlugin = applyPluginTo == index
                override val snapshot = this@AndroidProjectTemplate.snapshot
                override val extension = subExtensions.firstOrNull()
            }
        } else {
            object : LibProjectTemplate(index) {
                override val script = this@AndroidProjectTemplate.script
                override val applyPlugin = applyPluginTo == index
                override val snapshot = this@AndroidProjectTemplate.snapshot
                override val extension = subExtensions.getOrNull(index)
            }
        }
    }

    fun buildIn(root: File) {
        // settings.gradle
        root.resolve("settings.gradle${if (kts) ".kts" else ""}").writeText(
            buildSettingsFileContent(
                subProjectNames = subProjects.map { it.name },
                script = script
            )
        )

        // root build.gradle
        root.resolve("build.gradle${if (kts) ".kts" else ""}").writeText(
            buildRootBuildFileContent(script = script, extension = rootExtension)
        )

        // projects
        subProjects.forEach { project ->
            // build.gradle
            root.resolve(project.name).also { it.mkdirs() }
                .resolve("build.gradle${if (project.kts) ".kts" else ""}").writeText(project.buildFileContent)

            // src/main/AndroidManifest.xml
            root.resolve(File(project.name, "src/main/")).also { it.mkdirs() }.resolve("AndroidManifest.xml")
                .writeText(project.manifestFileContent)
        }
    }

    private fun buildSettingsFileContent(subProjectNames: Collection<String>, script: Int = SCRIPT_TYPE_KTS) =
        when (script) {
            SCRIPT_TYPE_KTS -> {
                """
                |pluginManagement {
                |    repositories {
                |        gradlePluginPortal()
                |        google()
                |        mavenCentral()
                |    }
                |}
                |
                |dependencyResolutionManagement {
                |    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                |    repositories {
                |        google()
                |        mavenCentral()
                |        ${if (snapshot) "maven { url = uri(\"https://oss.sonatype.org/content/repositories/snapshots\") }" else ""}
                |        ${if (snapshot) "maven { url = uri(\"https://s01.oss.sonatype.org/content/repositories/snapshots\") }" else ""}
                |    }
                |}
                |rootProject.name = "$FIXTURE_ROOT_PROJECT_NAME"
                |${subProjectNames.joinToString("\n") { "include(\":$it\")" }}
                """.trimMargin()
            }
            SCRIPT_TYPE_GROOVY -> {
                """
                |pluginManagement {
                |    repositories {
                |        gradlePluginPortal()
                |        google()
                |        mavenCentral()
                |    }
                |}
                |dependencyResolutionManagement {
                |    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                |    repositories {
                |        google()
                |        mavenCentral()
                |        ${if (snapshot) "maven { url 'https://oss.sonatype.org/content/repositories/snapshots' }" else ""}
                |        ${if (snapshot) "maven { url 'https://s01.oss.sonatype.org/content/repositories/snapshots' }" else ""}
                |    }
                |}
                |rootProject.name = "$FIXTURE_ROOT_PROJECT_NAME"
                |${subProjectNames.joinToString("\n") { "include ':$it'" }}
                """.trimMargin()
            }
            SCRIPT_TYPE_LEGACY_GROOVY -> {
                """
                |rootProject.name = "$FIXTURE_ROOT_PROJECT_NAME"
                |${subProjectNames.joinToString("\n") { "include ':$it'" }}
                """.trimMargin()
            }
            else -> throw IllegalArgumentException("Unknown script type: $script")
        }

    private fun buildRootBuildFileContent(script: Int = SCRIPT_TYPE_KTS, extension: String? = null) =
        when (script) {
            SCRIPT_TYPE_KTS -> {
                """
                |plugins {
                |    id("com.android.application").version("7.2.1").apply(false)
                |    id("com.android.library").version("7.2.1").apply(false)
                |    id("org.jetbrains.kotlin.android").version("1.7.0").apply(false)
                |    ${if (applyPluginTo == APPLY_PLUGIN_TO_ROOT) "id(\"io.github.yubyf.maven-offline\")" else ""}
                |}
                |${extension ?: ""}
                """.trimMargin()
            }
            SCRIPT_TYPE_GROOVY -> {
                """
                |plugins {
                |    id 'com.android.application' version '7.2.1' apply false
                |    id 'com.android.library' version '7.2.1' apply false
                |    ${if (applyPluginTo == APPLY_PLUGIN_TO_ROOT) "id 'io.github.yubyf.maven-offline'" else ""}
                |}
                |${extension ?: ""}
                """.trimMargin()
            }
            SCRIPT_TYPE_LEGACY_GROOVY -> {
                """
                |buildscript {
                |    repositories {
                |        google()
                |        mavenCentral()
                |    }
                |    dependencies {
                |        classpath 'com.android.tools.build:gradle:4.2.2'
                |    }
                |}
                |
                |plugins {
                |    ${if (applyPluginTo == -1) "id(\"io.github.yubyf.maven-offline\")" else ""}
                |}
                |
                |allprojects {
                |    repositories {
                |        google()
                |        mavenCentral()
                |        ${if (snapshot) "maven { url 'https://oss.sonatype.org/content/repositories/snapshots' }" else ""}
                |        ${if (snapshot) "maven { url 'https://s01.oss.sonatype.org/content/repositories/snapshots' }" else ""}
                |    }
                |}
                |${extension ?: ""}
                """.trimMargin()
            }
            else -> throw IllegalArgumentException("Unknown script type: $script")
        }

    abstract class AppProjectTemplate(
        override val name: String = "app",
        override val app: Boolean = true,
    ) : AndroidSubProjectTemplate()

    abstract class LibProjectTemplate(
        index: Int = 1,
        override val name: String = "lib${index}",
        override val app: Boolean = false,
    ) : AndroidSubProjectTemplate()

    abstract class AndroidSubProjectTemplate {
        abstract val name: String
        abstract val script: Int
        abstract val app: Boolean
        abstract val applyPlugin: Boolean
        abstract val extension: String?
        abstract val snapshot: Boolean

        val kts: Boolean
            get() = script == SCRIPT_TYPE_KTS

        val buildFileContent: String
            get() = when (script) {
                SCRIPT_TYPE_KTS -> ktsBuildFileContent
                SCRIPT_TYPE_GROOVY -> groovyBuildFileContent
                SCRIPT_TYPE_LEGACY_GROOVY -> legacyGroovyBuildFileContent
                else -> throw IllegalStateException("Unknown script type: $script")
            }

        private val ktsBuildFileContent: String
            get() =
                """
                |plugins {
                |    id("com.android.${if (app) "application" else "library"}")
                |    id("org.jetbrains.kotlin.android")
                |    ${if (applyPlugin) "id(\"io.github.yubyf.maven-offline\")" else ""}
                |}
                |
                |android {
                |    compileSdk = 32
                |    defaultConfig {
                |        minSdk = 21
                |        targetSdk = 31
                |    }
                |}
                |
                |${extension ?: ""}
                |
                |dependencies {
                |    ${if (snapshot) "implementation(\"com.github.bumptech.glide:glide:4.7.0-SNAPSHOT\")" else ""}
                |    implementation("com.google.guava:guava:31.1-android")
                |    testImplementation("junit:junit:4.13.2")
                |}
                """.trimMargin()

        private val groovyBuildFileContent: String
            get() =
                """
                |plugins {
                |    id 'com.android.${if (app) "application" else "library"}'
                |    ${if (applyPlugin) "id 'io.github.yubyf.maven-offline'" else ""}
                |}
                |
                |android {
                |    compileSdk 32
                |
                |    defaultConfig {
                |        minSdk 21
                |        targetSdk 32
                |    }
                |}
                |
                |${extension ?: ""}
                |
                |dependencies {
                |    ${if (snapshot) "implementation 'com.github.bumptech.glide:glide:4.7.0-SNAPSHOT'" else ""}
                |    implementation 'com.google.guava:guava:31.1-android'
                |    testImplementation 'junit:junit:4.13.2'
                |}
                """.trimMargin()

        private val legacyGroovyBuildFileContent: String
            get() =
                """
                |plugins {
                |    id 'com.android.${if (app) "application" else "library"}'
                |    ${if (applyPlugin) "id 'io.github.yubyf.maven-offline'" else ""}
                |}
                |
                |android {
                |    compileSdkVersion 32
                |
                |    defaultConfig {
                |        minSdkVersion 21
                |        targetSdkVersion 32
                |    }
                |}
                |
                |${extension ?: ""}
                |
                |dependencies {
                |    ${if (snapshot) "implementation 'com.github.bumptech.glide:glide:4.7.0-SNAPSHOT'" else ""}
                |    implementation 'com.google.guava:guava:31.1-android'
                |    testImplementation 'junit:junit:4.13.2'
                |}
                """.trimMargin()

        val manifestFileContent: String
            get() =
                """
                |<?xml version="1.0" encoding="utf-8"?>
                |<manifest package="io.github.yubyf.mavenoffline.${name}" />
                """.trimMargin()
    }

    companion object {
        const val APPLY_PLUGIN_TO_ROOT = -1
        const val APPLY_PLUGIN_TO_APP = 0
    }
}

internal const val SCRIPT_TYPE_KTS = 0
internal const val SCRIPT_TYPE_GROOVY = 1
internal const val SCRIPT_TYPE_LEGACY_GROOVY = 2

const val EXTENSION_MAVEN_CENTRAL = """
mavenOffline {
    maven(mavenCentral())
}
"""

const val EXTENSION_MAVEN_CENTRAL_SNAPSHOT_REGEX = """
mavenOffline {
    maven("https://.+?.sonatype.org/content/repositories/snapshots")
}
"""

const val EXTENSION_MAVEN_CENTRAL_SNAPSHOT_EXCLUDE_S01_REGEX = """
mavenOffline {
    maven("https://((?!s01).).+?.sonatype.org/content/repositories/snapshots")
}
"""

const val EXTENSION_MAVEN_CENTRAL_WITH_SNAPSHOT = """
mavenOffline {
    maven(mavenCentral(), "https://oss.sonatype.org/content/repositories/snapshots")
}
"""

const val EXTENSION_MAVEN_CENTRAL_WITH_TARGET = """
mavenOffline {
    maven(mavenCentral())
    targetPath = "/mavenOffline/"
}
"""

const val EXTENSION_MAVEN_CENTRAL_WITH_TARGET_INCLUDE_CLASSPATH = """
mavenOffline {
    maven(mavenCentral())
    targetPath = "/mavenOffline/"
    includeClasspath = true
}
"""