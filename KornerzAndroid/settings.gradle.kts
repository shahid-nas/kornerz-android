pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "KornerzAndroid"

include(":app")
include(":core:model")
include(":core:network")
include(":core:storage")
include(":core:ui")
include(":feature:splash")
include(":feature:shell")
include(":feature:auth")
include(":feature:home")