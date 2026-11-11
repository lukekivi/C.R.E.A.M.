package com.lucaskivi.cream.ui.format

import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.FinancialAccountType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FinancialAccountFormattingTest {

    @Nested
    inner class ToLabel {

        @Test
        fun `capitalizes only the first letter`() {
            // Test
            val label = FinancialAccountType.DEPOSITORY.toLabel()

            // Verify
            assertEquals("Depository", label)
        }
    }

    @Nested
    inner class FormattedDetails {

        @Test
        fun `puts the masked number before the type`() {
            // Test
            val details = account(mask = "1234").formattedDetails()

            // Verify
            assertEquals("•••• 1234 · Credit", details)
        }

        @Test
        fun `shows only the type when the mask is unknown`() {
            // Test
            val details = account(mask = null).formattedDetails()

            // Verify
            assertEquals("Credit", details)
        }
    }

    @Nested
    inner class FormattedBalance {

        @Test
        fun `shows the placeholder when the balance is unknown`() {
            // Test
            val balance = account(mask = null).formattedBalance()

            // Verify
            assertEquals(MISSING_AMOUNT_PLACEHOLDER, balance)
        }
    }

    private fun account(
        mask: String?,
    ): FinancialAccount =
        FinancialAccount(
            accountId = "acc1",
            itemId = "item1",
            displayName = "Sapphire",
            mask = mask,
            type = FinancialAccountType.CREDIT,
            subtype = "credit card",
            balanceCurrent = null,
            balanceAvailable = null,
            isoCurrencyCode = "USD",
        )
}
