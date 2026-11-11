package com.kornerz.core.network.nooks

import android.util.Log
import com.kornerz.core.model.SessionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for the `getSessionsInfo` Cloud Function — the endpoint the
 * iOS app uses to learn who is currently connected to a video-call
 * room and how many participants it has.
 *
 * iOS reference:
 *  - `kornerz/Kornerz/Helper/ApiService.swift:273-305`
 *    (`getSessionsInfo` / `getSessionsInfoAsync`)
 *  - `kornerz/Kornerz/Models/ConnectionCountResponse.swift`
 *    (`SessionsInfoResponse` / `SessionInfo` / `SessionInfoUser`)
 *
 * **Request.** `POST` with a JSON body `{"sessionIds": ["…", "…"]}`.
 *
 * **Response.** `{"success": true, "sessions": {"<sessionId>": {...}}}`.
 * Each per-session entry carries `success`, `connections`, and `users`.
 *
 * **Attendee names are JSON-encoded strings.** The backend returns each
 * participant's `name` as a *string containing JSON*, e.g.
 * `"name": "{\"userName\":\"Ada\"}"`. iOS decodes that nested payload
 * and reads its `userName` key; [decodeUserName] does the same so both
 * clients show identical names.
 *
 * Failures are **non-fatal**: the Nooks list still renders, falling back
 * to the "no one is attending" copy and a full spots count. iOS behaves
 * the same way — it uses `try?` and treats a failed lookup as "no
 * session info" (`NooksTabViewModel.swift:112`).
 */
