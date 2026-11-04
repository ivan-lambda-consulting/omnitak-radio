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
    }
}

rootProject.name = "omnitak-radio"
include(":app")
// Plugin SDK and the non-map diagnostics reference plugin.
include(":plugins:plugin-sdk")
include(":plugins:example-diagnostics")
