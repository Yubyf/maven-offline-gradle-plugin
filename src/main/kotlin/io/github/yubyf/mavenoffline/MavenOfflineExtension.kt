@file:Suppress("unused")

package io.github.yubyf.mavenoffline

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.reflect.TypeOf
import java.net.URI

open class MavenOfflineExtension {
    internal val mavens = mutableSetOf<URI>()
    internal val artifactFilters = mutableSetOf<Regex>()
    var targetPath: String? = null
    var includeClasspath = false

    fun maven(vararg uri: String) = uri.forEach { mavens.add(URI(it)) }

    fun artifactFilter(vararg pattern: String) =
        pattern.map { Regex(it) }.forEach { artifactFilters.add(it) }

    fun google() = ArtifactRepositoryContainer.GOOGLE_URL

    fun mavenCentral() = ArtifactRepositoryContainer.MAVEN_CENTRAL_URL

    fun jcenter() = "https://jcenter.bintray.com/"
}

fun Project.mavenOffline(configure: Action<MavenOfflineExtension>): Unit =
    (this as ExtensionAware).extensions.configure(TypeOf.typeOf(MavenOfflineExtension::class.java), configure)