@file:Suppress("UnstableApiUsage")

package io.github.yubyf.mavenoffline

import io.github.yubyf.mavenoffline.consts.*
import io.github.yubyf.mavenoffline.utils.indentError
import io.github.yubyf.mavenoffline.utils.indentLifecycle
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GradleInternal
import org.gradle.api.logging.Logger
import org.gradle.util.GradleVersion
import java.io.File
import java.util.regex.Pattern

@Suppress("unused")
abstract class MavenOfflinePlugin : Plugin<Project> {

    private lateinit var logger: Logger
    private lateinit var projectName: String

    override fun apply(project: Project) {
        projectName = project.name
        logger = project.logger

        if (project.isRoot()) {
            logger.lifecycle(
                "Apply $TAG to root project, sub projects - ${
                    project.subprojects.joinToString(",") { it.name }
                }"
            )
            logger.indentLifecycle(
                "apply $TAG for all subprojects" +
                        " since you are applying this plugin to the root project"
            )
            project.subprojects.forEach { it.plugins.apply(MavenOfflinePlugin::class.java) }
        } else {
            logger.lifecycle("Apply $TAG to project :${project.name}")
        }

        val extension = project.createMavenOfflineExtension()

        project.afterEvaluate {
            // Set up the DependencyHandlers after the project has been evaluated.
            it.setupDependencyHandlers(extension.includeClasspath)
            // Register tasks
            it.registerTask(extension)
        }
    }

    //region Extensions

    private fun ConfigurationContainer.filterDependencyHandlers(includeClasspath: Boolean): List<Configuration> =
        filter { configuration ->
            DEP_HANDLER_CONFIGURATION_NAMES.contains(configuration.name)
                    || (includeClasspath && DEP_CLASSPATH_CONFIGURATION == configuration.name)
        }

    private fun Project.setupDependencyHandlers(includeClasspath: Boolean) {
        if (isRoot()) return
        configurations.filterDependencyHandlers(includeClasspath).forEach { configuration ->
            runCatching { configuration.isCanBeResolved = true }.onFailure {
                logger.indentError("Failed to set up configuration :${configuration.name} - ${it.message}")
            }
        }
    }

    private fun Project.isRoot() = rootProject === this

    private fun Project.resolveArtifacts(includeClasspath: Boolean): List<ResolvedArtifact> =
        (if (isRoot()) buildscript.configurations else configurations).filterDependencyHandlers(includeClasspath)
            .flatMap { configuration ->
                configuration.runCatching {
                    resolvedConfiguration.let {
                        if (!it.hasError()) {
                            it.resolvedArtifacts.asIterable()
                        } else {
                            val lenientConfiguration = it.lenientConfiguration
                            logger.indentError(
                                "Failed to resolve artifacts - ${lenientConfiguration.unresolvedModuleDependencies}"
                            )
                            lenientConfiguration.artifacts.asIterable()
                        }
                    }
                }.onFailure {
                    logger.indentError("Resolve artifacts error - ${it.message}")
                }.getOrElse { emptyList() }
            }

    private fun Project.createMavenOfflineExtension() =
        extensions.create("mavenOffline", MavenOfflineExtension::class.java)

    private fun Project.registerTask(extension: MavenOfflineExtension) =
        tasks.register(PREF_TASK_NAME, MavenOfflineTask::class.java) { task ->
            logger.lifecycle("-----------")
            val artifacts = resolveArtifacts(extension.includeClasspath)
            var mavens = extension.mavens.toTypedArray()
            var targetDir =
                extension.targetPath?.let {
                    File(projectDir, it).validOrNull().also { file ->
                        if (file == null) logger.error("Invalid target path - $it")
                    }
                } ?: File(projectDir, PREF_TARGET_DIR)
            var logDir = File(buildDir, PREF_LOG_DIR)
            val repos = repositories.filterIsInstance<MavenArtifactRepository>().ifEmpty {
                if (GradleVersion.version(gradle.gradleVersion) >= GradleVersion.version("7.0")) {
                    settings.dependencyResolutionManagement.repositories.filterIsInstance<MavenArtifactRepository>()
                } else {
                    emptyList()
                }
            }

            if (!isRoot()) {
                rootProject.takeIf {
                    plugins.hasPlugin(MavenOfflinePlugin::class.java) || plugins.filterIsInstance<MavenOfflinePlugin>()
                        .isNotEmpty()
                }?.extensions?.findByType(
                    MavenOfflineExtension::class.java
                )?.let { rootExtension ->
                    if (rootExtension.mavens.isNotEmpty()) {
                        logger.lifecycle(
                            "Configuration \"maven\" of $TAG in project :$projectName is overridden by mavens from root project - ${rootExtension.mavens}"
                        )
                        mavens = rootExtension.mavens.toTypedArray()
                    }
                    targetDir =
                        rootExtension.targetPath?.let {
                            File(rootDir, it).validOrNull().also { file ->
                                if (file == null) logger.error("Invalid target path - $it")
                            }
                        } ?: File(rootDir, PREF_TARGET_DIR)
                    logDir = File(rootProject.buildDir, PREF_LOG_DIR)
                    logger.lifecycle(
                        "Configuration \"targetDir\" of $TAG in project :$projectName is overridden by target dir from root project - ${targetDir.path}"
                    )
                } ?: logger.lifecycle("root project maven offline extension not found")
            }

            logger.lifecycle("Extracting effective repositories for project :$projectName...")
            val offlineRepos: List<Repository> = mavens.runCatching {
                flatMap {
                    repos.filter { repo ->
                        repo.url == it || runCatching {
                            Pattern.compile(it.toString()).matcher(repo.url.toString()).matches()
                        }.getOrDefault(false)
                    }.map { repo ->
                        val credentials = repo.credentials
                        Repository(
                            repo.name,
                            repo.url.toString().takeIf { it.endsWith("/") } ?: "${repo.url}/",
                            credentials)
                    }
                }
            }.onFailure {
                logger.indentError("Failed to extract effective repositories for project :$projectName\n\t${it.message}")
            }.getOrDefault(emptyList())

            logger.lifecycle(
                "Effective repositories of project :$projectName - ${
                    if (offlineRepos.isEmpty()) "none"
                    else offlineRepos.joinToString(", ") { it.url }
                }"
            )

            task.group = PREF_TASK_GROUP
            task.artifacts.set(artifacts)
            task.offlineRepos.set(offlineRepos)
            task.targetDir.set(targetDir)
            task.cacheDir.set(File(buildDir, "intermediates/mavenoffline/cache/"))
            task.logDir.set(logDir)
        }

    //endregion
    companion object {
        private const val TAG = "maven-offline plugin"
    }
}

/**
 * Get gradle [org.gradle.api.internal.SettingsInternal] from [Project]
 * to access the [org.gradle.api.initialization.resolve.DependencyResolutionManagement.repositories] at any time.
 *
 * [Reference](https://github.com/gradle/gradle/issues/17295#issuecomment-1053620508)
 */
private val Project.settings: Settings
    get() = (gradle as GradleInternal).settings

fun File.validOrNull() = runCatching { also { canonicalPath } }.getOrNull()