package com.kornerz.core.network.loops

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.kornerz.core.model.Community
import com.kornerz.core.model.Loop
import com.kornerz.core.model.User
import com.kornerz.core.network.community.CommunitiesRepository
import com.kornerz.core.network.user.UsersRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The loop-detail join — the three reads that resolve a loop id into
 * the bundle the detail screen renders.
 */
class LoopDetailRepositoryTest {

    private val loopFirestoreApi = mockk<LoopFirestoreApi>()
    private val usersRepository = mockk<UsersRepository>()
    private val communitiesRepository = mockk<CommunitiesRepository>()

    private lateinit var repository: LoopDetailRepository

    @Before
    fun setUp() {
        repository = LoopDetailRepository(
            loopFirestoreApi = loopFirestoreApi,
            usersRepository = usersRepository,
            communitiesRepository = communitiesRepository,
        )
    }

    private fun loop(
        id: String = "l1",
        publisherId: String? = "u1",
        communityId: String? = "c1",
    ) = Loop(id = id, publisherId = publisherId, communityId = communityId, title = "Trip")

    private fun resolvedOwner(code: String = "u1") = User(
        id = "doc-1",
        email = "a@example.com",
        displayName = "Ana",
        nickname = "ana",
        userCode = code,
    )

    private fun resolvedCommunity(documentId: String = "doc-c1", communityId: String = "c1") = Community(
        id = documentId,
        code = documentId,
        communityId = communityId,
        name = "Brazil Travel",
    )

    @Test
    fun `the detail resolves loop, owner and community in one call`() = runTest {
        every { loopFirestoreApi.fetchLoopDetail("l1") } returns flowOf(Result.success(loop()))
        coEvery { usersRepository.fetchUsersByCodes(listOf("u1")) } returns Result.success(mapOf("u1" to resolvedOwner()))
        coEvery { communitiesRepository.fetchByCommunityId("c1") } returns resolvedCommunity()

        val detail = repository.fetchLoopDetail("l1").getOrThrow()

        assertEquals("Trip", detail.loop.title)
        assertEquals("ana", detail.owner?.nickname)
        assertEquals("Brazil Travel", detail.community?.name)
        verify(exactly = 1) { loopFirestoreApi.fetchLoopDetail("l1") }
    }

    @Test
    fun `the accent colour is derived from the resolved loop id`() = runTest {
        every { loopFirestoreApi.fetchLoopDetail("l1") } returns flowOf(Result.success(loop(id = "l1")))
        coEvery { usersRepository.fetchUsersByCodes(listOf("u1")) } returns Result.success(mapOf("u1" to resolvedOwner()))
        coEvery { communitiesRepository.fetchByCommunityId("c1") } returns resolvedCommunity()

        val detail = repository.fetchLoopDetail("l1").getOrThrow()

        assertEquals("#E27D5A", detail.accentColorHex)
    }

    @Test
    fun `an unresolved owner degrades to null`() = runTest {
        every { loopFirestoreApi.fetchLoopDetail("l1") } returns flowOf(Result.success(loop()))
        coEvery { usersRepository.fetchUsersByCodes(listOf("u1")) } returns Result.success(emptyMap())
        coEvery { communitiesRepository.fetchByCommunityId("c1") } returns resolvedCommunity()

        val detail = repository.fetchLoopDetail("l1").getOrThrow()

        assertNull(detail.owner)
        assertNotNull(detail.community)
    }

    @Test
    fun `an unresolved community degrades to null`() = runTest {
        every { loopFirestoreApi.fetchLoopDetail("l1") } returns flowOf(Result.success(loop()))
        coEvery { usersRepository.fetchUsersByCodes(listOf("u1")) } returns Result.success(mapOf("u1" to resolvedOwner()))
        coEvery { communitiesRepository.fetchByCommunityId("c1") } returns null

        val detail = repository.fetchLoopDetail("l1").getOrThrow()

        assertNull(detail.community)
        assertNotNull(detail.owner)
    }

    @Test
    fun `a missing loop produces a failure`() = runTest {
        every { loopFirestoreApi.fetchLoopDetail("missing") } returns flowOf(
            Result.failure(IllegalStateException("Loop missing not found")),
        )

        val result = repository.fetchLoopDetail("missing")

        assertTrue(result.isFailure)
    }

    @Test
    fun `a blank loop id produces a failure without querying`() = runTest {
        val result = repository.fetchLoopDetail("  ")

        assertTrue(result.isFailure)
        verify(exactly = 0) { loopFirestoreApi.fetchLoopDetail(any()) }
    }
}

/**
 * The `Loop` document lookup — the `id`-field-first resolution with the
 * document-id fallback, mirroring iOS `APICommunity.swift:606`.
 */
class LoopFirestoreApiDetailLookupTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val auth = mockk<FirebaseAuth>()
    private val loops = mockk<CollectionReference>()
    private val loopQuery = mockk<Query>()
    private val documentReference = mockk<DocumentReference>()

    private lateinit var api: LoopFirestoreApi

    @Before
    fun setUp() {
        every { firestore.collection("Loop") } returns loops
        every { loops.whereEqualTo("id", any<String>()) } returns loopQuery
        every { loopQuery.limit(any<Long>()) } returns loopQuery
        every { loops.document(any<String>()) } returns documentReference
        api = LoopFirestoreApi(firestore, auth)
    }

    private fun document(data: Map<String, Any?>): DocumentSnapshot {
        val document = mockk<DocumentSnapshot>()
        every { document.data } returns data
        every { document.id } returns "doc-xyz"
        return document
    }

    private fun queryResult(documents: List<DocumentSnapshot>) {
        val snapshot = mockk<QuerySnapshot>()
        every { snapshot.documents } returns documents
        val task = TaskCompletionSource<QuerySnapshot>()
        task.setResult(snapshot)
        every { loopQuery.get() } returns task.task
    }

    @Test
    fun `the loop is resolved by its id field`() = runTest {
        queryResult(listOf(document(mapOf("id" to "l1", "title" to "Trip"))))

        val loop = api.fetchLoopDetail("l1").first().getOrThrow()

        assertEquals("Trip", loop.title)
        verify(exactly = 1) { loops.whereEqualTo("id", "l1") }
        verify(exactly = 0) { loops.document(any<String>()) }
    }

    @Test
    fun `the document id is the fallback when no document carries the id field`() = runTest {
        queryResult(emptyList())
        val fallback = document(mapOf("title" to "Fallback"))
        val task = TaskCompletionSource<DocumentSnapshot>()
        task.setResult(fallback)
        every { documentReference.get() } returns task.task

        val loop = api.fetchLoopDetail("l1").first().getOrThrow()

        assertEquals("Fallback", loop.title)
        verify(exactly = 1) { loops.document("l1") }
    }

    @Test
    fun `a loop that does not exist produces a failure`() = runTest {
        queryResult(emptyList())
        val missing = mockk<DocumentSnapshot>()
        every { missing.data } returns null
        val task = TaskCompletionSource<DocumentSnapshot>()
        task.setResult(missing)
        every { documentReference.get() } returns task.task

        val result = api.fetchLoopDetail("l1").first()

        assertTrue(result.isFailure)
    }
}
