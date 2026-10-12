package com.kornerz.feature.profile.data.network

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud Functions API wrapper. Used by the account-deletion flow
 * (P6, `phase-6-profile-and-notifications` task 3.4 / 7.2). Mirrors
 * iOS `FireStoreServiceManager.shared.deleteUserAccount`.
 */
@Singleton
class CloudFunctionsApi @Inject constructor(
    private val functions: FirebaseFunctions,
) {

    /**
     * Calls the `deleteUserAccount` Cloud Function. The function
     * deletes the user's auth record, Firestore document, and any
     * owned community docs. Returns `Result.success(Unit)` on a
     * successful function call.
     */
    suspend fun deleteUserAccount(): Result<Unit> = runCatching {
        functions
            .getHttpsCallable(DELETE_USER_ACCOUNT_FUNCTION)
            .call()
            .await()
        Unit
    }

    private companion object {
        const val DELETE_USER_ACCOUNT_FUNCTION = "deleteUserAccount"
    }
}
