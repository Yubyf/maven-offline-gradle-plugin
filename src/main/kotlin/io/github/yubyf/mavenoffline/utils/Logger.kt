package io.github.yubyf.mavenoffline.utils

import org.gradle.api.logging.Logger

internal fun Logger.indentLifecycle(message: String) = lifecycle("\t$message")

internal fun Logger.indentError(message: String) = error("\t$message")