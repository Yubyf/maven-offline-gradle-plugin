import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm") version "1.7.0"
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.18.0"
    signing
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    mavenLocal()
}

//region Publish
val snapshot: String by project
val pluginId: String by project
val mavenGroupId: String by project
val mavenArtifactId: String by project
val mavenVersion: String by project
val pomName: String by project
val pomDescription: String by project
val pomUrl: String by project
val pomLicenseName: String by project
val pomLicenseUrl: String by project
val developerName: String by project
val developerEmail: String by project

val ossrhUsername: String = properties["ossrh.username"] as String
val ossrhPassword: String = properties["ossrh.password"] as String

group = mavenGroupId
version = "$mavenVersion${if (snapshot.toBoolean()) "-SNAPSHOT" else ""}"

pluginBundle {
    website = pomUrl
    vcsUrl = pomUrl
    tags = listOf("maven", "offline")
    mavenCoordinates {
        groupId = mavenGroupId
        artifactId = mavenArtifactId
    }
}

gradlePlugin {
    plugins.create("mavenOffline") {
        id = pluginId
        displayName = pomName
        description = pomDescription
        implementationClass = "io.github.yubyf.mavenoffline.MavenOfflinePlugin"
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    afterEvaluate {
        publications {
            getByName<MavenPublication>("pluginMaven") {
                pom {
                    name.set(pomName)
                    artifactId = mavenArtifactId
                    description.set(pomDescription)
                    url.set(pomUrl)
                    licenses {
                        license {
                            name.set(pomLicenseName)
                            url.set(pomLicenseUrl)
                        }
                    }
                    developers {
                        developer {
                            name.set(developerName)
                            email.set(developerEmail)
                        }
                    }
                    scm {
                        url.set(pom.url.get())
                        connection.set("scm:git:${url.get()}.git")
                        developerConnection.set("scm:git:${url.get()}.git")
                    }
                }
            }
            getByName<MavenPublication>("mavenOfflinePluginMarkerMaven") {
                pom {
                    url.set(pomUrl)
                    licenses {
                        license {
                            name.set(pomLicenseName)
                            url.set(pomLicenseUrl)
                        }
                    }
                    developers {
                        developer {
                            name.set(developerName)
                            email.set(developerEmail)
                        }
                    }
                    scm {
                        url.set(pom.url.get())
                        connection.set("scm:git:${url.get()}.git")
                        developerConnection.set("scm:git:${url.get()}.git")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            name = "sonatype-snapshot"
            url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            credentials {
                username = ossrhUsername
                password = ossrhPassword
            }
        }
        maven {
            name = "sonatype-release"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = ossrhUsername
                password = ossrhPassword
            }
        }
    }

    publications {
        create<MavenPublication>("release") {
            from(components["java"])

            artifactId = mavenArtifactId

            pom {
                packaging = "jar"
                name.set(pomName)
                description.set(pomDescription)
                url.set(pomUrl)
                licenses {
                    license {
                        name.set(pomLicenseName)
                        url.set(pomLicenseUrl)
                    }
                }
                developers {
                    developer {
                        name.set(developerName)
                        email.set(developerEmail)
                    }
                }
                scm {
                    url.set(pom.url.get())
                    connection.set("scm:git:${url.get()}.git")
                    developerConnection.set("scm:git:${url.get()}.git")
                }
            }
        }
    }
}

signing {
    // Load gpg info in gradle.properties(global)
    useGpgCmd()
    afterEvaluate {
        sign(publishing.publications["release"])
        sign(publishing.publications["pluginMaven"])
        sign(publishing.publications["mavenOfflinePluginMarkerMaven"])
    }
}
//endregion

//region Test

val fixtureClasspath: Configuration by configurations.creating
tasks.pluginUnderTestMetadata {
    pluginClasspath.from(fixtureClasspath)
}

val functionalTestSourceSet = sourceSets.create("functionalTest") {
    compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath
    runtimeClasspath += output + compileClasspath
}

configurations[functionalTestSourceSet.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())

gradlePlugin.testSourceSets(functionalTestSourceSet)

val functionalTestTask = tasks.register<Test>("functionalTest") {
    failFast = true
    description = "Runs the functional tests."
    group = "verification"
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
}
tasks.check {
    dependsOn(functionalTestTask)
}
tasks.test {
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}
//endregion

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.1.3")
}