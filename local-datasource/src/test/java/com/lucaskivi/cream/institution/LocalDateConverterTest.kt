package com.lucaskivi.cream.institution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Unit tests for [LocalDateConverter].
 */
class LocalDateConverterTest {

    private val converter = LocalDateConverter()

    // region fromLocalDate

    @Test
    fun `fromLocalDate converts date to ISO-8601 string`() {
        assertEquals("2024-03-15", converter.fromLocalDate(LocalDate.of(2024, 3, 15)))
    }

    @Test
    fun `fromLocalDate returns null for null input`() {
        assertNull(converter.fromLocalDate(null))
    }

    // endregion

    // region toLocalDate

    @Test
    fun `toLocalDate parses ISO-8601 string to LocalDate`() {
        assertEquals(LocalDate.of(2024, 3, 15), converter.toLocalDate("2024-03-15"))
    }

    @Test
    fun `toLocalDate returns null for null input`() {
        assertNull(converter.toLocalDate(null))
    }

    // endregion

    // region round-trip

    @Test
    fun `round-trip fromLocalDate then toLocalDate returns original date`() {
        val original = LocalDate.of(2024, 12, 31)
        assertEquals(original, converter.toLocalDate(converter.fromLocalDate(original)))
    }

    // endregion
}
