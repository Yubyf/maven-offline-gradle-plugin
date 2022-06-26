@file:Suppress("UnstableApiUsage")

package io.github.yubyf.mavenoffline

import io.github.yubyf.mavenoffline.fixtures.SCRIPT_TYPE_GROOVY

class MavenOfflinePluginGroovyTest : MavenOfflinePluginTest() {
    override val script: Int = SCRIPT_TYPE_GROOVY
    override val root: String = "legacy-groovy"
}