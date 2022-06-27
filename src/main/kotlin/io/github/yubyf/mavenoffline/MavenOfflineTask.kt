package io.github.yubyf.mavenoffline

import io.github.yubyf.mavenoffline.consts.PREF_LIB_CHECKSUM_SUFFIXES
import io.github.yubyf.mavenoffline.consts.PREF_LIB_EXTRA_SUFFIXES
import io.github.yubyf.mavenoffline.consts.PREF_METADATA_NAME
import io.github.yubyf.mavenoffline.consts.PREF_POM_EXTENSION
import io.github.yubyf.mavenoffline.utils.RemoteFileNotFoundException
import io.github.yubyf.mavenoffline.utils.downloadFile
import io.github.yubyf.mavenoffline.utils.indentError
import kotlinx.coroutines.*
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.*

abstract class MavenOfflineTask : DefaultTask() {

    private lateinit var projectName: String

    @get:Input
    abstract val targetDir: Property<File>

    @get:Input
    abstract val logDir: Property<File>

    @get:Input
    abstract val cacheDir: Property<File>

    @get:Input
    abstract val artifacts: Property<Array<ResolvedArtifact>>

    @get:Input
    abstract val repositories: Property<Array<MavenArtifactRepository>>

    @get:Input
    abstract val offlineMavens: Property<Array<URI>>

