@file:Suppress("UnstableApiUsage")

package io.github.yubyf.mavenoffline

import com.google.common.truth.Truth.assertThat
import io.github.yubyf.mavenoffline.consts.PREF_TARGET_DIR
import io.github.yubyf.mavenoffline.consts.PREF_TASK_GROUP
import io.github.yubyf.mavenoffline.consts.PREF_TASK_NAME
import io.github.yubyf.mavenoffline.fixtures.*
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

abstract class MavenOfflinePluginTest {

    protected abstract val script: Int

    protected abstract val root: String?

    @get:Rule
    var testProjectDir = TemporaryFolder()

    @Test
    fun `tasks are registered for Android root project and automatically registered to each sub-project automatically`() {
        val project = AndroidProjectTemplate(
            script = script,
            subProjectCount = 4,
            applyPluginTo = AndroidProjectTemplate.APPLY_PLUGIN_TO_ROOT
        )
        FixtureRunner(testProjectDir, project, root).let { runner ->
            runner.run("tasks", "--group=$PREF_TASK_GROUP") {
                assertThat(output).contains("Maven-offline tasks")
                assertThat(output).contains(PREF_TASK_NAME)
            }
            project.subProjects.map { it.name }.forEach {
                runner.run(":$it:tasks", "--group=$PREF_TASK_GROUP") {
                    assertThat(output).contains("Maven-offline tasks")
                    assertThat(output).contains(PREF_TASK_NAME)
                }
            }
        }
    }

    @Test
    fun `task is registered for Android app project but not for other subprojects`() {
        val project = AndroidProjectTemplate(
            script = script,
            subProjectCount = 4,
            applyPluginTo = AndroidProjectTemplate.APPLY_PLUGIN_TO_APP
        )
        FixtureRunner(testProjectDir, project, root).let { runner ->
            runner.run("tasks", "--group=$PREF_TASK_GROUP") {
                assertThat(output).contains("Maven-offline tasks")
                assertThat(output).contains(PREF_TASK_NAME)
            }
            project.subProjects.map { it.name }.forEachIndexed { index, name ->
                runner.run(":$name:tasks", "--group=$PREF_TASK_GROUP") {
                    if (index == AndroidProjectTemplate.APPLY_PLUGIN_TO_APP) {
                        assertThat(output).contains("Maven-offline tasks")
                        assertThat(output).contains(PREF_TASK_NAME)
                    } else {
                        assertThat(output).doesNotContain("Maven-offline tasks")
                        assertThat(output).doesNotContain(PREF_TASK_NAME)
                    }
                }
            }
        }
    }

