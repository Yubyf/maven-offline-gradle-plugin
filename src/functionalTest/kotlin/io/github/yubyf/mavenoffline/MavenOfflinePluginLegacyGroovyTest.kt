@file:Suppress("UnstableApiUsage")

package io.github.yubyf.mavenoffline

import io.github.yubyf.mavenoffline.fixtures.SCRIPT_TYPE_LEGACY_GROOVY

class MavenOfflinePluginLegacyGroovyTest : MavenOfflinePluginTest() {
    override val script: Int = SCRIPT_TYPE_LEGACY_GROOVY
    override val root: String = "groovy"
}