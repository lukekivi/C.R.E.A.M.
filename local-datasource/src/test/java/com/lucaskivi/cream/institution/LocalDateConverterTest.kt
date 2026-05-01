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
            assertEquals("2024-03-15", converter.fromLocalDate(LocalDate.of(2024, 3, 15)))
        }

        @Test
        fun `returns null for null input`() {
            assertNull(converter.fromLocalDate(null))
        }
    }

    @Nested
    inner class ToLocalDate {

        @Test
        fun `parses ISO-8601 string to LocalDate`() {
            assertEquals(LocalDate.of(2024, 3, 15), converter.toLocalDate("2024-03-15"))
        }

        @Test
        fun `returns null for null input`() {
            assertNull(converter.toLocalDate(null))
        }
    }

    @Nested
    inner class RoundTrip {

        @Test
        fun `fromLocalDate then toLocalDate returns original date`() {
            val original = LocalDate.of(2024, 12, 31)
            assertEquals(original, converter.toLocalDate(converter.fromLocalDate(original)))
        }
    }
}
