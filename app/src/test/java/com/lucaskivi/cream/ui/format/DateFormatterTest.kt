package com.lucaskivi.cream.ui.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Locale

class DateFormatterTest {

    private val formatter = DateFormatter()

    @Nested
    inner class Format {

        @Test
        fun `formats in the medium style of the locale`() {
            // Setup
            val date = LocalDate.of(2026, 11, 14)

            // Test
            val formatted = formatter.format(date, Locale.US)

            // Verify
            assertEquals("Nov 14, 2026", formatted)
        }
    }
}
