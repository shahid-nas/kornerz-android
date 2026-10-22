package com.kornerz.core.storage.session

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.kornerz.core.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlinx.datetime.Instant

/**
 * Covers `SessionRepository.updateUser(transform)` (P8), the only new write
 * path in `core/storage`.
 *
 * Exercised over a **real** file-backed DataStore in a temp directory rather
 * than a fake, because the guarantees under test are properties of the write
 * itself: that `token` and `expiresAt` survive byte-identical, that the write
 * is atomic, and that a successful patch never produces a transient
 * `SessionState.Anonymous` on the way through. A mocked `DataStore` would
 * assert nothing about any of that.
 *
 * The DataStore file backend is pure JVM (okio + `java.io.File`), so this runs
 * on the plain JUnit source set with no Robolectric or instrumentation
 * harness — matching the codebase's only existing test pattern.
 */
class SessionRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var storeScope: CoroutineScope
    private lateinit var repository: SessionRepository

    @Before
    fun setUp() {
        tempDir = File.createTempFile("kornerz-session", ".dir").let { file ->
            file.delete()
            file.mkdirs()
            file
        }
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = SessionRepository(
            DataStoreFactory.create(
                serializer = SessionSerializer,
                produceFile = { File(tempDir, SessionRepository.FILE_NAME) },
                corruptionHandler = ReplaceFileCorruptionHandler { anonymousRecord() },
                scope = storeScope,
            ),
        )
    }

    @After
    fun tearDown() {
        storeScope.coroutineContext[Job]?.cancel()
        tempDir.deleteRecursively()
    }

    @Test
    fun `updateUser preserves the token and expiresAt verbatim`() = runTest {
        repository.setSession(TOKEN, EXPIRY, user())

        repository.updateUser { it.copy(subscriptionPlatform = "Android") }

        val record = repository.rawRecord()
        assertEquals(TOKEN, record.token)
        assertEquals(EXPIRY.toString(), record.expiresAt)
        assertEquals("Android", record.user.subscriptionPlatform)
    }

    @Test
    fun `updateUser keeps the session authenticated`() = runTest {
        repository.setSession(TOKEN, EXPIRY, user())

        repository.updateUser { it.copy(subscriptionType = "video_call_yearly_sub") }

        val state = repository.data.first()
        assertTrue("expected Authenticated, got $state", state is SessionState.Authenticated)
        assertEquals(
            "video_call_yearly_sub",
            (state as SessionState.Authenticated).user.subscriptionType,
        )
    }

    @Test
    fun `updateUser emits no transient Anonymous`() = runTest {
        repository.setSession(TOKEN, EXPIRY, user())

        val observed = mutableListOf<SessionState>()
        val collector = backgroundScope.launch(Dispatchers.Default) {
            repository.data.collect { observed += it }
        }
        // Let the collector attach before the write so the patch's own
        // emission is observed, not just the pre-patch state.
        withTimeout(5_000) { while (observed.isEmpty()) delay(10) }
        val before = observed.size

        repository.updateUser { it.copy(subscriptionPlatform = "Android") }

        withTimeout(5_000) { while (observed.size <= before) delay(10) }
        collector.cancel()

        assertTrue(
            "observed a transient Anonymous: $observed",
            observed.none { it is SessionState.Anonymous },
        )
    }

    @Test
    fun `updateUser on an anonymous record leaves it anonymous`() = runTest {
        repository.clearSession()

        val result = repository.updateUser { it.copy(subscriptionPlatform = "Android") }

        assertTrue(result.isSuccess)
        val state = repository.data.first()
        assertTrue("expected Anonymous, got $state", state is SessionState.Anonymous)
    }

    @Test
    fun `updateUser returns failure rather than throwing when the store is broken`() =
        runTest {
            // A serializer that always throws stands in for a corrupt or
            // unwritable file: the method must surface a Result, never
            // propagate the exception into its caller.
            // A *directory* where the data file is expected: DataStore will
            // happily create missing parent directories, so a bad path is
            // not a failure — an unopenable path is.
            val occupied = File(tempDir, "occupied.json").also { it.mkdirs() }
            val brokenRepository = SessionRepository(
                DataStoreFactory.create(
                    serializer = SessionSerializer,
                    produceFile = { occupied },
                    corruptionHandler = ReplaceFileCorruptionHandler { anonymousRecord() },
                    scope = storeScope,
                ),
            )

            val result = brokenRepository.updateUser { it }

            assertTrue("expected Result.failure, got $result", result.isFailure)
        }

    @Test
    fun `a premium purchase survives the DataStore round trip`() = runTest {
        // The scenario `session-storage` calls out explicitly: kill and
        // relaunch must not lose the entitlement.
        repository.setSession(TOKEN, EXPIRY, user())
        repository.updateUser {
            it.copy(
                subscriptionExpiryDate = "2999-12-01T00:00:00Z",
                subscriptionType = "video_call_yearly_sub",
                subscriptionPlatform = "Android",
            )
        }

        val restored = repository.data.first()
        assertTrue(restored is SessionState.Authenticated)
        val restoredUser = (restored as SessionState.Authenticated).user
        assertEquals("2999-12-01T00:00:00Z", restoredUser.subscriptionExpiryDate)
        assertEquals("video_call_yearly_sub", restoredUser.subscriptionType)
        assertEquals("Android", restoredUser.subscriptionPlatform)
        assertTrue("a future expiry must read as premium", restoredUser.isUserPremiumNow)
    }

    @Test
    fun `the anonymous detection invariants are unchanged by updateUser`() = runTest {
        // Past expiry ⇒ Anonymous, even with a non-empty token and user id.
        // An absolute past instant, not `EXPIRY - 1h` — the suite's EXPIRY
        // is year 2100, so subtracting an hour would still be in the future.
        repository.setSession(TOKEN, PAST_EXPIRY, user())
        assertTrue(repository.data.first() is SessionState.Anonymous)

        // Empty token ⇒ Anonymous.
        repository.setSession("", EXPIRY, user())
        assertTrue(repository.data.first() is SessionState.Anonymous)

        // Empty user id ⇒ Anonymous.
        repository.setSession(TOKEN, EXPIRY, user().copy(id = ""))
        assertTrue(repository.data.first() is SessionState.Anonymous)

        // A patch over each of those must not change the verdict.
        repository.updateUser { it.copy(subscriptionPlatform = "Android") }
        assertTrue(repository.data.first() is SessionState.Anonymous)
    }

    // ---- helpers --------------------------------------------------------

    private fun user() = User(
        id = "u1",
        email = "satoshi@example.com",
        displayName = "Satoshi Black",
        nickname = "satoshi",
    )

    private fun anonymousRecord() = SessionRecord(
        token = "",
        expiresAt = "",
        user = User(id = "", email = "", displayName = "", nickname = ""),
    )

    /** Reads the persisted record back, bypassing the state projection. */
    private suspend fun SessionRepository.rawRecord(): SessionRecord {
        val json = File(tempDir, SessionRepository.FILE_NAME).readText()
        return Json.decodeFromString(SessionRecord.serializer(), json)
    }

    private companion object {
        const val TOKEN = "jwt-token-value"

        /** Year 2100 — comfortably in the future for any plausible run. */
        val EXPIRY: Instant = Instant.fromEpochSeconds(4_102_444_800)

        /** Epoch — unambiguously in the past. */
        val PAST_EXPIRY: Instant = Instant.fromEpochSeconds(0)
    }
}