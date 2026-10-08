package com.kornerz.core.storage.session

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.kornerz.core.model.User
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

    /**
     * Materialised by DataStore on the very first read/write when the
     * file does not yet exist (fresh install, or after the corruption
     * handler replaces a missing/garbled file). An empty record maps to
     * [SessionState.Anonymous] in `SessionRepository.toState`, matching
     * the shape `clearSession()` writes when the user signs out.
     */
    override val defaultValue: SessionRecord = SessionRecord(
        token = "",
        expiresAt = "",
        user = User(id = "", email = "", displayName = "", nickname = ""),
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