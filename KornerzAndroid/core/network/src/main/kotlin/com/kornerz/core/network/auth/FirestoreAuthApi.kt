package com.kornerz.core.network.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kornerz.core.model.SubscriptionDiagnostics
import com.kornerz.core.model.User
import com.kornerz.core.network.auth.dto.ChangePasswordRequest
import com.kornerz.core.network.auth.dto.ChangePasswordResponse
import com.kornerz.core.network.auth.dto.ConfirmPasswordResetCodeRequest
import com.kornerz.core.network.auth.dto.ConfirmPasswordResetCodeResponse
import com.kornerz.core.network.auth.dto.LoginRequest
import com.kornerz.core.network.auth.dto.LoginResponse
import com.kornerz.core.network.auth.dto.RequestPasswordResetRequest
import com.kornerz.core.network.auth.dto.RequestPasswordResetResponse
import com.kornerz.core.network.auth.dto.SendRecoveryEmailRequest
import com.kornerz.core.network.auth.dto.SendRecoveryEmailResponse
import com.kornerz.core.network.auth.dto.SignupRequest
import com.kornerz.core.network.auth.dto.SignupResponse
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.plus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete [AuthApi] that talks to the same backend the iOS app uses.
 *
 * **Implementation note** — the original proposal assumed a set of
 * custom-auth Cloud Functions, but inspection of the iOS source
 * (`kornerz/Kornerz/Backend/Repositories/UserRepository.swift`,
 * `ForgotPasswordRepository.swift`) shows the iOS app talks to Firestore
 * directly. This class mirrors that behaviour one-for-one while keeping the
 * Cloud-Function-shaped contract from [AuthApi] so P2+ can swap to a real
 * Cloud Function client without touching any ViewModel. See task #2.3 for
 * the follow-up issue.
 *
 * **Password recovery.** The reset code is written to the user document
 * exactly as iOS does (`resetCode`), but the code is *also* returned to
 * the caller so it can be mailed through the `sendrecoveryemail` Cloud
 * Function — iOS mails it from `ConfirmCodeViewModel.sendRecoveryEamil()`
 * using the code it received through navigation.
 */
