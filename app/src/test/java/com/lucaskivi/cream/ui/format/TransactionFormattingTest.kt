package com.lucaskivi.cream.ui.format

import com.lucaskivi.cream.core.institution.model.Transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Locale

class TransactionFormattingTest {

    @Nested
    inner class IsInflow {

        @Test
        fun `is true for a negative amount`() {
            // Test
            val isInflow = transaction(amount = -25.0).isInflow

            // Verify
            assertTrue(isInflow)
        }

        @Test
        fun `is false for a positive amount`() {
            // Test
            val isInflow = transaction(amount = 10.0).isInflow

            // Verify
            assertFalse(isInflow)
        }
    }

    @Nested
    inner class FormattedSignedAmount {

        @Test
        fun `shows money out with a minus sign`() {
            // Test
            val formatted = transaction(amount = 10.0).formattedSignedAmount("USD", Locale.US)

            // Verify
            assertEquals("-$10.00", formatted)
        }

        @Test
        fun `shows money in with a plus sign`() {
            // Test
            val formatted = transaction(amount = -25.5).formattedSignedAmount("USD", Locale.US)

            // Verify
            assertEquals("+$25.50", formatted)
        }

        @Test
        fun `prefers the transaction's own currency`() {
            // Test
            val formatted = transaction(amount = 5.0, isoCurrencyCode = "EUR").formattedSignedAmount("USD", Locale.US)

            // Verify
            assertEquals("-€5.00", formatted)
        }

        @Test
        fun `falls back to the given currency`() {
            // Test
            val formatted = transaction(amount = 5.0, isoCurrencyCode = null).formattedSignedAmount("EUR", Locale.US)

            // Verify
            assertEquals("-€5.00", formatted)
        }
    }

    private fun transaction(
        amount: Double,
        isoCurrencyCode: String? = "USD",
    ): Transaction =
        Transaction(
            transactionId = "t1",
            accountId = "acc1",
            amount = amount,
            isoCurrencyCode = isoCurrencyCode,
            date = LocalDate.of(2026, 11, 9),
            name = "COFFEE SHOP 123",
            merchantName = "Coffee Shop",
            pending = false,
            category = null,
        )
}
