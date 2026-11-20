pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "MavenCentralFallback"
            url = uri("https://repo1.maven.org/maven2/")
            content { includeGroup("junit"); includeGroup("org.hamcrest") }
        }
    }
}
rootProject.name = "GPS-tool-new"
include(":app")