@Singleton
class FirestoreAuthApi @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val recoveryEmailApi: RecoveryEmailApi,
) : AuthApi {

    private val usersCollection
        get() = firestore.collection(USERS_COLLECTION)

    override suspend fun login(request: LoginRequest): AuthResult<LoginResponse> = runCatching {
        val snapshot = usersCollection
            .whereEqualTo("email", request.email)
            .whereEqualTo("password", request.password)
            .get()
            .await()
        if (snapshot.isEmpty) {
            return AuthResult.Failure(AuthError.InvalidCredentials)
        }
        val doc = snapshot.documents.first()
        val userId = doc.id
        signInFirebaseAnonymously()
        AuthResult.Success(
            LoginResponse(
                token = "firebase-anon",
                expiresAt = Clock.System.now().plus(7 * 24, DateTimeUnit.HOUR).toString(),
                userId = userId,
            ),
        )
    }.getOrElse { mapError(it) }

    override suspend fun signup(request: SignupRequest): AuthResult<SignupResponse> = runCatching {
        val existing = usersCollection.whereEqualTo("email", request.email).get().await()
        if (!existing.isEmpty) {
            return AuthResult.Failure(AuthError.EmailAlreadyInUse)
        }
        val docRef = usersCollection.document(request.email)
        val payload = mapOf(
            "id" to docRef.id,
            "name" to request.displayName,
            "nickname" to request.nickname,
            "codeUser" to (request.userCode ?: generateUserCode()),
            "email" to request.email,
            "number" to request.phone,
            "countryCode" to request.countryCode,
            "language" to request.language,
            "interests" to request.interests,
            "firebaseImageUrl" to request.photoUrl,
            "password" to request.password,
            "emailUserLogged" to true,
            "termosAceitos" to true,
            "isUserPremium" to false,
            FIELD_REGISTRATION_DATE to com.google.firebase.Timestamp.now(),
        )
        docRef.set(payload).await()
        signInFirebaseAnonymously()
        AuthResult.Success(
            SignupResponse(
                token = "firebase-anon",
                expiresAt = Clock.System.now().plus(7 * 24, DateTimeUnit.HOUR).toString(),
                userId = docRef.id,
            ),
        )
    }.getOrElse { mapError(it) }

    override suspend fun requestPasswordReset(
        request: RequestPasswordResetRequest,
    ): AuthResult<RequestPasswordResetResponse> = runCatching {
        val snapshot = usersCollection.whereEqualTo("email", request.email).get().await()
        if (snapshot.isEmpty) {
            return AuthResult.Success(RequestPasswordResetResponse(sent = false, code = null))
        }
        val code = generateResetCode()
        snapshot.documents.first().reference.update("resetCode", code).await()
        // The code travels back to the caller so it can be mailed through
        // the `sendrecoveryemail` Cloud Function — iOS hands the code over
        // the same way (`ForgotPasswordViewModel` → `navigateToConfirmCode`).
        AuthResult.Success(RequestPasswordResetResponse(sent = true, code = code))
    }.getOrElse { mapError(it) }

    override suspend fun sendRecoveryEmail(
        request: SendRecoveryEmailRequest,
    ): AuthResult<SendRecoveryEmailResponse> {
        return recoveryEmailApi.send(request.to, request.code).fold(
            onSuccess = { AuthResult.Success(SendRecoveryEmailResponse(sent = true)) },
            onFailure = { mapError(it) },
        )
    }

    override suspend fun confirmPasswordResetCode(
        request: ConfirmPasswordResetCodeRequest,
    ): AuthResult<ConfirmPasswordResetCodeResponse> = runCatching {
        val snapshot = usersCollection.whereEqualTo("email", request.email).get().await()
        if (snapshot.isEmpty) {
            return AuthResult.Failure(AuthError.InvalidCode)
        }
        val doc = snapshot.documents.first()
        val stored = doc.getString("resetCode")
        if (stored == request.code) {
            AuthResult.Success(ConfirmPasswordResetCodeResponse(resetToken = doc.id))
        } else {
            AuthResult.Failure(AuthError.InvalidCode)
        }
    }.getOrElse { mapError(it) }

    override suspend fun changePassword(
        request: ChangePasswordRequest,
    ): AuthResult<ChangePasswordResponse> = runCatching {
        usersCollection.document(request.resetToken)
            .update("password", request.newPassword)
            .await()
        AuthResult.Success(ChangePasswordResponse(updated = true))
    }.getOrElse { mapError(it) }

    override suspend fun fetchUser(userId: String): AuthResult<User> = runCatching {
        val snapshot = usersCollection.document(userId).get().await()
        if (!snapshot.exists()) {
            return AuthResult.Failure(AuthError.SessionExpired)
        }
        val data = snapshot.data ?: emptyMap()
        // The entitlement fields are the paywall gate's input, and the
        // decision parses the decoded expiry string. The raw Firestore
        // value and its type are logged next to the decoded string so a
        // value that decodes to something `Instant.parse` rejects — which
        // makes a paying subscriber look free — is visible rather than
        // inferred.
        val rawExpiry = data[FIELD_SUBSCRIPTION_EXPIRY]
        SubscriptionDiagnostics.log(
            "1-refresh",
            "read the user document ${snapshot.id} — " +
                "rawExpiry=$rawExpiry rawExpiryType=${rawExpiry?.javaClass?.simpleName} " +
                "decodedExpiry=${rawExpiry.toInstantString()} " +
                "subscriptionType=${data[FIELD_SUBSCRIPTION_TYPE]} " +
                "subscriptionPlatform=${data[FIELD_SUBSCRIPTION_PLATFORM]}",
        )
        AuthResult.Success(
            User(
                id = snapshot.id,
                email = data["email"] as? String ?: "",
                displayName = data["name"] as? String ?: "",
                nickname = data["nickname"] as? String ?: "",
                userCode = data["codeUser"] as? String,
                phone = data["number"] as? String,
                countryCode = data["countryCode"] as? String,
                language = data["language"] as? String,
                photoUrl = data["firebaseImageUrl"] as? String,
                fcmToken = data["tokenPush"] as? String,
                isPremium = data["isUserPremium"] as? Boolean ?: false,
                // The three entitlement fields. They are the *server's*
                // record of what the user bought, and they are what
                // `User.isUserPremiumNow` — the single premium decision
                // input on both platforms — is derived from. Dropping
                // them here made every user look permanently free to the
                // paywall gate and to the Profile row.
                //
                // Normalised to the ISO-8601 `String?` the `User` model
                // stores, because `SessionRecord` serialises the whole
                // `User` into one DataStore JSON document and a Firestore
                // `Timestamp` is not `@Serializable`. Android writes a
                // `Timestamp`; an older document may hold a string. Both
                // are accepted, and an unrecognised shape degrades to
                // `null` — which makes `isUserPremiumNow` `false` rather
                // than crashing the shell.
                subscriptionExpiryDate = data[FIELD_SUBSCRIPTION_EXPIRY]?.toInstantString(),
                subscriptionType = data[FIELD_SUBSCRIPTION_TYPE] as? String,
                subscriptionPlatform = data[FIELD_SUBSCRIPTION_PLATFORM] as? String,                createdAt = data[FIELD_REGISTRATION_DATE]?.toInstantString(),
                // The avatar projection fields. Without these the session
                // user's `thumbnailURL` / `imageURL` resolve to null and
                // the Profile screen always renders the placeholder.
                firebaseImageUrl = data["firebaseImageUrl"] as? String,
                firebaseThumbnailUrl = data["firebaseThumbnailUrl"] as? String,
                thumbnailType = data["thumbnailType"] as? String,
            ),
        )
    }.getOrElse { mapError(it) }

    /**
     * Normalises a Firestore subscription-expiry value to the ISO-8601
     * `String?` the [User] model stores.
     *
     * Mirrors `UserFirestoreApi.toInstantString` in
     * `feature/profile` — the same document shape is decoded in three
     * places, and all three must agree on the representation or a
     * subscriber silently loses their entitlement depending on which
     * reader ran.
     */
    private fun Any?.toInstantString(): String? = when (this) {
        is com.google.firebase.Timestamp -> runCatching {
            kotlinx.datetime.Instant.fromEpochMilliseconds(toDate().time).toString()
        }.getOrNull()
        is String -> this
        else -> null
    }

    override suspend fun checkIfNickNameExists(nickname: String): AuthResult<Boolean> =
        runCatching {
            val snapshot = usersCollection
                .whereEqualTo("nickname", nickname)
                .get()
                .await()
            AuthResult.Success(!snapshot.isEmpty)
        }.getOrElse { mapError(it) }

    override suspend fun checkIfEmailExists(email: String): AuthResult<Boolean> =
        runCatching {
            val snapshot = usersCollection
                .whereEqualTo("email", email)
                .get()
                .await()
            AuthResult.Success(!snapshot.isEmpty)
        }.getOrElse { mapError(it) }

    override fun now(): Instant = Clock.System.now()

    private suspend fun signInFirebaseAnonymously() {
        if (firebaseAuth.currentUser == null) {
            runCatching { firebaseAuth.signInAnonymously().await() }
        }
    }

    private fun generateResetCode(): String =
        (1..6).map { ('0'..'9').random() }.joinToString("")

    private fun generateUserCode(): String =
        (1..8).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")

    private fun mapError(t: Throwable): AuthResult<Nothing> =
        AuthResult.Failure(
            when (t) {
                is java.net.UnknownHostException,
                is java.io.IOException -> AuthError.NetworkError
                else -> AuthError.Unknown(t.message ?: t::class.java.simpleName)
            },
        )

    private companion object {
        const val USERS_COLLECTION = "Users"

        /**
         * The three entitlement keys, alongside the raw `isUserPremium`
         * bit. `isUserPremium` is mapped but never used as the premium
         * decision input — see `User.isUserPremiumNow`.
         */
        const val FIELD_SUBSCRIPTION_EXPIRY = "subscriptionExpiryDate"
        const val FIELD_SUBSCRIPTION_TYPE = "subscriptionType"
        const val FIELD_SUBSCRIPTION_PLATFORM = "subscriptionPlatform"
        const val FIELD_REGISTRATION_DATE = "userDateRegister"
    }
}