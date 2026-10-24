package com.kornerz.core.network.gamification

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.kornerz.core.model.Rank
import com.kornerz.core.model.User
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The gamification read path.
 *
 * The whole point of these cases is the **field names**: the P2
 * implementation read `gameficationPoints`, a field no server document
 * carries, so every score resolved to `0` and the leaderboard rendered
 * an empty table.
 */
class GamificationRepositoryTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val sessionRepository = mockk<SessionRepository>()
    private val gameficationCollection = mockk<CollectionReference>()
    private val usersCollection = mockk<CollectionReference>()

    private lateinit var repository: GamificationRepository

    @Before
    fun setUp() {
        every { firestore.collection("Gamefication") } returns gameficationCollection
        every { gameficationCollection.whereEqualTo(any<String>(), any<Any>()) } returns gameficationCollection
        every { gameficationCollection.whereGreaterThanOrEqualTo(any<String>(), any<Any>()) } returns gameficationCollection
        every { gameficationCollection.whereLessThan(any<String>(), any<Any>()) } returns gameficationCollection
        every { gameficationCollection.startAfter(any<DocumentSnapshot>()) } returns gameficationCollection

        every { firestore.collection("Users") } returns usersCollection
        every { usersCollection.whereIn(any<String>(), any<List<Any>>()) } returns usersCollection

        repository = GamificationRepository(firestore, sessionRepository)
    }

    private fun document(
        userId: String? = "user-1",
        points: Any? = 25,
    ): DocumentSnapshot {
        val document = mockk<DocumentSnapshot>()
        every { document.get("gameficationQtdPts") } returns points
        every { document.get("gameficationPoints") } returns null
        every { document.getString("gameficationUserId") } returns userId
        return document
    }

    /**
     * Serves [documents] as the **first** page and an empty page after
     * it — the read path pages with `startAfter` until a page comes
     * back empty, so a mock that always returns the same page would
     * never terminate.
     */
    private fun gameficationPage(documents: List<DocumentSnapshot>) {
        val pages = ArrayDeque(listOf(documents, emptyList<DocumentSnapshot>()))
        every { gameficationCollection.get() } answers {
            val page = if (pages.isEmpty()) emptyList() else pages.removeFirst()
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns page
            val task = TaskCompletionSource<QuerySnapshot>()
            task.setResult(snapshot)
            task.task
        }
    }

    private fun usersPage(entries: Map<String, String>) {
        val documents = entries.map { (code, nickname) ->
            val document = mockk<DocumentSnapshot>()
            every { document.getString("codeUser") } returns code
            every { document.getString("nickname") } returns nickname
            document
        }
        val snapshot = mockk<QuerySnapshot>()
        every { snapshot.documents } returns documents
        val task = TaskCompletionSource<QuerySnapshot>()
        task.setResult(snapshot)
        every { usersCollection.get() } returns task.task
    }

    @Test
    fun `getUserPoints sums the authoritative gameficationQtdPts field`() = runTest {
        every { sessionRepository.data } returns flowOf(
            SessionState.Authenticated(User(id = "user-1", email = "a@example.com", displayName = "Ana", nickname = "Ana", userCode = "user-1")),
        )
        gameficationPage(listOf(document(points = 25), document(points = 40)))

        val result = repository.getUserPoints()

        assertTrue(result.isSuccess)
        assertEquals(65, result.getOrThrow())
    }

    @Test
    fun `getUserPoints falls back to gameficationPoints only when the authoritative field is absent`() =
        runTest {
            every { sessionRepository.data } returns flowOf(
                SessionState.Authenticated(User(id = "user-1", email = "a@example.com", displayName = "Ana", nickname = "Ana", userCode = "user-1")),
            )
            val document = mockk<DocumentSnapshot>()
            every { document.get("gameficationQtdPts") } returns null
            every { document.get("gameficationPoints") } returns 12
            every { document.getString("gameficationUserId") } returns "user-1"
            gameficationPage(listOf(document))

            assertEquals(12, repository.getUserPoints().getOrThrow())
        }

    @Test
    fun `the authoritative field wins when both spellings are present`() = runTest {
        every { sessionRepository.data } returns flowOf(
            SessionState.Authenticated(User(id = "user-1", email = "a@example.com", displayName = "Ana", nickname = "Ana", userCode = "user-1")),
        )
        val document = mockk<DocumentSnapshot>()
        every { document.get("gameficationQtdPts") } returns 25
        every { document.get("gameficationPoints") } returns 999
        every { document.getString("gameficationUserId") } returns "user-1"
        gameficationPage(listOf(document))

        assertEquals(25, repository.getUserPoints().getOrThrow())
    }

    @Test
    fun `getUserPoints returns zero for an anonymous session without querying Firestore`() = runTest {
        every { sessionRepository.data } returns flowOf(SessionState.Anonymous)

        val result = repository.getUserPoints()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow())
        verify(exactly = 0) { gameficationCollection.get() }
    }

    @Test
    fun `getUserPoints returns zero for a user with no codeUser without querying Firestore`() = runTest {
        every { sessionRepository.data } returns flowOf(
            SessionState.Authenticated(User(id = "user-1", email = "a@example.com", displayName = "Ana", nickname = "Ana", userCode = null)),
        )

        val result = repository.getUserPoints()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow())
        verify(exactly = 0) { gameficationCollection.get() }
    }

    @Test
    fun `getRanking aggregates per user and sorts by points descending`() = runTest {
        gameficationPage(
            listOf(
                document(userId = "u1", points = 10),
                document(userId = "u2", points = 40),
                document(userId = "u1", points = 15),
            ),
        )
        usersPage(mapOf("u1" to "Ana", "u2" to "Bo"))
        every { sessionRepository.data } returns flowOf(SessionState.Anonymous)

        val rows = repository.getRanking(10).getOrThrow()

        assertEquals(listOf("u2", "u1"), rows.map { it.userId })
        assertEquals(listOf(40, 25), rows.map { it.points })
        assertEquals(listOf("Bo", "Ana"), rows.map { it.userName })
    }

    @Test
    fun `getRanking assigns the medal tier by final position`() = runTest {
        gameficationPage(
            listOf(
                document(userId = "u1", points = 10),
                document(userId = "u2", points = 40),
                document(userId = "u3", points = 30),
                document(userId = "u4", points = 20),
            ),
        )
        usersPage(mapOf("u1" to "A", "u2" to "B", "u3" to "C", "u4" to "D"))
        every { sessionRepository.data } returns flowOf(SessionState.Anonymous)

        val rows = repository.getRanking(10).getOrThrow()

        assertEquals(
            listOf(Rank.GOLD, Rank.SILVER, Rank.BRONZE, Rank.NONE),
            rows.map { it.medal },
        )
    }

    @Test
    fun `getRanking truncates to the requested count`() = runTest {
        gameficationPage(
            listOf(
                document(userId = "u1", points = 10),
                document(userId = "u2", points = 40),
                document(userId = "u3", points = 30),
            ),
        )
        usersPage(mapOf("u1" to "A", "u2" to "B", "u3" to "C"))
        every { sessionRepository.data } returns flowOf(SessionState.Anonymous)

        val rows = repository.getRanking(2).getOrThrow()

        assertEquals(2, rows.size)
        assertEquals(listOf("u2", "u3"), rows.map { it.userId })
    }

    @Test
    fun `getRanking keeps the literal User for an id that does not resolve`() = runTest {
        gameficationPage(listOf(document(userId = "ghost", points = 5)))
        usersPage(emptyMap())
        every { sessionRepository.data } returns flowOf(SessionState.Anonymous)

        val rows = repository.getRanking(10).getOrThrow()

        assertEquals("User", rows.single().userName)
    }

    @Test
    fun `getRanking returns a failure rather than throwing when the query fails`() = runTest {
        every { gameficationCollection.get() } throws IllegalStateException("offline")
        every { sessionRepository.data } returns flowOf(SessionState.Anonymous)

        val result = repository.getRanking(10)

        assertTrue(result.isFailure)
    }

    @Test
    fun `addPoints writes a document with the ios field names`() = runTest {
        every { sessionRepository.data } returns flowOf(
            SessionState.Authenticated(User(id = "user-1", email = "a@example.com", displayName = "Ana", nickname = "Ana", userCode = "u1")),
        )
        val document = mockk<DocumentReference>()
        every { gameficationCollection.document() } returns document
        every { document.set(any<Map<String, Any?>>()) } returns TaskCompletionSource<Void>().apply {
            setResult(null)
        }.task

        val result = repository.addPoints(action = "ACCESS_LOOP", points = 1, referenceId = "l1")

        assertTrue(result.isSuccess)
        val captured = slot<Map<String, Any?>>()
        verify(exactly = 1) { document.set(capture(captured)) }
        assertEquals("ACCESS_LOOP", captured.captured["gameficationAction"])
        assertEquals(1, captured.captured["gameficationQtdPts"])
        assertEquals("u1", captured.captured["gameficationUserId"])
        assertEquals("l1", captured.captured["gameficationReferenceId"])
        assertTrue(captured.captured.containsKey("gameficationDateHora"))
    }

    @Test
    fun `addPoints is a no-op for an anonymous session`() = runTest {
        every { sessionRepository.data } returns flowOf(SessionState.Anonymous)

        val result = repository.addPoints(action = "ACCESS_LOOP", points = 1, referenceId = "l1")

        assertTrue(result.isSuccess)
        verify(exactly = 0) { gameficationCollection.document() }
    }

    @Test
    fun `addPoints is a no-op for a user without a codeUser`() = runTest {
        every { sessionRepository.data } returns flowOf(
            SessionState.Authenticated(User(id = "user-1", email = "a@example.com", displayName = "Ana", nickname = "Ana", userCode = null)),
        )

        val result = repository.addPoints(action = "ACCESS_LOOP", points = 1, referenceId = "l1")

        assertTrue(result.isSuccess)
        verify(exactly = 0) { gameficationCollection.document() }
    }

    @Test
    fun `addPoints reports a failed write rather than throwing`() = runTest {
        every { sessionRepository.data } returns flowOf(
            SessionState.Authenticated(User(id = "user-1", email = "a@example.com", displayName = "Ana", nickname = "Ana", userCode = "u1")),
        )
        val document = mockk<DocumentReference>()
        every { gameficationCollection.document() } returns document
        every { document.set(any<Map<String, Any?>>()) } throws IllegalStateException("offline")

        val result = repository.addPoints(action = "ACCESS_LOOP", points = 1, referenceId = "l1")

        assertTrue(result.isFailure)
    }

    /**
     * The write path and the read path must agree on the field names —
     * a `Gamefication` document Android writes has to be readable by the
     * P10 leaderboard.
     */
    @Test
    fun `the document written by addPoints is counted by the points read path`() = runTest {
        every { sessionRepository.data } returns flowOf(
            SessionState.Authenticated(User(id = "user-1", email = "a@example.com", displayName = "Ana", nickname = "Ana", userCode = "u1")),
        )
        val written = captureWrittenDocument()

        repository.addPoints(action = "ACCESS_LOOP", points = 1, referenceId = "l1")

        val payload = written.single()
        assertEquals(1, payload["gameficationQtdPts"])
        assertEquals("u1", payload["gameficationUserId"])
        assertEquals("ACCESS_LOOP", payload["gameficationAction"])
        assertEquals("l1", payload["gameficationReferenceId"])

        gameficationPage(payloadsAsDocuments(written))
        assertEquals(1, repository.getUserPoints().getOrThrow())
    }

    @Test
    fun `the document written by addPoints shows up in the leaderboard`() = runTest {
        every { sessionRepository.data } returns flowOf(
            SessionState.Authenticated(User(id = "user-1", email = "a@example.com", displayName = "Ana", nickname = "Ana", userCode = "u1")),
        )
        val written = captureWrittenDocument()

        repository.addPoints(action = "ACCESS_COMMENT", points = 3, referenceId = "l1")

        gameficationPage(payloadsAsDocuments(written))
        usersPage(mapOf("u1" to "Ana"))

        val rows = repository.getRanking(10).getOrThrow()

        assertEquals(listOf("u1"), rows.map { it.userId })
        assertEquals(listOf(3), rows.map { it.points })
        assertEquals(listOf("Ana"), rows.map { it.userName })
    }

    /** Captures the payload every `set` call writes. */
    private fun captureWrittenDocument(): MutableList<Map<String, Any?>> {
        val written = mutableListOf<Map<String, Any?>>()
        val document = mockk<DocumentReference>()
        every { gameficationCollection.document() } returns document
        every { document.set(any<Map<String, Any?>>()) } answers {
            written.add(firstArg<Map<String, Any?>>())
            TaskCompletionSource<Void>().apply { setResult(null) }.task
        }
        return written
    }

    /** Serves the written payloads back through the read path. */
    private fun payloadsAsDocuments(payloads: List<Map<String, Any?>>): List<DocumentSnapshot> =
        payloads.map { payload ->
            val document = mockk<DocumentSnapshot>()
            every { document.get("gameficationQtdPts") } returns payload["gameficationQtdPts"]
            every { document.get("gameficationPoints") } returns null
            every { document.getString("gameficationUserId") } returns payload["gameficationUserId"] as? String
            every { document.get("gameficationDateHora") } returns payload["gameficationDateHora"]
            document
        }
}
