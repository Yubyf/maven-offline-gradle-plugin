package io.github.yubyf.mavenoffline.consts

internal val DEP_HANDLER_CONFIGURATION_NAMES = setOf(
    "implementation",
    "testImplementation",
    "androidTestImplementation",
    "debugImplementation",
    "testDebugImplementation",
    "androidTestDebugImplementation",
    "releaseImplementation",
    "testReleaseImplementation",
    "androidTestReleaseImplementation",

    "api",
    "testApi",
    "androidTestApi",
    "debugApi",
    "testDebugApi",
    "androidTestDebugApi",
    "releaseApi",
    "testReleaseApi",
    "androidTestReleaseApi",

    "compileOnly",
    "testCompileOnly",
    "androidTestCompileOnly",
    "debugCompileOnly",
    "testDebugCompileOnly",
    "androidTestDebugCompileOnly",
    "releaseCompileOnly",
    "testReleaseCompileOnly",
    "androidTestReleaseCompileOnly",

    "runtimeOnly",
    "testRuntimeOnly",
    "androidTestRuntimeOnly",
    "debugRuntimeOnly",
    "testDebugRuntimeOnly",
    "androidTestDebugRuntimeOnly",
    "releaseRuntimeOnly",
    "testReleaseRuntimeOnly",
    "androidTestReleaseRuntimeOnly",

    "annotationProcessor",
    "testAnnotationProcessor",
    "androidTestAnnotationProcessor",
    "debugAnnotationProcessor",
    "testDebugAnnotationProcessor",
    "androidTestDebugAnnotationProcessor",
    "releaseAnnotationProcessor",
    "testReleaseAnnotationProcessor",
    "androidTestReleaseAnnotationProcessor",
)

internal const val DEP_CLASSPATH_CONFIGURATION = "classpath"

internal const val PREF_METADATA_NAME = "maven-metadata.xml"

internal const val PREF_POM_EXTENSION = ".pom"

internal var PREF_LIB_EXTRA_SUFFIXES = setOf(
    ".module",
    "-sources.jar",
    "-javadoc.jar"
)

internal var PREF_LIB_CHECKSUM_SUFFIXES = setOf(
    ".asc",
    ".md5",
    ".sha1",
    ".sha256",
    ".sha512",
)

const val PREF_TASK_NAME = "fetchMavens"
const val PREF_TASK_GROUP = "maven-offline"
const val PREF_TARGET_DIR = "/maven-offline"
const val PREF_LOG_DIR = "/outputs/logs"