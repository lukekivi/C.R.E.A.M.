package com.lucaskivi.cream.ui.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Locale

class CurrencyFormattingTest {

    @Nested
    inner class FormatCurrency {

        @Test
        fun `formats US dollars`() {
            // Test
            val formatted = formatCurrency(1234.5, "USD", Locale.US)

            // Verify
            assertEquals("$1,234.50", formatted)
        }

        @Test
        fun `falls back to US dollars when the code is null`() {
            // Test
            val formatted = formatCurrency(1234.5, null, Locale.US)

            // Verify
            assertEquals("$1,234.50", formatted)
        }

        @Test
        fun `uses the given currency`() {
            // Test
            val formatted = formatCurrency(1234.5, "EUR", Locale.US)

            // Verify
            assertEquals("€1,234.50", formatted)
        }

        @Test
        fun `falls back to US dollars when the code isn't recognized`() {
            // Test
            val formatted = formatCurrency(1234.5, "NOT_A_CODE", Locale.US)

            // Verify
            assertEquals("$1,234.50", formatted)
        }

        @Test
        fun `keeps the sign of a negative amount`() {
            // Test
            val formatted = formatCurrency(-42.0, "USD", Locale.US)

            // Verify
            assertEquals("-$42.00", formatted)
        }

        @Test
        fun `uses the currency's own fraction digits`() {
            // Test
            val formatted = formatCurrency(1234.0, "JPY", Locale.US)

            // Verify
            assertEquals("¥1,234", formatted)
        }

        @Test
        fun `lays the amount out for the locale`() {
            // Test
            val formatted = formatCurrency(1234.5, "EUR", Locale.GERMANY)
            val normalized = formatted.replace('\u00A0', ' ')

            // Verify
            assertEquals("1.234,50 €", normalized)
        }
    }
}
