package com.kornerz.core.ui.device

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Reads the installed app's marketing version and build number from
 * the platform's [PackageManager].
 *
 * This is the Android counterpart of the iOS `AppInfo` helper
 * (`kornerz/Kornerz/Helper/AppInfo.swift`), which reads
 * `CFBundleShortVersionString` and `CFBundleVersion` from the main
 * bundle. Android exposes the same pair as `versionName` and
 * `versionCode`, so the mapping is one-to-one:
 *
 * | iOS | Android |
 * |---|---|
 * | `CFBundleShortVersionString` | `PackageInfo.versionName` |
 * | `CFBundleVersion` | `PackageInfo.longVersionCode` |
 *
 * The lookups are wrapped so a missing or unreadable package info
 * degrades to the `"—"` placeholder rather than crashing the screen
 * that renders it.
 */
object AppVersionInfo {

    /** The placeholder shown when the version cannot be resolved. */
    const val UNKNOWN: String = "—"

    /**
     * The marketing version of the installed app (e.g. `"2.0"`), or
     * [UNKNOWN] when it cannot be read.
     */
    fun versionName(context: Context): String =
        packageInfo(context)?.versionName?.takeIf { it.isNotBlank() } ?: UNKNOWN

    /**
     * The build number of the installed app (e.g. `"181"`), or
     * [UNKNOWN] when it cannot be read.
     */
    fun buildNumber(context: Context): String =
        packageInfo(context)?.longVersionCode?.toString() ?: UNKNOWN

    private fun packageInfo(context: Context): PackageInfo? = runCatching {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    }.getOrNull()
}
