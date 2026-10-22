plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.kornerz.core.storage"
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

    implementation(libs.androidx.core.ktx)

    // DataStore (session storage)
    implementation(libs.androidx.datastore.preferences)

    // Kotlinx
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Tests — `SessionRepository` is exercised over a real file-backed
    // DataStore in a temp directory, which is the only way to assert the
    // atomicity and no-transient-`Anonymous` guarantees P8's `updateUser`
    // makes. No Robolectric: the DataStore file backend is pure JVM.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

kapt {
    correctErrorTypes = true
}