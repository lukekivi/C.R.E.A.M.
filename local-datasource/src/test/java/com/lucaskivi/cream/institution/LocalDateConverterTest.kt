package com.lucaskivi.cream.institution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class LocalDateConverterTest {

    private val converter = LocalDateConverter()

    @Nested
    inner class FromLocalDate {

        @Test
        fun `converts date to ISO-8601 string`() {
            // Test
            val result = converter.fromLocalDate(LocalDate.of(2024, 3, 15))

            // Verify
            assertEquals("2024-03-15", result)
        }

        @Test
        fun `returns null for null input`() {
            // Test
            val result = converter.fromLocalDate(null)

            // Verify
            assertNull(result)
        }
    }

    @Nested
    inner class ToLocalDate {

        @Test
        fun `parses ISO-8601 string to LocalDate`() {
            // Setup
            val expected = LocalDate.of(2024, 3, 15)

            // Test
            val result = converter.toLocalDate("2024-03-15")

            // Verify
            assertEquals(expected, result)
        }

        @Test
        fun `returns null for null input`() {
            // Test
            val result = converter.toLocalDate(null)

            // Verify
            assertNull(result)
        }
    }

    @Nested
    inner class RoundTrip {

        @Test
        fun `fromLocalDate then toLocalDate returns original date`() {
            // Setup
            val original = LocalDate.of(2024, 12, 31)

            // Test
            val result = converter.toLocalDate(converter.fromLocalDate(original))

            // Verify
            assertEquals(original, result)
        }
    }
}
