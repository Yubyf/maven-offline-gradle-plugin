@file:Suppress("UnstableApiUsage")

package io.github.yubyf.mavenoffline

import io.github.yubyf.mavenoffline.fixtures.SCRIPT_TYPE_KTS

class MavenOfflinePluginKtsTest : MavenOfflinePluginTest() {
    override val script: Int = SCRIPT_TYPE_KTS
    override val root: String = "kts"
}