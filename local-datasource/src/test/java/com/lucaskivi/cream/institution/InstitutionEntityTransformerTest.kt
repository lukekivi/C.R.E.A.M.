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
            val entity = LinkedInstitutionEntity(
                itemId = "item1",
                institutionId = "ins_3",
                institutionName = "Chase",
            )
            val domain = transformer.toLinkedInstitution(entity)
            assertEquals("item1", domain.itemId)
            assertEquals("ins_3", domain.institutionId)
            assertEquals("Chase", domain.institutionName)
        }
    }

    @Nested
    inner class ToLinkedInstitutionWithAccounts {

        @Test
        fun `maps institution and accounts`() {
            val institutionEntity = LinkedInstitutionEntity(
                itemId = "item1",
                institutionId = "ins_3",
                institutionName = "Chase",
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
            val domain = transformer.toLinkedInstitutionWithAccounts(relation)
            assertEquals("item1", domain.institution.itemId)
            assertEquals("Chase", domain.institution.institutionName)
            assertEquals(1, domain.accounts.size)
            assertEquals("acc1", domain.accounts[0].accountId)
            assertEquals(FinancialAccountType.DEPOSITORY, domain.accounts[0].type)
        }

        @Test
        fun `handles empty accounts list`() {
            val relation = InstitutionWithAccountsEntity(
                institution = LinkedInstitutionEntity("item1", "ins_3", "Chase"),
                accounts = emptyList(),
            )
            assertEquals(0, transformer.toLinkedInstitutionWithAccounts(relation).accounts.size)
        }
    }

    @Nested
    inner class ToFinancialAccount {

        @Test
        fun `maps all fields including type`() {
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
            val domain = transformer.toFinancialAccount(entity)
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
            assertEquals(FinancialAccountType.OTHER, transformer.toFinancialAccount(entity).type)
        }

        @Test
        fun `handles null optional fields`() {
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
            val domain = transformer.toFinancialAccount(entity)
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
            val domain = transformer.toTransaction(entity)
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
            val domain = transformer.toTransaction(entity)
            assertNull(domain.isoCurrencyCode)
            assertNull(domain.merchantName)
            assertNull(domain.category)
        }
    }

    @Nested
    inner class ToLinkedInstitutionEntity {

        @Test
        fun `maps all fields`() {
            val domain = LinkedInstitution(
                itemId = "item1",
                institutionId = "ins_3",
                institutionName = "Chase",
            )
            val entity = transformer.toLinkedInstitutionEntity(domain)
            assertEquals("item1", entity.itemId)
            assertEquals("ins_3", entity.institutionId)
            assertEquals("Chase", entity.institutionName)
        }

        @Test
        fun `round-trips through toLinkedInstitution`() {
            val original = LinkedInstitution(
                itemId = "item1",
                institutionId = "ins_3",
                institutionName = "Chase",
            )
            val entity = transformer.toLinkedInstitutionEntity(original)
            assertEquals(original, transformer.toLinkedInstitution(entity))
        }
    }

    @Nested
    inner class ToFinancialAccountEntity {

        @Test
        fun `stores type as enum name`() {
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
            assertEquals("CREDIT", transformer.toFinancialAccountEntity(domain).type)
        }

        @Test
        fun `round-trips through toFinancialAccount for every type`() {
            for (type in FinancialAccountType.entries) {
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
                val entity = transformer.toFinancialAccountEntity(original)
                assertEquals(original, transformer.toFinancialAccount(entity), "Round-trip failed for $type")
            }
        }
    }

    @Nested
    inner class ToTransactionEntity {

        @Test
        fun `round-trips through toTransaction`() {
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
            val entity = transformer.toTransactionEntity(original)
            assertEquals(original, transformer.toTransaction(entity))
        }
    }
}
