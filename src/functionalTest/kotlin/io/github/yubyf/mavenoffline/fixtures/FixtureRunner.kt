package io.github.yubyf.mavenoffline.fixtures

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import org.junit.rules.TemporaryFolder
import java.io.File

class FixtureRunner(
    private val dir: TemporaryFolder,
    projects: AndroidProjectTemplate,
    rootDir: String? = null,
) {
    private val root = rootDir?.let { dir.root.resolve(it).apply { mkdirs() } } ?: dir.root

    init {
        dir.buildFixture(projects, root)
    }

    fun run(
        vararg arguments: String,
        block: BuildResult.() -> Unit,
    ) {
        block(
            runner.withProjectDir(root)
                .withArguments(mutableListOf("--stacktrace", "--info", "--build-cache") + arguments)
                .withPluginClasspath()
                .withGradleVersion(GradleVersion.current().version)
                .build()
        )
    }
}

private val runner = GradleRunner.create().withPluginClasspath().withDebug(true)

private fun TemporaryFolder.buildFixture(project: AndroidProjectTemplate, rootDir: File? = null) =
    project.buildIn(rootDir ?: root)