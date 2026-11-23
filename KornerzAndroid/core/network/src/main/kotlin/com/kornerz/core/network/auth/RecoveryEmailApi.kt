package com.kornerz.core.network.auth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for the `sendrecoveryemail` Cloud Function — the endpoint the
 * iOS app uses to mail the password-reset code to the user.
 *
 * iOS reference:
 *  - `kornerz/Kornerz/Helper/ApiService.swift:207-246`
 *    (`sendRecoveryEmail(toEmail:code:completion:)`)
 *  - `kornerz/Kornerz/User/ConfirmCode/ConfirmCodeViewModel.swift:44`
 *    (invoked when the Confirm Code screen appears, and on resend)
 *
 * **Request.** `POST` with a JSON body
 * `{"to": "<email>", "subject": "Recovery Code", "text": "…<code>…"}`.
 * No query parameters, no auth header.
 *
 * **Endpoint.** The production Cloud Function URL iOS uses
 * (`sendrecoveryemail-hp7acuma7a-uc.a.run.app`); iOS always talks to
 * production regardless of the app flavour, so Android does the same.
 *
 * **Why this exists.** Without it the Android client generated a reset
 * code, stored it in Firestore and never told anybody about it — so no
 * email was ever sent and the user could not complete the flow.
 */
@Singleton
class RecoveryEmailApi @Inject constructor(
    private val client: OkHttpClient,
) {

    /**
     * Mails [code] to [email].
     *
     * Returns a failed [Result] when the function is unreachable or
     * answers with a non-2xx status; never throws.
     */
    suspend fun send(email: String, code: String): Result<Unit> {
        if (email.isBlank() || code.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing email or recovery code"))
        }

        val payload = buildJsonObject {
            put("to", JsonPrimitive(email))
            put("subject", JsonPrimitive(SUBJECT))
            put("text", JsonPrimitive(bodyText(code)))
        }
        val body = json.encodeToString(JsonObject.serializer(), payload)
        val request = Request.Builder()
            .url(ENDPOINT)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "Recovery email sent to $email")
                        Result.success(Unit)
                    } else {
                        Log.w(TAG, "Recovery email failed — HTTP ${response.code}")
                        Result.failure(
                            IllegalStateException("Recovery email failed: HTTP ${response.code}"),
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recovery email failed: ${e::class.java.simpleName}: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * The exact copy iOS sends, so a user who gets the Android mail sees
     * the same wording as an iOS user.
     */
    private fun bodyText(code: String): String =
        "Please use following code to reset your password for Kornzerz App: $code"

    private companion object {
        const val ENDPOINT = "https://sendrecoveryemail-hp7acuma7a-uc.a.run.app"
        const val SUBJECT = "Recovery Code"
        const val TAG = "RecoveryEmailApi"

        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val json = Json { ignoreUnknownKeys = true }
    }
}
