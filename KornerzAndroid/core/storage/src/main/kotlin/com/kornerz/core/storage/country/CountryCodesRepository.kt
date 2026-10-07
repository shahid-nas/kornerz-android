package com.kornerz.core.storage.country

import android.content.Context
import com.kornerz.core.model.Country
import com.kornerz.core.model.CountryCodes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the bundled ISO-3166-1 country catalog from
 * `core/model/src/main/assets/country_codes.json` and exposes it as a
 * one-shot suspend function (architecture §2.3 — `suspend fun ... Result<T>`
 * for one-shot reads).
 *
 * P1 only ever reads the catalog once per cold start; results are kept
 * in-memory by callers as needed.
 */
@Singleton
class CountryCodesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val json: Json = Json { ignoreUnknownKeys = true }

    suspend fun getAll(): Result<List<Country>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.assets.open(ASSET_NAME).bufferedReader()
                .use { it.readText() }
            json.decodeFromString(CountryCodes.serializer(), raw).countries
        }
    }

    private companion object {
        const val ASSET_NAME = "country_codes.json"
    }
}