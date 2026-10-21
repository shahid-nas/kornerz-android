package com.kornerz.feature.community.data.network

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CommunitiesFirestoreApi.generateCode] (P7, task 10.1).
 */
class CommunitiesFirestoreApiTest {

    private val api = CommunitiesFirestoreApi(firestore = mockk(relaxed = true))

    @Test
    fun `generateCode returns a 6-character alphanumeric string`() {
        val code = api.generateCode()
        assertEquals(6, code.length)
        assertTrue(code.all { it.isLetterOrDigit() })
        assertTrue(code == code.uppercase())
    }
}
