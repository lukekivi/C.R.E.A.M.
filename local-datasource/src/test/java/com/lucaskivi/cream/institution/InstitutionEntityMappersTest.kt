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

class InstitutionEntityMappersTest {

    @Nested
    inner class LinkedInstitutionMappers {

        @Test
        fun `toDomain maps all fields`() {
            val entity = LinkedInstitutionEntity(
                itemId = "item1",
                institutionId = "ins_3",
                institutionName = "Chase",
            )
            val domain = entity.toDomain()
            assertEquals("item1", domain.itemId)
            assertEquals("ins_3", domain.institutionId)
            assertEquals("Chase", domain.institutionName)
        }

        @Test
        fun `toEntity maps all fields`() {
            val domain = LinkedInstitution(
                itemId = "item1",
                institutionId = "ins_3",
                institutionName = "Chase",
            )
            val entity = domain.toEntity()
            assertEquals("item1", entity.itemId)
            assertEquals("ins_3", entity.institutionId)
            assertEquals("Chase", entity.institutionName)
        }

        @Test
        fun `round-trips through entity`() {
            val original = LinkedInstitution(
                itemId = "item1",
                institutionId = "ins_3",
                institutionName = "Chase",
            )
            val roundTripped = original
                .toEntity()
                .toDomain()
            assertEquals(original, roundTripped)
        }
    }

    @Nested
    inner class FinancialAccountMappers {

        @Test
        fun `toDomain maps all fields including type`() {
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
            val domain = entity.toDomain()
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
        fun `toEntity stores type as enum name`() {
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
            val entity = domain.toEntity()
            assertEquals("CREDIT", entity.type)
        }

        @Test
        fun `toDomain returns OTHER for unknown type string`() {
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
            assertEquals(FinancialAccountType.OTHER, entity.toDomain().type)
        }

        @Test
        fun `toDomain handles null optional fields`() {
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
            val domain = entity.toDomain()
            assertNull(domain.mask)
            assertNull(domain.subtype)
            assertNull(domain.balanceCurrent)
            assertNull(domain.balanceAvailable)
            assertNull(domain.isoCurrencyCode)
        }

        @Test
        fun `round-trips through entity for every type`() {
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
                val roundTripped = original
                    .toEntity()
                    .toDomain()
                assertEquals(original, roundTripped, "Round-trip failed for $type")
            }
        }
    }

    @Nested
    inner class TransactionMappers {

        @Test
        fun `toDomain maps all fields`() {
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
            val domain = entity.toDomain()
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
        fun `toDomain handles null optional fields`() {
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
            val domain = entity.toDomain()
            assertNull(domain.isoCurrencyCode)
            assertNull(domain.merchantName)
            assertNull(domain.category)
        }

        @Test
        fun `round-trips through entity`() {
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
            val roundTripped = original
                .toEntity()
                .toDomain()
            assertEquals(original, roundTripped)
        }
    }

    @Nested
    inner class InstitutionWithAccountsMappers {

        @Test
        fun `toDomain maps institution and accounts`() {
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
            val domain = relation.toDomain()
            assertEquals("item1", domain.institution.itemId)
            assertEquals("Chase", domain.institution.institutionName)
            assertEquals(1, domain.accounts.size)
            assertEquals("acc1", domain.accounts[0].accountId)
            assertEquals(FinancialAccountType.DEPOSITORY, domain.accounts[0].type)
        }

        @Test
        fun `toDomain handles empty accounts list`() {
            val relation = InstitutionWithAccountsEntity(
                institution = LinkedInstitutionEntity("item1", "ins_3", "Chase"),
                accounts = emptyList(),
            )
            assertEquals(0, relation.toDomain().accounts.size)
        }
    }
}
