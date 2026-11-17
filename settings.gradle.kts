pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Fallback for the JVM-only JSON test dependency when Maven Central rate-limits CI.
        maven {
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
            content { includeGroup("org.json") }
        }
        mavenCentral()
    }
}
rootProject.name = "DirectorX"
include(":app")