@Singleton
class SessionInfoApi @Inject constructor(
    private val client: OkHttpClient,
) {

    /**
     * Fetches the live roster for every id in [sessionIds].
     *
     * Returns an empty map when [sessionIds] is blank or the call fails
     * — never throws — so callers can render the list unconditionally.
     *
     * **Threading.** The OkHttp call is a *blocking* `execute()`, so it
     * runs on [Dispatchers.IO]. Without that switch it inherited the
     * caller's dispatcher — `viewModelScope` is `Dispatchers.Main` —
     * and Android rejected it with `NetworkOnMainThreadException`.
     * (The Firestore reads in the same repositories are unaffected
     * because `await()` suspends instead of blocking.)
     *
     * **Logging.** Every call is traced under the `SessionInfoApi` tag so
     * the round trip can be verified from logcat:
     *
     * ```
     * adb logcat -s SessionInfoApi
     * ```
     *
     * The trace covers the request (endpoint + session ids), the raw
     * response body, the HTTP status and elapsed time, the parsed
     * per-session summary, and the failure reason when the call does not
     * go through.
     */
    suspend fun fetchSessionsInfo(sessionIds: List<String>): Map<String, SessionInfo> {
        val ids = sessionIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) {
            Log.i(TAG, "GET sessions info — skipped, no session ids requested")
            return emptyMap()
        }

        return try {
            val body = buildString {
                append("{\"sessionIds\":[")
                append(ids.joinToString(",") { "\"$it\"" })
                append("]}")
            }
            val request = Request.Builder()
                .url(ENDPOINT)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            Log.i(
                TAG,
                "GET sessions info — request: POST ${ENDPOINT} ids=${ids.size} " +
                    "[${ids.joinToString(", ") { it.ellipsize() }}]",
            )
            val startedAt = System.currentTimeMillis()

            val outcome = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val elapsedMs = System.currentTimeMillis() - startedAt
                    val payload = response.body?.string().orEmpty()
                    Log.i(
                        TAG,
                        "GET sessions info — response: http=${response.code} " +
                            "success=${response.isSuccessful} elapsedMs=$elapsedMs " +
                            "bytes=${payload.length} body=$payload",
                    )
                    if (!response.isSuccessful) {
                        Log.w(
                            TAG,
                            "GET sessions info — HTTP ${response.code}, treating as no session info",
                        )
                        emptyMap()
                    } else {
                        parseSessions(payload)
                    }
                }
            }

            Log.i(
                TAG,
                "GET sessions info — parsed ${outcome.size}/${ids.size} sessions: " +
                    outcome.entries.joinToString(" | ") { (id, info) ->
                        "$id {connections=${info.connections}, " +
                            "attendees=${info.attendeeNames.size}}" +
                            "${info.attendeeNames.takeIf { it.isNotEmpty() }?.let { " names=$it" } ?: ""}"
                    },
            )
            outcome
        } catch (e: Exception) {
            Log.e(TAG, "GET sessions info — failed: ${e::class.java.simpleName}: ${e.message}", e)
            emptyMap()
        }
    }

    private fun parseSessions(payload: String): Map<String, SessionInfo> {
        val sessions = try {
            json.parseToJsonElement(payload).jsonObject[SESSIONS_KEY]?.jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "GET sessions info — could not parse 'sessions' from body: ${e.message}", e)
            null
        }
        if (sessions == null) {
            Log.w(TAG, "GET sessions info — no 'sessions' object in response body")
            return emptyMap()
        }

        val result = mutableMapOf<String, SessionInfo>()
        for ((sessionId, element) in sessions) {
            val session = try {
                element.jsonObject
            } catch (e: Exception) {
                Log.w(TAG, "GET sessions info — skipping unparseable session '$sessionId': ${e.message}")
                continue
            }
            val connections =
                (session[CONNECTIONS_KEY] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
            result[sessionId] = SessionInfo(
                connections = connections,
                attendeeNames = session.attendeeNames(),
            )
        }
        return result
    }

    /**
     * Reads the `users` array of a session entry, pulling each
     * attendee's display name out of the JSON-encoded `name` string.
     *
     * The raw `name` value is logged once per session so the backend's
     * encoding can be confirmed from logcat — iOS reads the
     * `userName` key out of that nested JSON, and the fallback to the
     * plain string is there for a backend that sends it unwrapped.
     */
    private fun JsonObject.attendeeNames(): List<String> {
        val users = this[USERS_KEY] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        val rawNames = users.mapNotNull { element ->
            (element as? JsonObject)?.get(NAME_KEY) as? JsonPrimitive
        }
        if (rawNames.isNotEmpty()) {
            Log.i(
                TAG,
                "GET sessions info — raw attendee name values: " +
                    rawNames.joinToString(" | ") { it.content },
            )
        }
        return rawNames.mapNotNull { decodeUserName(it.content) }
    }

    /**
     * Mirrors iOS `SessionInfoUser.userName`: the `name` string is
     * itself a JSON object; read its `userName` key. A plain
     * (non-JSON) name is accepted as-is so a backend change degrades
     * gracefully instead of blanking the line.
     */
    private fun decodeUserName(raw: String): String? {
        return try {
            val decoded = json.parseToJsonElement(raw).jsonObject
            (decoded[USER_NAME_KEY] as? JsonPrimitive)?.content
        } catch (_: Exception) {
            raw.takeIf { it.isNotBlank() }
        }
    }

    private companion object {
        const val ENDPOINT =
            "https://us-central1-kornerz-72fba.cloudfunctions.net/getSessionsInfo"

        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        val json = Json { ignoreUnknownKeys = true }

        const val SESSIONS_KEY = "sessions"
        const val CONNECTIONS_KEY = "connections"
        const val USERS_KEY = "users"
        const val NAME_KEY = "name"
        const val USER_NAME_KEY = "userName"

        const val TAG = "SessionInfoApi"
    }
}

/**
 * Shortens a session id for the log line — the ids are ~70 characters
 * long, so a page of them would swamp the log.
 */
private fun String.ellipsize(limit: Int = 24): String =
    if (length <= limit) this else take(limit) + "…"

/**
 * Wire shape of one attendee entry. The parser reads the JSON tree
 * directly so it can tolerate the backend adding fields; this type
 * documents the contract for reference.
 */
@Serializable
private data class SessionAttendeeWire(
    val id: String? = null,
    @SerialName("name") val name: String? = null,
)
