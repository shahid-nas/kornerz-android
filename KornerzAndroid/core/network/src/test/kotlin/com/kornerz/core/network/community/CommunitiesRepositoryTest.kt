package com.kornerz.core.network.community

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The community lookup.
 *
 * The `communityId` field and the Firestore document id are **different
 * values** on the same document — iOS resolves a community by the field
 * (`CommunitiesRepository.swift:241`, `APICommunity.swift:475`). The
 * Android port used to look the document up by its id, which misses for
 * every community whose `communityId` field differs from its document
 * id; that is the "Community not found" alert the P6 notification flow
 * showed.
 */
class CommunitiesRepositoryTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val collection = mockk<CollectionReference>()

    private lateinit var repository: CommunitiesRepository

    @Before
    fun setUp() {
        every { firestore.collection("Communities") } returns collection
        every { collection.whereEqualTo(any<String>(), any<Any>()) } returns collection
        repository = CommunitiesRepository(firestore)
    }

    private fun communityDocument(
        documentId: String,
        communityId: String,
        name: String = "Design",
    ): DocumentSnapshot {
        val document = mockk<DocumentSnapshot>()
        every { document.id } returns documentId
        every { document.data } returns mapOf(
            "communityId" to communityId,
            "communityName" to name,
        )
        return document
    }

    private fun serve(documents: List<DocumentSnapshot>) {
        val snapshot = mockk<QuerySnapshot>()
        every { snapshot.documents } returns documents
        val task = TaskCompletionSource<QuerySnapshot>()
        task.setResult(snapshot)
        every { collection.get() } returns task.task
    }

    @Test
    fun `fetchById looks the document up by the communityId field and not by its document id`() = runTest {
        serve(listOf(communityDocument(documentId = "doc-1", communityId = "community-1")))

        val community = repository.fetchById("community-1")

        assertEquals("community-1", community?.id)
        assertEquals("community-1", community?.code)
        assertEquals("community-1", community?.communityId)
        assertEquals("Design", community?.name)
        verify { collection.whereEqualTo("communityId", "community-1") }
        verify(exactly = 0) { collection.document(any<String>()) }
    }

    @Test
    fun `fetchById returns null when no community carries that communityId`() = runTest {
        serve(emptyList())

        assertNull(repository.fetchById("missing"))
    }

    @Test
    fun `fetchById rejects a blank id`() = runTest {
        assertNull(repository.fetchById(""))
        assertNull(repository.fetchById("   "))
    }

    @Test
    fun `fetchDocumentIdByCommunityId translates the field into the document id`() = runTest {
        serve(listOf(communityDocument(documentId = "doc-1", communityId = "community-1")))

        assertEquals("doc-1", repository.fetchDocumentIdByCommunityId("community-1"))
    }

    @Test
    fun `fetchDocumentIdByCommunityId returns null when the community does not resolve`() = runTest {
        serve(emptyList())

        assertNull(repository.fetchDocumentIdByCommunityId("missing"))
    }

    @Test
    fun `fetchDocumentIdByCommunityId rejects a blank id`() = runTest {
        assertNull(repository.fetchDocumentIdByCommunityId(""))
    }
}
