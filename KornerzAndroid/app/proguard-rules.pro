# Baseline ProGuard / R8 rules for the Kornerz Android app.
#
# P10 turns R8 on for the release build type, so these rules are now
# *exercised* rather than aspirational: `./gradlew :app:assembleProdRelease`
# plus a clean install-and-launch of the minified APK is what proves they
# are right.

# --- Compose runtime ---
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }

# --- Hilt / Dagger generated classes ---
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.** { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# --- Firebase ---
# The Firebase SDKs look their classes up by name at runtime (component
# discovery, persistence, Crashlytics symbolication), so R8 cannot see
# the references.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

# --- Vonage video SDK (P9) ---
# The SDK instantiates its session and media classes from native code
# and by name, so R8 cannot see the references either.
-keep class com.opentok.android.** { *; }
-keep class com.opentok.client.** { *; }
-keep class com.opentok.impl.** { *; }
-keep class com.opentok.otc.** { *; }
-dontwarn com.opentok.**

# --- Kotlin reflection (used by Hilt and Firebase) ---
-keep class kotlin.reflect.jvm.internal.** { *; }
-keep class kotlin.Metadata { *; }

# --- kotlinx-serialization ---
# The generated `Companion.serializer()` calls are resolved
# reflectively, and the plugin's generated `$serializer` classes are
# looked up by name. This is the upstream ruleset.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Keep `Companion` object fields of serializable classes.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of
# serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- The `@Serializable` model classes ---
# `core/model` holds the wire types the app decodes: the notification
# document, the session record, the gamification document and the
# community. They are small, and keeping them whole removes the chance
# of a stripped `@SerialName` drifting out of sync with the backend.
-keep class com.kornerz.core.model.** { *; }
-keep class com.kornerz.core.storage.session.** { *; }

# --- Moshi / kotlinx-serialization parity (safe to keep even if unused in P0) ---
-dontwarn org.jetbrains.annotations.**
-keep class kotlinx.coroutines.** { *; }

# --- AppCompat / Material ---
-keep class androidx.appcompat.** { *; }
-dontwarn androidx.appcompat.**