    @TaskAction
    fun taskAction() {
        projectName = project.name

        logger.lifecycle("Extracting artifacts for project :$projectName...")
        val dependencies = extractDependencies()
        if (dependencies == null || dependencies.size.also { logger.lifecycle("Found $it dependencies in project :$projectName") } == 0) {
            printCompletedLog()
            return
        }
        if (!targetDir.get().exists()) {
            targetDir.get().mkdirs()
        }
        logger.lifecycle("Download directory of project :$projectName: ${targetDir.get().absolutePath}/")
        logger.lifecycle("Extracting effective repositories for project :$projectName...")
        val effectiveRepos = extractEffectiveRepositories().also {
            if (it.isEmpty()) {
                logger.lifecycle("No effective repositories found in project :$projectName, skip offline downloading")
                printCompletedLog()
                return
            }
        }
        logger.lifecycle(
            "Effective repositories of project :$projectName - ${
                effectiveRepos.fold("") { acc, repo ->
                    "$acc, ${repo.name}"
                }.drop(2)
            }"
        )
        logger.lifecycle("Start fetching mavens for project :$projectName...")
        val notFoundDependencies = mutableSetOf<String>()
        val downloadedDependencies = mutableSetOf<String>()
        val failedDependencies = mutableSetOf<String>()
        val depDeferreds = mutableSetOf<Deferred<*>>()
        dependencies.forEach { (name, id, path, version, snapshotVersion, extension) ->
            depDeferreds.add(ioScope.async {
                val snapshot = snapshotVersion != null
                val metaPath = "$path$PREF_METADATA_NAME"
                val basename =
                    "$id-${
                        snapshotVersion?.let {
                            version.removeSuffix("-SNAPSHOT").plus("-").plus(it)
                        } ?: version
                    }"
                val libMainFileNames = setOf(PREF_POM_EXTENSION, ".$extension").map { suffix ->
                    "$basename$suffix"
                }
                val libMainFileChecksumNames = libMainFileNames.flatMap {
                    PREF_LIB_CHECKSUM_SUFFIXES.map { suffix ->
                        "$it$suffix"
                    }
                }
                val libExtraFileNames = PREF_LIB_EXTRA_SUFFIXES.map { suffix ->
                    "$basename$suffix"
                }
                val libExtraFileChecksumNames = libExtraFileNames.flatMap {
                    PREF_LIB_CHECKSUM_SUFFIXES.map { suffix ->
                        "$it$suffix"
                    }
                }
                val dir = File(targetDir.get(), path)
                effectiveRepos.runCatching {
                    find { repo ->
                        val url = repo.url
                        val credentials = repo.credentials
                        val metaUrl = "$url$metaPath"

                        // Key-value of target files and cache files
                        // Copy all cache files to target files only if all files downloaded successfully
                        val cacheFiles = mutableMapOf<File, File>()

                        suspend fun File.downloadToCacheFrom(url: String, block: ((Throwable) -> Unit)): Boolean =
                            url.runCatching {
                                File(cacheDir.get(), UUID.randomUUID().toString()).let { cache ->
                                    downloadFile(cache, credentials.username, credentials.password)
                                    cacheFiles[this@downloadToCacheFrom] = cache
                                }
                                true
                            }.onFailure { e ->
                                block.invoke(e)
                            }.getOrDefault(false)

                        // Download maven-metadata.xml
                        File(dir, PREF_METADATA_NAME).downloadToCacheFrom(metaUrl) { e ->
                            e.takeIf { it !is RemoteFileNotFoundException }?.let { throw e }
                        }.takeIf { it } ?: return@find false

                        val versionDir = File(dir, version)

                        // Download snapshot maven-metadata.xml
                        if (snapshot) {
                            val snapshotMetaUrl = "$url$path$version/$PREF_METADATA_NAME"
                            File(versionDir, PREF_METADATA_NAME).downloadToCacheFrom(snapshotMetaUrl) { e ->
                                e.takeIf { it !is RemoteFileNotFoundException }?.let { throw e }
                            }.takeIf { it } ?: return@find false
                        }

                        // Download lib main files
                        val libDeferreds = mutableSetOf<Deferred<Boolean>>()
                        libMainFileNames.forEach { filename ->
                            val fileUrl = "$url$path$version/$filename"
                            libDeferreds.add(ioScope.async {
                                File(versionDir, filename).downloadToCacheFrom(fileUrl) { e ->
                                    e.takeIf { it !is RemoteFileNotFoundException }?.let { throw e }
                                }
                            })
                        }
                        libDeferreds.awaitAll().all { it }.also { result ->
                            if (result) {
                                cacheFiles.forEach { (target, cache) ->
                                    target.parentFile.takeIf { !it.exists() }?.mkdirs()
                                    cache.copyTo(target, overwrite = true)
                                }
                                logger.lifecycle("Main files of [$name] fetched successfully")
                                downloadedDependencies.add(name)
                            }
                        }
                    }?.let { repo ->
                        val url = repo.url
                        val credentials = repo.credentials

                        // Download lib extra and checksum files
                        val extraDeferreds = mutableSetOf<Deferred<Boolean>>()
                        val versionDir = File(dir, version)
                        (libExtraFileNames + libMainFileChecksumNames + libExtraFileChecksumNames).forEach extras@{ filename ->
                            val fileUrl = "$url$path$version/$filename"
                            extraDeferreds.add(ioScope.async {
                                File(versionDir, filename).let { file ->
                                    fileUrl.runCatching {
                                        downloadFile(file, credentials.username, credentials.password)
                                        true
                                    }.onFailure {
                                        file.takeIf { it.exists() }?.delete()
                                    }.getOrDefault(false)
                                }
                            })
                        }
                        extraDeferreds.awaitAll().any { true }.takeIf { it }?.also {
                            logger.lifecycle("Extra files of [$name] fetched successfully")
                        }
                    } ?: notFoundDependencies.add(name)
                }.onFailure { e ->
                    logger.error("Failed to download $id", e)
                    failedDependencies.add(name)
                }
            })
        }
        runBlocking { depDeferreds.awaitAll() }
        // Clear cache files
        cacheDir.get().deleteRecursively()
        printCompletedLog(downloadedDependencies.size, failedDependencies.size, notFoundDependencies.size)
        val logFile = writeLogToFile(downloadedDependencies, failedDependencies, notFoundDependencies)
        logger.lifecycle("Detailed logs are output to ${logFile.absolutePath}")
    }

    private fun extractDependencies() = artifacts.runCatching {
        get().mapNotNull { artifact ->
            val name = artifact.id.componentIdentifier.displayName
            val pathSegments = name.split(":")
            if (pathSegments.size < 3) {
                logger.indentError("$name invalid.")
                return@mapNotNull null
            }
            val groupId = pathSegments[0]
            val artifactId = pathSegments[1]
            val version = pathSegments[2]
            val snapshotVersion = if (version.endsWith("-SNAPSHOT") && pathSegments.size > 3) {
                pathSegments[3]
            } else {
                null
            }
            val path = groupId.replace(
                ".",
                File.separatorChar.toString()
            ) + File.separatorChar + artifactId + File.separatorChar
            Dependency(name, artifact.name, path, version, snapshotVersion, artifact.extension)
        }
    }.onFailure {
        logger.indentError("Failed to extract dependencies for project :$projectName\n\t${it.message}")
    }.getOrNull()

    private fun extractEffectiveRepositories(): List<Repository> = offlineMavens.runCatching {
        get().mapNotNull {
            repositories.get().find { repo ->
                repo.url == it
            }?.let { repo ->
                val credentials = repo.credentials
                Repository(repo.name, repo.url.toString().takeIf { it.endsWith("/") } ?: "${repo.url}/", credentials)
            }
        }
    }.onFailure {
        logger.indentError("Failed to extract effective repositories for project :$projectName\n\t${it.message}")
    }.getOrDefault(emptyList())

    private fun printCompletedLog(downloadedCount: Int = 0, failedCount: Int = 0, notFoundCount: Int = 0) =
        logger.lifecycle(
            "Finished fetching mavens for project :$projectName\n" +
                    "\tDownloaded dependencies: $downloadedCount\n" +
                    "\tFailed to download dependencies: $failedCount\n" +
                    "\tNot found dependencies: $notFoundCount"
        )

    @Throws(IOException::class)
    private fun writeLogToFile(downloaded: Set<String>, failed: Set<String>, notFound: Set<String>): File =
        File(logDir.get(), "maven-offline-$projectName-report.txt").apply {
            if (!parentFile.exists()) parentFile.mkdirs()
            writeText(
                """
                |Downloaded dependencies: ${downloaded.size}
                |Failed to download dependencies: ${failed.size}
                |Not found dependencies: ${notFound.size}
                |
                |--- Details ---
                |
                |DOWNLOADED dependencies:
                |${"\t" + if (downloaded.isNotEmpty()) downloaded.joinToString("\n\t") else "No dependencies downloaded"}
                |
                |FAILED to download dependencies:
                |${"\t" + if (failed.isNotEmpty()) failed.joinToString("\n\t") else "No failed dependencies"}
                |
                |NOT FOUND dependencies:
                |${"\t" + if (notFound.isNotEmpty()) notFound.joinToString("\n\t") else "No not found dependencies"}
                """.trimMargin()
            )
        }

    private data class Dependency(
        val name: String,
        val id: String,
        val path: String,
        val version: String,
        val snapshotVersion: String? = null,
        val extension: String,
    )

    private data class Repository(
        val name: String = "",
        val url: String,
        val credentials: PasswordCredentials,
    )
}

private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())