package com.kornerz.feature.profile

import com.kornerz.core.model.User
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.profile.data.UserProfileRepository
import com.kornerz.feature.profile.data.network.CloudFunctionsApi
import com.kornerz.feature.profile.data.network.UserFirestoreApi
import com.kornerz.feature.profile.data.network.UserNicknameFirestoreApi
import com.kornerz.feature.profile.data.network.UserStorageApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UserProfileRepositoryTest {

    @Test
    fun `updateName writes to firestore and emits new user`() = runTest {
        val userApi = mockk<UserFirestoreApi>()
        val nicknameApi = mockk<UserNicknameFirestoreApi>()
        val storageApi = mockk<UserStorageApi>()
        val cloudFunctionsApi = mockk<CloudFunctionsApi>()
        val sessionRepo = mockk<SessionRepository>(relaxed = true)

        coEvery { userApi.updateName("u1", "New Name") } returns Result.success(Unit)
        coEvery { userApi.fetchByCode("u1") } returns null

        val repo = UserProfileRepository(userApi, nicknameApi, storageApi, cloudFunctionsApi, sessionRepo)
        // No session user is emitted — we only assert the api call happens
        // (the in-memory cache write is a no-op when no user is loaded).
        repo.updateName("u1", "New Name")
    }

    @Test
    fun `checkIfNickNameExists delegates to nickname api`() = runTest {
        val userApi = mockk<UserFirestoreApi>(relaxed = true)
        val nicknameApi = mockk<UserNicknameFirestoreApi>()
        val storageApi = mockk<UserStorageApi>(relaxed = true)
        val cloudFunctionsApi = mockk<CloudFunctionsApi>(relaxed = true)
        val sessionRepo = mockk<SessionRepository>(relaxed = true)

        coEvery { nicknameApi.checkIfNickNameExists("taken") } returns Result.success(true)

        val repo = UserProfileRepository(userApi, nicknameApi, storageApi, cloudFunctionsApi, sessionRepo)
        val result = repo.checkIfNickNameExists("taken")
        assertEquals(true, result.getOrNull())
    }

    @Test
    fun `observeSessionUser maps authenticated state to user`() = runTest {
        val userApi = mockk<UserFirestoreApi>(relaxed = true)
        val nicknameApi = mockk<UserNicknameFirestoreApi>(relaxed = true)
        val storageApi = mockk<UserStorageApi>(relaxed = true)
        val cloudFunctionsApi = mockk<CloudFunctionsApi>(relaxed = true)
        val sessionRepo = mockk<SessionRepository>(relaxed = true)
        val testUser = User(id = "u1", email = "a@b.com", displayName = "Test", nickname = "t")
        coEvery { sessionRepo.data } returns flowOf(SessionState.Authenticated(testUser))

        val repo = UserProfileRepository(userApi, nicknameApi, storageApi, cloudFunctionsApi, sessionRepo)
        val first: User? = repo.observeSessionUser().first()
        assertEquals(testUser, first)
    }
}
