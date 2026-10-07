package com.kornerz.core.storage.interests

import android.content.Context
import com.kornerz.core.model.KornerInterest
import com.kornerz.core.model.KornerInterests
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the bundled Korner-interest catalog from
 * `core/model/src/main/assets/korner_interests.json` and exposes it as a
 * one-shot suspend function (architecture §2.3).
 */
@Singleton
class KornerInterestRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val json: Json = Json { ignoreUnknownKeys = true }

    suspend fun getAll(): Result<List<KornerInterest>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.assets.open(ASSET_NAME).bufferedReader()
                .use { it.readText() }
            json.decodeFromString(KornerInterests.serializer(), raw).interests
        }
    }

    private companion object {
        const val ASSET_NAME = "korner_interests.json"
    }
}