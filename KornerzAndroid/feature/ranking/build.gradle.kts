plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.kornerz.feature.ranking"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    /**
     * Mirrors `app`'s `env` dimension so AGP variant matching selects
     * `feature:ranking-dev` for `app:devDebug` and `feature:ranking-prod`
     * for `app:prodDebug` — the same seam `feature/subscription` uses.
     *
     * The Klouders leaderboard has no flavour-specific behaviour; the
     * dimension exists so the module exposes the `assembleDevDebug`
     * variant the phase's exit checklist builds.
     */
    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
        }
        create("prod") {
            dimension = "env"
        }
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

    implementation(libs.androidx.core.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Tests — `RankingViewModel` is a thin UDF over
    // `GamificationRepository`, so the whole load path runs on the JVM
    // with the repository mocked. No Robolectric, no instrumentation
    // harness.
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    // Compose UI tests — task 12.9. The bodies are skeletons only: CI
    // builds `assembleDevDebug` only and provisions no emulator, so the
    // cases are written out and `@Ignore`d rather than left unwritten.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.appcompat)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

kapt {
    correctErrorTypes = true
}
