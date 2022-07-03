package io.github.yubyf.mavenoffline.consts

import io.github.yubyf.mavenoffline.utils.MessageDigestAlgorithm

internal val DEP_HANDLER_CONFIGURATION_NAMES = setOf(
    "implementation",
    "testImplementation",
    "androidTestImplementation",

    "api",
    "testApi",
    "androidTestApi",

    "compileOnly",
    "testCompileOnly",
    "androidTestCompileOnly",

    "runtimeOnly",
    "testRuntimeOnly",
    "androidTestRuntimeOnly",

    "annotationProcessor",
    "testAnnotationProcessor",
    "androidTestAnnotationProcessor",
)

internal const val DEP_CLASSPATH_CONFIGURATION = "classpath"

internal const val PREF_METADATA_NAME = "maven-metadata.xml"

internal const val PREF_POM_EXTENSION = ".pom"

internal var PREF_LIB_EXTRA_SUFFIXES = arrayOf(
    ".module",
    "-sources.jar",
    "-javadoc.jar"
)

internal var PREF_LIB_CHECKSUM_SUFFIXES = arrayOf(
    ".md5",
    ".sha1",
    ".sha256",
    ".sha512",
)

internal var PREF_LIB_CHECKSUM_ALGORITHM_MAP = mapOf(
    ".md5" to MessageDigestAlgorithm.MD5,
    ".sha1" to MessageDigestAlgorithm.SHA_1,
    ".sha256" to MessageDigestAlgorithm.SHA_256,
    ".sha512" to MessageDigestAlgorithm.SHA_512,
)

const val PREF_TASK_NAME = "fetchMavens"
const val PREF_TASK_GROUP = "maven-offline"
const val PREF_TARGET_DIR = "/maven-offline"
const val PREF_LOG_DIR = "/outputs/logs"