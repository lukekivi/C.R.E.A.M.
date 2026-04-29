package com.lucaskivi.cream.institution

import com.lucaskivi.cream.core.institution.model.AccountType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class InstitutionTransformerTest {

    private val transformer = InstitutionTransformer()

    @Nested
    inner class ToLinkToken {

        @Test
        fun `returns the link token`() {
            assertEquals("lt-abc", transformer.toLinkToken(mapOf("linkToken" to "lt-abc")))
        }
    }

    @Nested
    inner class ToLinkedInstitution {

        @Test
        fun `combines response fields with the selected institution id`() {
            val institution = transformer.toLinkedInstitution(
                response = mapOf("itemId" to "item-1", "institutionName" to "Chase"),
                institutionId = "ins_3",
            )
            assertEquals("item-1", institution.itemId)
            assertEquals("ins_3", institution.institutionId)
            assertEquals("Chase", institution.institutionName)
        }
    }

    @Nested
    inner class ToAccountType {

        @Test
        fun `maps known type strings`() {
            assertEquals(AccountType.DEPOSITORY, transformer.toAccountType("depository"))
            assertEquals(AccountType.CREDIT, transformer.toAccountType("credit"))
            assertEquals(AccountType.LOAN, transformer.toAccountType("loan"))
            assertEquals(AccountType.INVESTMENT, transformer.toAccountType("investment"))
        }

        @Test
        fun `is case-insensitive`() {
            assertEquals(AccountType.DEPOSITORY, transformer.toAccountType("DEPOSITORY"))
            assertEquals(AccountType.CREDIT, transformer.toAccountType("Credit"))
        }

        @Test
        fun `returns OTHER for unknown string`() {
            assertEquals(AccountType.OTHER, transformer.toAccountType("brokerage"))
        }

        @Test
        fun `returns OTHER for null`() {
            assertEquals(AccountType.OTHER, transformer.toAccountType(null))
        }
    }

    @Nested
    inner class ToTransaction {

        @Test
        fun `maps all fields correctly`() {
            val map = mapOf(
                "transactionId" to "tx1",
                "accountId" to "acc1",
                "amount" to 12.50,
                "isoCurrencyCode" to "USD",
                "date" to "2024-03-15",
                "name" to "Coffee Shop",
                "merchantName" to "Blue Bottle",
                "pending" to false,
                "category" to "Food and Drink",
            )
            val tx = transformer.toTransaction(map)
            assertEquals("tx1", tx.transactionId)
            assertEquals("acc1", tx.accountId)
            assertEquals(12.50, tx.amount, 0.0)
            assertEquals("USD", tx.isoCurrencyCode)
            assertEquals(LocalDate.of(2024, 3, 15), tx.date)
            assertEquals("Coffee Shop", tx.name)
            assertEquals("Blue Bottle", tx.merchantName)
            assertEquals(false, tx.pending)
            assertEquals("Food and Drink", tx.category)
        }

        @Test
        fun `handles nullable fields absent`() {
            val map = mapOf(
                "transactionId" to "tx2",
                "accountId" to "acc2",
                "amount" to 5L,
                "isoCurrencyCode" to null,
                "date" to "2024-01-01",
                "name" to "Unknown",
                "merchantName" to null,
                "pending" to true,
                "category" to null,
            )
            val tx = transformer.toTransaction(map)
            assertNull(tx.isoCurrencyCode)
            assertNull(tx.merchantName)
            assertNull(tx.category)
            assertEquals(5.0, tx.amount, 0.0)
        }
    }

    @Nested
    inner class ToAccount {

        @Test
        fun `maps all fields correctly`() {
            val balance = mapOf(
                "current" to 1000.0,
                "available" to 950.0,
                "isoCurrencyCode" to "USD",
            )
            val map = mapOf(
                "accountId" to "acc1",
                "name" to "Total Checking",
                "mask" to "1234",
                "type" to "depository",
                "subtype" to "checking",
                "balance" to balance,
            )
            val account = transformer.toAccount(map, "item1")
            assertEquals("acc1", account.accountId)
            assertEquals("item1", account.itemId)
            assertEquals("Total Checking", account.displayName)
            assertEquals("1234", account.mask)
            assertEquals(AccountType.DEPOSITORY, account.type)
            assertEquals("checking", account.subtype)
            assertEquals(1000.0, account.balanceCurrent!!, 0.0)
            assertEquals(950.0, account.balanceAvailable!!, 0.0)
            assertEquals("USD", account.isoCurrencyCode)
        }

        @Test
        fun `handles null optional fields`() {
            val balance = mapOf<String, Any?>(
                "current" to null,
                "available" to null,
                "isoCurrencyCode" to null,
            )
            val map = mapOf(
                "accountId" to "acc2",
                "name" to "Savings",
                "mask" to null,
                "type" to null,
                "subtype" to null,
                "balance" to balance,
            )
            val account = transformer.toAccount(map, "item2")
            assertNull(account.mask)
            assertEquals(AccountType.OTHER, account.type)
            assertNull(account.subtype)
            assertNull(account.balanceCurrent)
            assertNull(account.balanceAvailable)
            assertNull(account.isoCurrencyCode)
        }
    }

    @Nested
    inner class ToTransactionDelta {

        @Test
        fun `maps added, modified, removed, and cursor`() {
            fun txMap(id: String) = mapOf(
                "transactionId" to id,
                "accountId" to "acc1",
                "amount" to 1.0,
                "isoCurrencyCode" to null,
                "date" to "2024-06-01",
                "name" to "Store",
                "merchantName" to null,
                "pending" to false,
                "category" to null,
            )
            val map = mapOf(
                "added" to listOf(txMap("a1")),
                "modified" to listOf(txMap("m1")),
                "removed" to listOf(mapOf("transactionId" to "r1")),
                "cursor" to "cursor-xyz",
            )
            val delta = transformer.toTransactionDelta(map)
            assertEquals(1, delta.added.size)
            assertEquals("a1", delta.added[0].transactionId)
            assertEquals(1, delta.modified.size)
            assertEquals("m1", delta.modified[0].transactionId)
            assertEquals(listOf("r1"), delta.removedIds)
            assertEquals("cursor-xyz", delta.nextCursor)
        }

        @Test
        fun `handles empty buckets`() {
            val map = mapOf(
                "added" to emptyList<Map<String, Any?>>(),
                "modified" to emptyList<Map<String, Any?>>(),
                "removed" to emptyList<Map<String, Any?>>(),
                "cursor" to "cursor-abc",
            )
            val delta = transformer.toTransactionDelta(map)
            assertEquals(0, delta.added.size)
            assertEquals(0, delta.modified.size)
            assertEquals(0, delta.removedIds.size)
            assertEquals("cursor-abc", delta.nextCursor)
        }

        @Test
        fun `throws when a removed entry lacks transactionId`() {
            val map = mapOf(
                "added" to emptyList<Map<String, Any?>>(),
                "modified" to emptyList<Map<String, Any?>>(),
                "removed" to listOf(mapOf("id" to "r1")),
                "cursor" to "cursor-abc",
            )
            assertThrows<NullPointerException> { transformer.toTransactionDelta(map) }
        }
    }

    @Nested
    inner class ToLinkedInstitutionWithAccounts {

        @Test
        fun `maps institution and accounts`() {
            val balance = mapOf<String, Any?>(
                "current" to 500.0,
                "available" to null,
                "isoCurrencyCode" to "USD",
            )
            val account = mapOf(
                "accountId" to "acc1",
                "name" to "Checking",
                "mask" to null,
                "type" to "depository",
                "subtype" to null,
                "balance" to balance,
            )
            val map = mapOf(
                "itemId" to "item1",
                "institutionId" to "ins_3",
                "institutionName" to "Chase",
                "accounts" to listOf(account),
            )
            val result = transformer.toLinkedInstitutionWithAccounts(map)
            assertEquals("item1", result.institution.itemId)
            assertEquals("ins_3", result.institution.institutionId)
            assertEquals("Chase", result.institution.institutionName)
            assertEquals(1, result.accounts.size)
            assertEquals("acc1", result.accounts[0].accountId)
            assertEquals("item1", result.accounts[0].itemId)
        }
    }

    @Nested
    inner class ToLinkedInstitutionsWithAccounts {

        @Test
        fun `maps every institution in the response`() {
            fun institution(itemId: String) = mapOf(
                "itemId" to itemId,
                "institutionId" to "ins_3",
                "institutionName" to "Chase",
                "accounts" to emptyList<Map<String, Any?>>(),
            )
            val response = mapOf("institutions" to listOf(institution("item1"), institution("item2")))
            val result = transformer.toLinkedInstitutionsWithAccounts(response)
            assertEquals(listOf("item1", "item2"), result.map { it.institution.itemId })
        }

        @Test
        fun `returns empty list when nothing is linked`() {
            val response = mapOf("institutions" to emptyList<Map<String, Any?>>())
            assertEquals(emptyList<Any>(), transformer.toLinkedInstitutionsWithAccounts(response))
        }
    }
}
