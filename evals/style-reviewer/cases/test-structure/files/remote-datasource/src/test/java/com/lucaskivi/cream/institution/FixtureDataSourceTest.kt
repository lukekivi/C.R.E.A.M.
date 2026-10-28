package com.lucaskivi.cream.institution

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FixtureDataSourceTest {

    private val transformer = mockk<InstitutionTransformer>()

    // region toLinkToken

    @Test
    fun `returns the link token`() = runTest {
        every { transformer.toLinkToken(any()) } returns "lt-abc"
        val result = transformer.toLinkToken(mapOf("linkToken" to "lt-abc"))
        assertEquals("lt-abc", result)
        verify { transformer.toLinkToken(any()) }
    }

    // endregion
}
