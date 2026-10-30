package com.kornerz.core.ui.device

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import com.kornerz.core.ui.R

/**
 * Device form factor helpers.
 *
 * The distinction is expressed as a resource boolean
 * (`R.bool.is_tablet`) so the platform itself resolves it from the
 * `sw600dp` qualifier: `false` in `res/values`, `true` in
 * `res/values-sw600dp`. Anything with a smallest width of 600dp or
 * more is treated as a tablet.
 *
 * Phones are portrait-only; tablets may rotate.
 */
object DeviceFormFactor {

    /** `true` when the current device is a tablet (smallest width >= 600dp). */
    fun isTablet(context: Context): Boolean =
        context.resources.getBoolean(R.bool.is_tablet)

    /** `true` when the current device is a phone (smallest width < 600dp). */
    fun isPhone(context: Context): Boolean = !isTablet(context)
}

/** Compose-friendly view of [DeviceFormFactor.isTablet]. */
@Composable
@ReadOnlyComposable
fun isTablet(): Boolean = DeviceFormFactor.isTablet(LocalContext.current)
