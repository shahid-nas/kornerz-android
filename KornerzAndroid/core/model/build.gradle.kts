plugins {
    // Apply the Kotlin JVM plugin by id (not the version-catalog alias)
    // because every other module in the build applies
    // `kotlin.android` first, which loads the Kotlin Gradle Plugin onto
    // the classpath. Using `alias(libs.plugins.kotlin.jvm)` here re-
    // applies the same plugin with a version and triggers a
    // "plugin is already on the classpath with an unknown version"
    // error in some daemon / configuration-cache states. The id-only
    // form picks up the version from the Android module that loaded
    // the plugin first.
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
