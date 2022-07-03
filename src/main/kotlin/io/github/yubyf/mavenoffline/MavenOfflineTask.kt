package io.github.yubyf.mavenoffline

import io.github.yubyf.mavenoffline.consts.*
import io.github.yubyf.mavenoffline.utils.*
import kotlinx.coroutines.*
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
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
    abstract val artifacts: ListProperty<ResolvedArtifact>

    @get:Input
    abstract val offlineRepos: ListProperty<Repository>

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
        if (offlineRepos.get().isEmpty()) {
            logger.lifecycle("No effective repositories found in project :$projectName, skip offline downloading")
            printCompletedLog()
            return
        }
        logger.lifecycle("Start fetching mavens for project :$projectName...")
        val notFoundDependencies = mutableSetOf<String>()
        val downloadedDependencies = mutableSetOf<String>()
        val failedDependencies = mutableSetOf<String>()
        val depDeferreds = mutableSetOf<Deferred<*>>()

        suspend fun File.verifyLocalFile(baseUrl: String, credentials: PasswordCredentials): Boolean {
            if (exists()) {
                PREF_LIB_CHECKSUM_SUFFIXES.mapNotNull { suffix ->
                    PREF_LIB_CHECKSUM_ALGORITHM_MAP[suffix]?.let { it to baseUrl + suffix }
                }.fold(Pair("", "")) { acc, (algorithm, url) ->
                    if (acc.first.isNotEmpty() && acc.second.isNotEmpty()) return@fold acc
                    val checksum =
                        url.runCatching { downloadString(credentials.username, credentials.password) }
                            .getOrNull()
                    if (checksum != null) {
                        algorithm to checksum
                    } else {
                        acc
                    }
                }.takeIf {
                    it.first.isNotEmpty() && it.second.isNotEmpty()
                }?.let {
                    if (checkSum(it.first) == it.second) {
                        logger.lifecycle("File ${this.path} is up-to-date")
                        return true
                    }
                }
            }
            return false
        }

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
                val libExtraFileNames = PREF_LIB_EXTRA_SUFFIXES.map { suffix ->
                    "$basename$suffix"
                }
                val dir = File(targetDir.get(), path)
                offlineRepos.runCatching {
                    get().find { repo ->
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
                        File(dir, PREF_METADATA_NAME).apply {
                            if (verifyLocalFile(metaUrl, credentials)) {
                                return@apply
                            }
                            downloadToCacheFrom(metaUrl) { e ->
                                e.takeIf { it !is RemoteFileNotFoundException }?.let { throw e }
                            }.takeIf { it } ?: return@find false
                        }

                        val versionDir = File(dir, version)

                        // Download snapshot maven-metadata.xml
                        if (snapshot) {
                            val snapshotMetaUrl = "$url$path$version/$PREF_METADATA_NAME"
                            File(versionDir, PREF_METADATA_NAME).apply {
                                downloadToCacheFrom(snapshotMetaUrl) { e ->
                                    e.takeIf { it !is RemoteFileNotFoundException }?.let { throw e }
                                }.takeIf { it } ?: return@find false
                            }
                        }

                        // Download lib main files
                        val libDeferreds = mutableSetOf<Deferred<Boolean>>()
                        libMainFileNames.forEach { filename ->
                            val fileUrl = "$url$path$version/$filename"
                            libDeferreds.add(ioScope.async {
                                File(versionDir, filename).run {
                                    if (verifyLocalFile(fileUrl, credentials)) {
                                        true
                                    } else {
                                        downloadToCacheFrom(fileUrl) { e ->
                                            e.takeIf { it !is RemoteFileNotFoundException }?.let { throw e }
                                        }
                                    }
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
                        libExtraFileNames.forEach extras@{ filename ->
                            val fileUrl = "$url$path$version/$filename"
                            extraDeferreds.add(ioScope.async {
                                File(versionDir, filename).let { file ->
                                    if (file.verifyLocalFile(fileUrl, credentials)) {
                                        true
                                    } else {
                                        fileUrl.runCatching {
                                            downloadFile(file, credentials.username, credentials.password)
                                            true
                                        }.onFailure {
                                            file.takeIf { it.exists() }?.delete()
                                        }.getOrDefault(false)
                                    }
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
        }.distinct()
    }.onFailure {
        logger.indentError("Failed to extract dependencies for project :$projectName\n\t${it.message}")
    }.getOrNull()

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
}

data class Repository(
    val name: String = "",
    val url: String,
    val credentials: PasswordCredentials,
)

@OptIn(ExperimentalCoroutinesApi::class)
private val ioScope = CoroutineScope(Dispatchers.IO.limitedParallelism(10) + SupervisorJob())