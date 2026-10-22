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
        // P6 (task 1.3) — JitPack for `com.github.barteksc:android-pdf-viewer`
        maven { url = uri("https://jitpack.io") }
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
include(":feature:kornerz")
include(":feature:loops")
include(":feature:nooks")
include(":feature:videocall")
include(":feature:profile")
include(":feature:notifications")
include(":feature:community")
// P8 — premium / IAP. Imported only by `app` per `docs/architecture.md`
// §2.5; the shell reaches it through `SubscriptionRoute`.
include(":feature:subscription")