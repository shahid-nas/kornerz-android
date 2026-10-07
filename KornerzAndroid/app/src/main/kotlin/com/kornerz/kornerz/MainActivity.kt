package com.kornerz.kornerz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kornerz.core.storage.country.CountryCodesRepository
import com.kornerz.core.storage.interests.KornerInterestRepository
import com.kornerz.core.ui.theme.KornerzTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Single activity host. Edge-to-edge enabled so the bottom-bar
 * respects gesture navigation insets on API 26+.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RepositoryEntryPoint {
        fun countryCodesRepository(): CountryCodesRepository
        fun kornerInterestRepository(): KornerInterestRepository
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val entry = EntryPointAccessors.fromApplication(
            applicationContext,
            RepositoryEntryPoint::class.java,
        )
        val countryRepository = entry.countryCodesRepository()
        val interestsRepository = entry.kornerInterestRepository()
        setContent {
            KornerzTheme {
                AppRoot(
                    countryRepository = countryRepository,
                    interestsRepository = interestsRepository,
                )
            }
        }
    }
}