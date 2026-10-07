plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

// P0: core/model is intentionally empty. Domain types land in P1+.
// No dependencies yet; the module exists so feature modules can wire
// `implementation(project(":core:model"))` early without restructuring.