package com.lucaskivi.cream.screens.institutions

import com.plaid.link.result.LinkError
import com.plaid.link.result.LinkExit
import com.plaid.link.result.LinkInstitution
import com.plaid.link.result.LinkSuccess
import com.plaid.link.result.LinkSuccessMetadata
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PlaidLinkLauncherTest {

    @Nested
    inner class ToPlaidLinkResult {

        @Test
        fun `maps a success with an institution to Success`() {
            // Setup
            val institution: LinkInstitution = mockk {
                every { id } returns "ins_3"
                every { name } returns "Chase"
            }
            val linkResult = linkSuccess(institution)
            val expected = PlaidLinkResult.Success(
                publicToken = "public-sandbox-123",
                institutionId = "ins_3",
                institutionName = "Chase",
            )

            // Test
            val result = linkResult.toPlaidLinkResult()

            // Verify
            assertEquals(expected, result)
        }

        @Test
        fun `maps a success without an institution to the fallback Error`() {
            // Setup
            val linkResult = linkSuccess(institution = null)
            val expected = PlaidLinkResult.Error(PLAID_LINK_FALLBACK_ERROR_MESSAGE)

            // Test
            val result = linkResult.toPlaidLinkResult()

            // Verify
            assertEquals(expected, result)
        }

        @Test
        fun `maps an exit without an error to Cancelled`() {
            // Setup
            val linkResult: LinkExit = mockk {
                every { error } returns null
            }

            // Test
            val result = linkResult.toPlaidLinkResult()

            // Verify
            assertEquals(PlaidLinkResult.Cancelled, result)
        }

        @Test
        fun `uses the display message when present`() {
            // Setup
            val linkResult = linkExit(errorMessage = "internal failure", displayMessage = "Try again later")
            val expected = PlaidLinkResult.Error("Try again later")

            // Test
            val result = linkResult.toPlaidLinkResult()

            // Verify
            assertEquals(expected, result)
        }

        @Test
        fun `falls back to the error message when the display message is null`() {
            // Setup
            val linkResult = linkExit(errorMessage = "internal failure", displayMessage = null)
            val expected = PlaidLinkResult.Error("internal failure")

            // Test
            val result = linkResult.toPlaidLinkResult()

            // Verify
            assertEquals(expected, result)
        }

        @Test
        fun `falls back to the error message when the display message is blank`() {
            // Setup
            val linkResult = linkExit(errorMessage = "internal failure", displayMessage = "  ")
            val expected = PlaidLinkResult.Error("internal failure")

            // Test
            val result = linkResult.toPlaidLinkResult()

            // Verify
            assertEquals(expected, result)
        }

        @Test
        fun `falls back to the generic message when both messages are blank`() {
            // Setup
            val linkResult = linkExit(errorMessage = "  ", displayMessage = null)
            val expected = PlaidLinkResult.Error(PLAID_LINK_FALLBACK_ERROR_MESSAGE)

            // Test
            val result = linkResult.toPlaidLinkResult()

            // Verify
            assertEquals(expected, result)
        }
    }

    private fun linkSuccess(institution: LinkInstitution?): LinkSuccess {
        val metadata: LinkSuccessMetadata = mockk {
            every { this@mockk.institution } returns institution
        }
        return mockk {
            every { publicToken } returns "public-sandbox-123"
            every { this@mockk.metadata } returns metadata
        }
    }

    private fun linkExit(
        errorMessage: String,
        displayMessage: String?,
    ): LinkExit {
        val error: LinkError = mockk {
            every { this@mockk.errorMessage } returns errorMessage
            every { this@mockk.displayMessage } returns displayMessage
        }
        return mockk {
            every { this@mockk.error } returns error
        }
    }
}
