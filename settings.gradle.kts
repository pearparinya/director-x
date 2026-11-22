pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Fallback for Kotlin artifacts needed by Android Gradle Plugin's
        // Unified Test Platform (connectedDebugAndroidTest on the emulator).
        maven {
            url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
            content { includeGroup("org.jetbrains.kotlin") }
        }
    }
}
rootProject.name = "DirectorX"
include(":app")
