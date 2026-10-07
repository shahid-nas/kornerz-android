# Baseline ProGuard / R8 rules for the Kornerz Android app.
# P0 keeps minification OFF, but the rules are in place for future phases.

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
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

# --- Kotlin reflection (used by Hilt and Firebase) ---
-keep class kotlin.reflect.jvm.internal.** { *; }
-keep class kotlin.Metadata { *; }

# --- Moshi / kotlinx-serialization parity (safe to keep even if unused in P0) ---
-dontwarn org.jetbrains.annotations.**
-keep class kotlinx.coroutines.** { *; }

# --- AppCompat / Material ---
-keep class androidx.appcompat.** { *; }
-dontwarn androidx.appcompat.**