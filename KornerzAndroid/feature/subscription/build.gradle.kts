plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.kornerz.feature.subscription"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    /**
     * Mirrors `app`'s `env` dimension so AGP variant matching selects
     * `feature:subscription-dev` for `app:devDebug` and
     * `feature:subscription-prod` for `app:prodDebug`.
     *
     * The split exists because `com.kornerz.kornerz.dev` is not a
     * registered Play listing and there is no Play Billing sandbox —
     * Google ships no equivalent of Xcode's `.storekit` file — so without
     * it the plans screen can only ever render its error state until a
     * Play Console app exists. `src/dev` binds a simulated gateway,
     * `src/prod` binds the real `BillingClientApi`. See the
     * `play-billing` spec § "The billing gateway seam".
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

    implementation(libs.coil.compose)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Google Play Billing (P8) — the ONLY Play dependency in the app.
    // Confined to this module and `app` per the `android-build` spec.
    implementation(libs.android.billing)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    // Compose UI tests — task 15.7. The bodies are skeletons only: the
    // repository has no instrumentation harness today (no `androidTest`
    // source set existed before P8), so the cases are written out and
    // `@Ignore`d rather than left unwritten. See the KDoc on
    // `SubscriptionContentTest`.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.appcompat)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

kapt {
    correctErrorTypes = true
}