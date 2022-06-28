pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        mavenLocal()
    }
}

// `PluginMarkerMaven` task uses root project's name as the marked dependency's artifact id.
rootProject.name = "maven-offline-gradle-plugin"
