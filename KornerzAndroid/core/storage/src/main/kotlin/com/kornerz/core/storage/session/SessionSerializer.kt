package com.kornerz.core.storage.session

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Encodes/decodes a [SessionRecord] as JSON inside a single DataStore
 * file (architecture §3 — DataStore Preferences isn't used here because
 * the record is an aggregate, not loose keys). The serializer is
 * stateless and thread-safe.
 */
object SessionSerializer : Serializer<SessionRecord> {

    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: SessionRecord
        get() = throw UnsupportedOperationException(
            "SessionStorage has no meaningful default; absence of a record " +
                "should be modelled as a missing file.",
        )

    override suspend fun readFrom(input: InputStream): SessionRecord {
        try {
            return json.decodeFromString(
                SessionRecord.serializer(),
                input.readBytes().decodeToString(),
            )
        } catch (e: SerializationException) {
            throw CorruptionException("Unable to read SessionRecord", e)
        }
    }

    override suspend fun writeTo(t: SessionRecord, output: OutputStream) {
        output.write(
            json.encodeToString(SessionRecord.serializer(), t).encodeToByteArray(),
        )
    }
}