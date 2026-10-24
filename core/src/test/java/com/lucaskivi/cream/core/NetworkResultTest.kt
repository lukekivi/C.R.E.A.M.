package com.lucaskivi.cream.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
            // Test
            val result = catching { 42 }

            // Verify
            assertTrue(result is NetworkResult.Success)
            assertEquals(42, (result as NetworkResult.Success).value)
        }

        @Test
        fun `wraps non-cancellation exception in Failure`() {
            // Setup
            val cause = RuntimeException("boom")

            // Test
            val result = catching<Unit> { throw cause }

            // Verify
            assertTrue(result is NetworkResult.Failure)
            assertSame(cause, (result as NetworkResult.Failure).cause)
        }

        @Test
        fun `rethrows CancellationException`() {
            // Verify
            assertThrows<CancellationException> {
                catching<Unit> { throw CancellationException("cancelled") }
            }
        }
    }

    @Nested
    inner class FlatMap {

        @Test
        fun `returns the transformed result on Success`() {
            // Setup
            val expected = NetworkResult.Success(2)

            // Test
            val result = NetworkResult.Success(1).flatMap { NetworkResult.Success(it + 1) }

            // Verify
            assertEquals(expected, result)
        }

        @Test
        fun `returns the transformed failure on Success`() {
            // Setup
            val failure = NetworkResult.Failure(RuntimeException("boom"))

            // Test
            val result = NetworkResult.Success(1).flatMap { failure }

            // Verify
            assertSame(failure, result)
        }

        @Test
        fun `returns the original failure without calling transform on Failure`() {
            // Setup
            val failure = NetworkResult.Failure(RuntimeException("boom"))
            var called = false

            // Test
            val result = failure.flatMap {
                called = true
                NetworkResult.Success(Unit)
            }

            // Verify
            assertSame(failure, result)
            assertFalse(called)
        }
    }
}
