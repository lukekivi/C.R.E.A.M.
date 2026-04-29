package com.lucaskivi.cream.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CancellationException

class NetworkResultTest {

    @Nested
    inner class Catching {

        @Test
        fun `returns Success when block completes normally`() {
            val result = catching { 42 }
            assertTrue(result is NetworkResult.Success)
            assertEquals(42, (result as NetworkResult.Success).value)
        }

        @Test
        fun `wraps non-cancellation exception in Failure`() {
            val cause = RuntimeException("boom")
            val result = catching<Unit> { throw cause }
            assertTrue(result is NetworkResult.Failure)
            assertSame(cause, (result as NetworkResult.Failure).cause)
        }

        @Test
        fun `rethrows CancellationException`() {
            assertThrows<CancellationException> {
                catching<Unit> { throw CancellationException("cancelled") }
            }
        }
    }
}
