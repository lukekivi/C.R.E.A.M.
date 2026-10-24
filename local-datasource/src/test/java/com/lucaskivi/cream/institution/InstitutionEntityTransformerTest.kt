package com.lucaskivi.cream.institution

import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.FinancialAccountType
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.core.institution.model.Transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class InstitutionEntityTransformerTest {

    private val transformer = InstitutionEntityTransformer()

    @Nested
    inner class ToLinkedInstitution {

        @Test
        fun `maps all fields`() {
            // Setup
            val entity = LinkedInstitutionEntity(
                itemId = "item1",
                institutionId = "ins_3",
                institutionName = "Chase",
                transactionCursor = "cursor-abc",
            )

            // Test
            val domain = transformer.toLinkedInstitution(entity)

            // Verify
            assertEquals("item1", domain.itemId)
            assertEquals("ins_3", domain.institutionId)
            assertEquals("Chase", domain.institutionName)
        }
    }

    @Nested
    inner class ToLinkedInstitutionWithAccounts {

        @Test
        fun `maps institution and accounts`() {
            // Setup
            val institutionEntity = LinkedInstitutionEntity(
                itemId = "item1",
                institutionId = "ins_3",
                institutionName = "Chase",
                transactionCursor = null,
            )
            val accountEntity = FinancialAccountEntity(
                accountId = "acc1",
                itemId = "item1",
                displayName = "Checking",
                mask = null,
                type = "DEPOSITORY",
                subtype = null,
                balanceCurrent = 500.0,
                balanceAvailable = null,
                isoCurrencyCode = "USD",
            )
            val relation = InstitutionWithAccountsEntity(
                institution = institutionEntity,
                accounts = listOf(accountEntity),
            )

            // Test
            val domain = transformer.toLinkedInstitutionWithAccounts(relation)

            // Verify
            assertEquals("item1", domain.institution.itemId)
            assertEquals("Chase", domain.institution.institutionName)
            assertEquals(1, domain.accounts.size)
            assertEquals("acc1", domain.accounts[0].accountId)
            assertEquals(FinancialAccountType.DEPOSITORY, domain.accounts[0].type)
        }

        @Test
        fun `handles empty accounts list`() {
            // Setup
            val relation = InstitutionWithAccountsEntity(
                institution = LinkedInstitutionEntity("item1", "ins_3", "Chase", null),
                accounts = emptyList(),
            )

            // Test
            val domain = transformer.toLinkedInstitutionWithAccounts(relation)

            // Verify
            assertEquals(0, domain.accounts.size)
        }
    }

    @Nested
    inner class ToFinancialAccount {

        @Test
        fun `maps all fields including type`() {
            // Setup
            val entity = FinancialAccountEntity(
                accountId = "acc1",
                itemId = "item1",
                displayName = "Total Checking",
                mask = "1234",
                type = "DEPOSITORY",
                subtype = "checking",
                balanceCurrent = 1000.0,
                balanceAvailable = 950.0,
                isoCurrencyCode = "USD",
            )

            // Test
            val domain = transformer.toFinancialAccount(entity)

            // Verify
            assertEquals("acc1", domain.accountId)
            assertEquals("item1", domain.itemId)
            assertEquals("Total Checking", domain.displayName)
            assertEquals("1234", domain.mask)
            assertEquals(FinancialAccountType.DEPOSITORY, domain.type)
            assertEquals("checking", domain.subtype)
            assertEquals(1000.0, domain.balanceCurrent!!, 0.0)
            assertEquals(950.0, domain.balanceAvailable!!, 0.0)
            assertEquals("USD", domain.isoCurrencyCode)
        }

        @Test
        fun `returns OTHER for unknown type string`() {
            // Setup
            val entity = FinancialAccountEntity(
                accountId = "acc1",
                itemId = "item1",
                displayName = "Unknown",
                mask = null,
                type = "BROKERAGE",
                subtype = null,
                balanceCurrent = null,
                balanceAvailable = null,
                isoCurrencyCode = null,
            )

            // Test
            val domain = transformer.toFinancialAccount(entity)

            // Verify
            assertEquals(FinancialAccountType.OTHER, domain.type)
        }

        @Test
        fun `handles null optional fields`() {
            // Setup
            val entity = FinancialAccountEntity(
                accountId = "acc2",
                itemId = "item2",
                displayName = "Savings",
                mask = null,
                type = "OTHER",
                subtype = null,
                balanceCurrent = null,
                balanceAvailable = null,
                isoCurrencyCode = null,
            )

            // Test
            val domain = transformer.toFinancialAccount(entity)

            // Verify
            assertNull(domain.mask)
            assertNull(domain.subtype)
            assertNull(domain.balanceCurrent)
            assertNull(domain.balanceAvailable)
            assertNull(domain.isoCurrencyCode)
        }
    }

    @Nested
    inner class ToTransaction {

        @Test
        fun `maps all fields`() {
            // Setup
            val date = LocalDate.of(2024, 3, 15)
            val entity = TransactionEntity(
                transactionId = "tx1",
                accountId = "acc1",
                amount = 12.50,
                isoCurrencyCode = "USD",
                date = date,
                name = "Coffee Shop",
                merchantName = "Blue Bottle",
                pending = false,
                category = "Food and Drink",
            )

            // Test
            val domain = transformer.toTransaction(entity)

            // Verify
            assertEquals("tx1", domain.transactionId)
            assertEquals("acc1", domain.accountId)
            assertEquals(12.50, domain.amount, 0.0)
            assertEquals("USD", domain.isoCurrencyCode)
            assertEquals(date, domain.date)
            assertEquals("Coffee Shop", domain.name)
            assertEquals("Blue Bottle", domain.merchantName)
            assertEquals(false, domain.pending)
            assertEquals("Food and Drink", domain.category)
        }

        @Test
        fun `handles null optional fields`() {
            // Setup
            val entity = TransactionEntity(
                transactionId = "tx2",
                accountId = "acc1",
                amount = 5.0,
                isoCurrencyCode = null,
                date = LocalDate.of(2024, 1, 1),
                name = "Unknown",
                merchantName = null,
                pending = true,
                category = null,
            )

            // Test
            val domain = transformer.toTransaction(entity)

            // Verify
            assertNull(domain.isoCurrencyCode)
            assertNull(domain.merchantName)
            assertNull(domain.category)
        }
    }

    @Nested
    inner class ToLinkedInstitutionEntity {

        @Test
        fun `maps all fields with a null transaction cursor`() {
            // Setup
            val domain = LinkedInstitution(
                itemId = "item1",
                institutionId = "ins_3",
                institutionName = "Chase",
            )

            // Test
            val entity = transformer.toLinkedInstitutionEntity(domain)

            // Verify
            assertEquals("item1", entity.itemId)
            assertEquals("ins_3", entity.institutionId)
            assertEquals("Chase", entity.institutionName)
            assertNull(entity.transactionCursor)
        }

        @Test
        fun `round-trips through toLinkedInstitution`() {
            // Setup
            val original = LinkedInstitution(
                itemId = "item1",
                institutionId = "ins_3",
                institutionName = "Chase",
            )

            // Test
            val entity = transformer.toLinkedInstitutionEntity(original)
            val roundTripped = transformer.toLinkedInstitution(entity)

            // Verify
            assertEquals(original, roundTripped)
        }
    }

    @Nested
    inner class ToFinancialAccountEntity {

        @Test
        fun `stores type as enum name`() {
            // Setup
            val domain = FinancialAccount(
                accountId = "acc1",
                itemId = "item1",
                displayName = "Savings",
                mask = null,
                type = FinancialAccountType.CREDIT,
                subtype = null,
                balanceCurrent = null,
                balanceAvailable = null,
                isoCurrencyCode = null,
            )

            // Test
            val entity = transformer.toFinancialAccountEntity(domain)

            // Verify
            assertEquals("CREDIT", entity.type)
        }

        @Test
        fun `round-trips through toFinancialAccount for every type`() {
            for (type in FinancialAccountType.entries) {
                // Setup
                val original = FinancialAccount(
                    accountId = "acc",
                    itemId = "item",
                    displayName = "Acct",
                    mask = null,
                    type = type,
                    subtype = null,
                    balanceCurrent = null,
                    balanceAvailable = null,
                    isoCurrencyCode = null,
                )

                // Test
                val entity = transformer.toFinancialAccountEntity(original)
                val roundTripped = transformer.toFinancialAccount(entity)

                // Verify
                assertEquals(original, roundTripped, "Round-trip failed for $type")
            }
        }
    }

    @Nested
    inner class ToTransactionEntity {

        @Test
        fun `round-trips through toTransaction`() {
            // Setup
            val original = Transaction(
                transactionId = "tx1",
                accountId = "acc1",
                amount = 12.50,
                isoCurrencyCode = "USD",
                date = LocalDate.of(2024, 3, 15),
                name = "Coffee Shop",
                merchantName = "Blue Bottle",
                pending = false,
                category = "Food and Drink",
            )

            // Test
            val entity = transformer.toTransactionEntity(original)
            val roundTripped = transformer.toTransaction(entity)

            // Verify
            assertEquals(original, roundTripped)
        }
    }
}