    @Test
    fun `don't fetch any maven when no extension is declared`() {
        val project = AndroidProjectTemplate(
            script = script,
            subProjectCount = 4,
            applyPluginTo = AndroidProjectTemplate.APPLY_PLUGIN_TO_ROOT
        )
        FixtureRunner(testProjectDir, project, root)
            .run(PREF_TASK_NAME) {
                assertThat(task(":$PREF_TASK_NAME")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
                project.subProjects.map { it.name }.forEachIndexed { _, name ->
                    assertThat(task(":$name:$PREF_TASK_NAME")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
                }
                assertThat(output).containsMatch("No effective repositories found in project :.+?, skip offline downloading")
            }
    }

    @Test
    fun `fetch dependencies of mavenCentral for root project`() {
        val project = AndroidProjectTemplate(
            script = script,
            subProjectCount = 2,
            applyPluginTo = AndroidProjectTemplate.APPLY_PLUGIN_TO_ROOT,
            rootExtension = EXTENSION_MAVEN_CENTRAL
        )
        FixtureRunner(testProjectDir, project, root)
            .run(PREF_TASK_NAME) {
                assertThat(task(":$PREF_TASK_NAME")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
                project.subProjects.map { it.name }.forEachIndexed { _, name ->
                    assertThat(task(":$name:$PREF_TASK_NAME")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
                }
                assertThat(output).contains("Downloaded dependencies: ${if (script == SCRIPT_TYPE_KTS) "14" else "9"}")
                assertThat(
                    File(root?.let { File(testProjectDir.root, it) } ?: testProjectDir.root,
                        PREF_TARGET_DIR).listFiles()
                        .isNullOrEmpty()
                ).isFalse()
            }
    }

    @Test
    fun `fetch dependencies of mavenCentral with target dir for root project`() {
        val project = AndroidProjectTemplate(
            script = script,
            subProjectCount = 2,
            applyPluginTo = AndroidProjectTemplate.APPLY_PLUGIN_TO_ROOT,
            rootExtension = EXTENSION_MAVEN_CENTRAL_WITH_TARGET
        )
        FixtureRunner(testProjectDir, project, root)
            .run(PREF_TASK_NAME) {
                assertThat(task(":$PREF_TASK_NAME")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
                project.subProjects.map { it.name }.forEachIndexed { _, name ->
                    assertThat(task(":$name:$PREF_TASK_NAME")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
                }
                assertThat(output).contains("Downloaded dependencies: ${if (script == SCRIPT_TYPE_KTS) "14" else "9"}")
                assertThat(
                    File(root?.let { File(testProjectDir.root, it) } ?: testProjectDir.root,
                        PREF_TARGET_DIR).listFiles()
                        .isNullOrEmpty()
                ).isTrue()
                assertThat(
                    File(root?.let { File(testProjectDir.root, it) } ?: testProjectDir.root,
                        "mavenOffline").listFiles()
                        .isNullOrEmpty()
                ).isFalse()
            }
    }

    @Test
    fun `fetch dependencies of mavenCentral with target dir and includeClasspath enabled for root project`() {
        val project = AndroidProjectTemplate(
            script = script,
            subProjectCount = 2,
            applyPluginTo = AndroidProjectTemplate.APPLY_PLUGIN_TO_ROOT,
            rootExtension = EXTENSION_MAVEN_CENTRAL_WITH_TARGET_INCLUDE_CLASSPATH
        )
        FixtureRunner(testProjectDir, project, root)
            .run(PREF_TASK_NAME) {
                assertThat(task(":$PREF_TASK_NAME")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
                project.subProjects.map { it.name }.forEachIndexed { _, name ->
                    assertThat(task(":$name:$PREF_TASK_NAME")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
                }
                assertThat(output).contains(
                    "Downloaded dependencies: ${
                        when (script) {
                            SCRIPT_TYPE_KTS -> "120"
                            SCRIPT_TYPE_GROOVY -> "96"
                            SCRIPT_TYPE_LEGACY_GROOVY -> "67"
                            else -> throw IllegalArgumentException("Unknown script type: $script")
                        }
                    }"
                )
                assertThat(
                    File(root?.let { File(testProjectDir.root, it) } ?: testProjectDir.root,
                        PREF_TARGET_DIR).listFiles()
                        .isNullOrEmpty()
                ).isTrue()
                assertThat(
                    File(root?.let { File(testProjectDir.root, it) } ?: testProjectDir.root,
                        "mavenOffline").listFiles()
                        .isNullOrEmpty()
                ).isFalse()
            }
    }

    @Test
    fun `fetch dependencies of mavenCentral with snapshot for root project`() {
        val project = AndroidProjectTemplate(
            script = script,
            subProjectCount = 2,
            applyPluginTo = AndroidProjectTemplate.APPLY_PLUGIN_TO_ROOT,
            rootExtension = EXTENSION_MAVEN_CENTRAL_WITH_SNAPSHOT,
            snapshot = true,
        )
        FixtureRunner(testProjectDir, project, root)
            .run(PREF_TASK_NAME) {
                assertThat(task(":$PREF_TASK_NAME")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
                project.subProjects.map { it.name }.forEachIndexed { _, name ->
                    assertThat(task(":$name:$PREF_TASK_NAME")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
                }
                assertThat(output).contains("Downloaded dependencies: ${if (script == SCRIPT_TYPE_KTS) "16" else "11"}")
                assertThat(
                    File(root?.let { File(testProjectDir.root, it) } ?: testProjectDir.root,
                        PREF_TARGET_DIR).listFiles()
                        .isNullOrEmpty()
                ).isFalse()
            }
    }
}