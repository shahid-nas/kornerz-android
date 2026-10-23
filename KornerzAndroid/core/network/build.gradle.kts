plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.kornerz.core.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:storage"))

    implementation(libs.androidx.core.ktx)

    // Kotlinx
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // OkHttp (used by SessionTokenService for the Vonage session-token handshake)
    api(libs.okhttp)

    // Vonage (used by VonageSessionController)
    api(libs.opentok.android.sdk)

    // Firebase (versions come from the BOM)
    implementation(platform(libs.firebase.bom))
    api(libs.firebase.auth.ktx)
    api(libs.firebase.firestore.ktx)
    api(libs.firebase.crashlytics.ktx)
    api(libs.firebase.messaging.ktx)
    api(libs.firebase.config.ktx)
    api(libs.firebase.functions.ktx)
    api(libs.firebase.storage.ktx)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Tests — the Firestore read path is exercised against a mocked
    // `FirebaseFirestore`; `TaskCompletionSource` stands in for the
    // async task the Firestore SDK returns.
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}

kapt {
    correctErrorTypes = true
}