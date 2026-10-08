package com.kornerz.core.network.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kornerz.core.model.User
import com.kornerz.core.network.auth.dto.ChangePasswordRequest
import com.kornerz.core.network.auth.dto.ChangePasswordResponse
import com.kornerz.core.network.auth.dto.ClearFcmTokenRequest
import com.kornerz.core.network.auth.dto.ClearFcmTokenResponse
import com.kornerz.core.network.auth.dto.ConfirmPasswordResetCodeRequest
import com.kornerz.core.network.auth.dto.ConfirmPasswordResetCodeResponse
import com.kornerz.core.network.auth.dto.LoginRequest
import com.kornerz.core.network.auth.dto.LoginResponse
import com.kornerz.core.network.auth.dto.RequestPasswordResetRequest
import com.kornerz.core.network.auth.dto.RequestPasswordResetResponse
import com.kornerz.core.network.auth.dto.SignupRequest
import com.kornerz.core.network.auth.dto.SignupResponse
import com.kornerz.core.network.auth.dto.UpdateFcmTokenRequest
import com.kornerz.core.network.auth.dto.UpdateFcmTokenResponse
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
 */
@Singleton
class FirestoreAuthApi @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
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
            return AuthResult.Success(RequestPasswordResetResponse(sent = false))
        }
        val code = generateResetCode()
        snapshot.documents.first().reference.update("resetCode", code).await()
        AuthResult.Success(RequestPasswordResetResponse(sent = true))
    }.getOrElse { mapError(it) }

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

    override suspend fun updateFcmToken(
        request: UpdateFcmTokenRequest,
    ): AuthResult<UpdateFcmTokenResponse> = runCatching {
        val user = firebaseAuth.currentUser
            ?: return AuthResult.Failure(AuthError.SessionExpired)
        firestore.collection("users")
            .document(user.uid)
            .update("tokenPush", request.token)
            .await()
        AuthResult.Success(UpdateFcmTokenResponse(updated = true))
    }.getOrElse { mapError(it) }

    override suspend fun clearFcmToken(
        request: ClearFcmTokenRequest,
    ): AuthResult<ClearFcmTokenResponse> = runCatching {
        val user = firebaseAuth.currentUser
            ?: return AuthResult.Success(ClearFcmTokenResponse(updated = false))
        firestore.collection("users")
            .document(user.uid)
            .update("tokenPush", null)
            .await()
        AuthResult.Success(ClearFcmTokenResponse(updated = true))
    }.getOrElse { mapError(it) }

    override suspend fun fetchUser(userId: String): AuthResult<User> = runCatching {
        val snapshot = usersCollection.document(userId).get().await()
        if (!snapshot.exists()) {
            return AuthResult.Failure(AuthError.SessionExpired)
        }
        val data = snapshot.data ?: emptyMap()
        AuthResult.Success(
            User(
                id = snapshot.id,
                email = data["email"] as? String ?: "",
                displayName = data["name"] as? String ?: "",
                nickname = data["nickname"] as? String ?: "",
                phone = data["number"] as? String,
                countryCode = data["countryCode"] as? String,
                language = data["language"] as? String,
                photoUrl = data["firebaseImageUrl"] as? String,
                fcmToken = data["tokenPush"] as? String,
                isPremium = data["isUserPremium"] as? Boolean ?: false,
                createdAt = null,
            ),
        )
    }.getOrElse { mapError(it) }

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
    }
}