package com.lucaskivi.cream.screens.home

import com.lucaskivi.cream.data.model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HomeUiTransformerTest {

    private val transformer = HomeUiTransformer()

    @Nested
    inner class ToProfileSectionData {

        @Test
        fun `uses the display name`() {
            // Setup
            val user = User("uid", "jane@example.com", "jane")
            val expected = ProfileSectionData("J", "jane")

            // Test
            val result = transformer.toProfileSectionData(user)

            // Verify
            assertEquals(expected, result)
        }

        @Test
        fun `falls back to the email without a display name`() {
            // Setup
            val user = User("uid", "jane@example.com", null)
            val expected = ProfileSectionData("J", "jane@example.com")

            // Test
            val result = transformer.toProfileSectionData(user)

            // Verify
            assertEquals(expected, result)
        }

        @Test
        fun `falls back to placeholders without a user`() {
            // Setup
            val expected = ProfileSectionData("?", "there")

            // Test
            val result = transformer.toProfileSectionData(null)

            // Verify
            assertEquals(expected, result)
        }
    }
}
