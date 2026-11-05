plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.kornerz.feature.shell"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        // `feature:ranking` declares the `env` flavor dimension, mirroring
        // `app` and `feature:subscription`, so that
        // `:feature:ranking:assembleDevDebug` exists as the phase's
        // exit-checklist command. The shell has no flavour-specific
        // behaviour of its own, so it declares no dimension and pins the
        // selection instead — without this, AGP cannot choose between
        // `feature:ranking`'s `devDebug` and `prodDebug` variants.
        missingDimensionStrategy("env", "dev")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:storage"))
    implementation(project(":feature:home"))
    implementation(project(":feature:kornerz"))
    implementation(project(":feature:loops"))
    implementation(project(":feature:nooks"))
    implementation(project(":feature:videocall"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:notifications"))
    implementation(project(":feature:community"))
    // P10 — the shell owns the tab graph, so it is the module that
    // reaches the Klouders leaderboard. This is the documented
    // `feature.shell` tab-graph seam (`android-build` spec).
    implementation(project(":feature:ranking"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
}

kapt {
    correctErrorTypes = true
}