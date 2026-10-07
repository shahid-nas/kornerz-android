package com.kornerz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kornerz.core.ui.theme.KornerzTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity host. Compose drives the entire UI from here, so the
 * AndroidX activity contains no logic beyond wiring Compose to the
 * window. Edge-to-edge is enabled so the bottom-bar respects gesture
 * navigation insets on API 26+.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KornerzTheme {
                AppRoot()
            }
        }
    }
}