package com.lucaskivi.cream.screens.institutions

import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.FinancialAccountType
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.ui.format.CurrencyFormatter
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class InstitutionsUiTransformerTest {

    private val currencyFormatter: CurrencyFormatter = mockk()
    private val transformer = InstitutionsUiTransformer(currencyFormatter)
    private val chase = LinkedInstitution("item1", "ins_1", "Chase")
    private val checking = FinancialAccount(
        accountId = "acc1",
        itemId = "item1",
        displayName = "Total Checking",
        mask = "1234",
        type = FinancialAccountType.DEPOSITORY,
        subtype = "checking",
        balanceCurrent = 1234.5,
        balanceAvailable = null,
        isoCurrencyCode = "USD",
    )

    @AfterEach
    fun tearDown() {
        confirmVerified(currencyFormatter)
    }

    @Nested
    inner class ToInstitutionItemData {

        @Test
        fun `shows the institution's name and each of its accounts`() {
            // Setup
            every { currencyFormatter.format(1234.5, "USD", any()) } returns "$1,234.50"
            val institutionWithAccounts = LinkedInstitutionWithAccounts(chase, listOf(checking))
            val expected = InstitutionItemData(
                itemId = "item1",
                name = "Chase",
                accounts = listOf(AccountItemData("acc1", "Total Checking", "•••• 1234 · Depository", "$1,234.50")),
                institution = chase,
            )

            // Test
            val result = transformer.toInstitutionItemData(institutionWithAccounts)

            // Verify
            assertEquals(expected, result)
            verify { currencyFormatter.format(1234.5, "USD", any()) }
        }
    }

    @Nested
    inner class ToAccountItemData {

        @Test
        fun `shows the masked number, type, and formatted balance`() {
            // Setup
            every { currencyFormatter.format(1234.5, "USD", any()) } returns "$1,234.50"
            val expected = AccountItemData("acc1", "Total Checking", "•••• 1234 · Depository", "$1,234.50")

            // Test
            val result = transformer.toAccountItemData(checking)

            // Verify
            assertEquals(expected, result)
            verify { currencyFormatter.format(1234.5, "USD", any()) }
        }

        @Test
        fun `shows only the type when there's no mask`() {
            // Setup
            val account = checking.copy(
                mask = null,
            )
            every { currencyFormatter.format(1234.5, "USD", any()) } returns "$1,234.50"

            // Test
            val result = transformer.toAccountItemData(account)

            // Verify
            assertEquals("Depository", result.details)
            verify { currencyFormatter.format(1234.5, "USD", any()) }
        }
    }

    @Nested
    inner class ToConfirmDisconnect {

        @Test
        fun `names the institution and carries it as the payload`() {
            // Setup
            val expected = InstitutionsDialog.ConfirmDisconnect("Chase", chase)

            // Test
            val result = transformer.toConfirmDisconnect(chase)

            // Verify
            assertEquals(expected, result)
        }
    }

    @Nested
    inner class ToDisconnectFailedMessage {

        @Test
        fun `names the institution`() {
            // Test
            val result = transformer.toDisconnectFailedMessage(chase)

            // Verify
            assertEquals("Couldn't disconnect Chase. Please try again.", result)
        }
    }
}
