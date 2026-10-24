package com.kornerz.core.network.loops

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The **batched** comment-engagement reads.
 *
 * iOS issues two queries per comment inside its `DispatchGroup`
 * fan-out, so a 40-comment loop costs 80 reads there. The batched form
 * costs two — one per chunk of 30 ids — and produces the same data.
 */
class CommentLikeRepositoryTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val likes = mockk<CollectionReference>()
    private val query = mockk<Query>()

    /** The id groups each `in` clause was asked for, in order. */
    private val requestedChunks = mutableListOf<List<String>>()

    private lateinit var repository: CommentLikeRepository

    @Before
    fun setUp() {
        every { firestore.collection("CommentLikes") } returns likes
        every { likes.whereIn("commentId", any<List<Any>>()) } answers {
            requestedChunks.add(secondArg<List<Any>>().map { it.toString() })
            query
        }
        every { likes.whereEqualTo("userId", any<String>()) } returns query
        every { query.whereIn("commentId", any<List<Any>>()) } answers {
            requestedChunks.add(secondArg<List<Any>>().map { it.toString() })
            query
        }
        every { query.get() } returns TaskCompletionSource<QuerySnapshot>().apply {
            setResult(emptySnapshot())
        }.task
        repository = CommentLikeRepository(CommentLikeFirestoreApi(firestore))
    }

    private fun emptySnapshot(): QuerySnapshot {
        val snapshot = mockk<QuerySnapshot>()
        every { snapshot.documents } returns emptyList()
        return snapshot
    }

    private fun likeDocument(commentId: String): DocumentSnapshot {
        val document = mockk<DocumentSnapshot>()
        every { document.getString("commentId") } returns commentId
        every { document.getString("userId") } returns "u1"
        return document
    }

    /**
     * Serves one page per query, in order — the repository issues one
     * query per chunk, so each chunk needs its own page.
     */
    private fun servePages(vararg pages: List<DocumentSnapshot>) {
        val queue = ArrayDeque(pages.toList())
        every { query.get() } answers {
            val page = if (queue.isEmpty()) emptyList() else queue.removeFirst()
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns page
            val task = TaskCompletionSource<QuerySnapshot>()
            task.setResult(snapshot)
            task.task
        }
    }

    @Test
    fun `forty comment ids resolve in two chunked queries`() = runTest {
        val ids = (1..40).map { "c$it" }
        servePages(
            listOf(likeDocument("c1"), likeDocument("c1"), likeDocument("c2")),
            listOf(likeDocument("c3")),
        )

        val counts = repository.fetchLikeCounts(ids).getOrThrow()

        assertEquals(2, requestedChunks.size)
        assertEquals(30, requestedChunks[0].size)
        assertEquals(10, requestedChunks[1].size)
        assertEquals(2, counts["c1"])
        assertEquals(1, counts["c2"])
        assertEquals(1, counts["c3"])
    }

    @Test
    fun `the live count replaces the denormalized comment count`() = runTest {
        servePages(listOf(likeDocument("c1"), likeDocument("c1"), likeDocument("c1")))

        val counts = repository.fetchLikeCounts(listOf("c1")).getOrThrow()

        // The denormalized `LoopComments.likesCount` on the comment
        // document is 2; the live `CommentLikes` collection holds 3.
        val denormalized = 2
        assertEquals(3, counts["c1"])
        assertTrue(counts["c1"] != denormalized)
    }

    @Test
    fun `the liked ids are read in chunked queries`() = runTest {
        val ids = (1..40).map { "c$it" }
        servePages(
            listOf(likeDocument("c3")),
            listOf(likeDocument("c40")),
        )

        val liked = repository.fetchLikedCommentIds("u1", ids).getOrThrow()

        assertEquals(2, requestedChunks.size)
        assertEquals(setOf("c3", "c40"), liked)
    }

    @Test
    fun `an empty id list costs no query`() = runTest {
        val counts = repository.fetchLikeCounts(emptyList()).getOrThrow()

        assertTrue(counts.isEmpty())
        assertTrue(requestedChunks.isEmpty())
    }

    @Test
    fun `a signed-out user has no liked comments`() = runTest {
        val liked = repository.fetchLikedCommentIds("", listOf("c1")).getOrThrow()

        assertTrue(liked.isEmpty())
        assertTrue(requestedChunks.isEmpty())
    }
}
