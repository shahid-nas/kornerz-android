plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.kornerz.kornerz"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kornerz.kornerz"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Kornerz Dev")
        }
        create("prod") {
            dimension = "env"
            resValue("string", "app_name", "Kornerz")
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        getByName("release") {
            // P10 (task 11.9) — R8 minification and resource shrinking
            // are ON for the release build type, so `proguard-rules.pro`
            // is actually exercised. The debug build type stays
            // unshrunk: it is the phase's install-and-launch artefact
            // and readable stack traces matter while iterating.
            //
            // The signing configuration is unchanged — P10 does not
            // introduce an upload key (`docs/phases.md:407` open
            // question 3), so the minified APK is install-and-launch
            // verified only, not uploadable to Play.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
            )
        }
    }
}

dependencies {
    // Compose BOM keeps all androidx.compose.* libraries on a tested set.
    implementation(platform(libs.androidx.compose.bom))

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // AndroidX core / lifecycle / activity
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Images / video (ExoPlayer unused in P0)
    implementation(libs.coil.compose)
    implementation(libs.androidx.media3.exoplayer)

    // Firebase (versions come from the BOM)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.crashlytics.ktx)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.config.ktx)
    implementation(libs.firebase.functions.ktx)

    // Kotlinx
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Google Play Billing (P8). `app` carries it for the Play-services
    // license-check surface and manifest merging; the wrapper itself
    // lives in `feature/subscription`. Deliberately not added to
    // `core/*` so the Play dependency's blast radius stays at two
    // modules (`android-build` spec).
    implementation(libs.android.billing)

    // Internal modules
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:storage"))
    implementation(project(":core:ui"))
    implementation(project(":feature:splash"))
    implementation(project(":feature:shell"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:home"))
    implementation(project(":feature:kornerz"))
    implementation(project(":feature:loops"))
    implementation(project(":feature:nooks"))
    implementation(project(":feature:videocall"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:notifications"))
    implementation(project(":feature:community"))
    implementation(project(":feature:subscription"))
    // P10 — the Klouders leaderboard module.
    implementation(project(":feature:ranking"))
}

kapt {
    correctErrorTypes = true
}