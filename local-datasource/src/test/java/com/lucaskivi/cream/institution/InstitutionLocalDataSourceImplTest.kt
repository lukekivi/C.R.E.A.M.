package com.lucaskivi.cream.institution

import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.core.institution.model.Transaction
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class InstitutionLocalDataSourceImplTest {

    private val dao: InstitutionDao = mockk()
    private val transformer: InstitutionEntityTransformer = mockk()
    private val dataSource = InstitutionLocalDataSourceImpl(
        dao = dao,
        transformer = transformer,
    )

    @Nested
    inner class ObserveInstitutionsWithAccounts {

        @Test
        fun `emits the transformed institutions`() = runTest {
            // Setup
            val entity: InstitutionWithAccountsEntity = mockk()
            val domain: LinkedInstitutionWithAccounts = mockk()
            every { dao.observeInstitutionsWithAccounts() } returns flowOf(listOf(entity))
            every { transformer.toLinkedInstitutionWithAccounts(entity) } returns domain
            val expected = listOf(domain)

            // Test
            val result = dataSource
                .observeInstitutionsWithAccounts()
                .first()

            // Verify
            assertEquals(expected, result)
            verify {
                dao.observeInstitutionsWithAccounts()
                transformer.toLinkedInstitutionWithAccounts(entity)
            }
            confirmVerified(dao, transformer)
        }
    }

    @Nested
    inner class ObserveAccount {

        @Test
        fun `emits the transformed account`() = runTest {
            // Setup
            val entity: FinancialAccountEntity = mockk()
            val domain: FinancialAccount = mockk()
            every { dao.observeAccount("acc1") } returns flowOf(entity)
            every { transformer.toFinancialAccount(entity) } returns domain

            // Test
            val result = dataSource
                .observeAccount("acc1")
                .first()

            // Verify
            assertEquals(domain, result)
            verify {
                dao.observeAccount("acc1")
                transformer.toFinancialAccount(entity)
            }
            confirmVerified(dao, transformer)
        }

        @Test
        fun `emits null when the account doesn't exist`() = runTest {
            // Setup
            every { dao.observeAccount("acc1") } returns flowOf(null)

            // Test
            val result = dataSource
                .observeAccount("acc1")
                .first()

            // Verify
            assertNull(result)
            verify { dao.observeAccount("acc1") }
            confirmVerified(dao, transformer)
        }
    }

    @Nested
    inner class ObserveTransactions {

        @Test
        fun `emits the transformed transactions`() = runTest {
            // Setup
            val entity: TransactionEntity = mockk()
            val domain: Transaction = mockk()
            every { dao.observeTransactions("acc1") } returns flowOf(listOf(entity))
            every { transformer.toTransaction(entity) } returns domain
            val expected = listOf(domain)

            // Test
            val result = dataSource
                .observeTransactions("acc1")
                .first()

            // Verify
            assertEquals(expected, result)
            verify {
                dao.observeTransactions("acc1")
                transformer.toTransaction(entity)
            }
            confirmVerified(dao, transformer)
        }
    }

    @Nested
    inner class UpsertInstitutions {

        @Test
        fun `inserts the transformed institutions if absent then updates each one's info`() = runTest {
            // Setup
            val domain = LinkedInstitution(
                itemId = "item1",
                institutionId = "ins_3",
                institutionName = "Chase",
            )
            val entity: LinkedInstitutionEntity = mockk()
            every { transformer.toLinkedInstitutionEntity(domain) } returns entity
            coJustRun { dao.insertInstitutionsIfAbsent(any()) }
            coJustRun { dao.updateInstitutionInfo(any(), any(), any()) }

            // Test
            dataSource.upsertInstitutions(listOf(domain))

            // Verify
            coVerify {
                transformer.toLinkedInstitutionEntity(domain)
                dao.insertInstitutionsIfAbsent(listOf(entity))
                dao.updateInstitutionInfo(
                    itemId = "item1",
                    institutionId = "ins_3",
                    institutionName = "Chase",
                )
            }
            confirmVerified(dao, transformer)
        }
    }

    @Nested
    inner class UpsertAccounts {

        @Test
        fun `upserts the transformed accounts`() = runTest {
            // Setup
            val domain: FinancialAccount = mockk()
            val entity: FinancialAccountEntity = mockk()
            every { transformer.toFinancialAccountEntity(domain) } returns entity
            coJustRun { dao.upsertAccounts(any()) }

            // Test
            dataSource.upsertAccounts(listOf(domain))

            // Verify
            coVerify {
                transformer.toFinancialAccountEntity(domain)
                dao.upsertAccounts(listOf(entity))
            }
            confirmVerified(dao, transformer)
        }
    }

    @Nested
    inner class UpsertTransactions {

        @Test
        fun `upserts the transformed transactions`() = runTest {
            // Setup
            val domain: Transaction = mockk()
            val entity: TransactionEntity = mockk()
            every { transformer.toTransactionEntity(domain) } returns entity
            coJustRun { dao.upsertTransactions(any()) }

            // Test
            dataSource.upsertTransactions(listOf(domain))

            // Verify
            coVerify {
                transformer.toTransactionEntity(domain)
                dao.upsertTransactions(listOf(entity))
            }
            confirmVerified(dao, transformer)
        }
    }

    @Nested
    inner class DeleteInstitution {

        @Test
        fun `deletes the institution`() = runTest {
            // Setup
            coJustRun { dao.deleteInstitution(any()) }

            // Test
            dataSource.deleteInstitution("item1")

            // Verify
            coVerify { dao.deleteInstitution("item1") }
            confirmVerified(dao, transformer)
        }
    }

    @Nested
    inner class DeleteTransactions {

        @Test
        fun `deletes the transactions`() = runTest {
            // Setup
            val transactionIds = listOf("tx1", "tx2")
            coJustRun { dao.deleteTransactions(any()) }

            // Test
            dataSource.deleteTransactions(transactionIds)

            // Verify
            coVerify { dao.deleteTransactions(transactionIds) }
            confirmVerified(dao, transformer)
        }
    }

    @Nested
    inner class GetTransactionCursor {

        @Test
        fun `returns the stored cursor`() = runTest {
            // Setup
            coEvery { dao.getTransactionCursor("item1") } returns "cursor-abc"

            // Test
            val result = dataSource.getTransactionCursor("item1")

            // Verify
            assertEquals("cursor-abc", result)
            coVerify { dao.getTransactionCursor("item1") }
            confirmVerified(dao, transformer)
        }
    }

    @Nested
    inner class SaveTransactionCursor {

        @Test
        fun `saves the cursor`() = runTest {
            // Setup
            coJustRun { dao.saveTransactionCursor(any(), any()) }

            // Test
            dataSource.saveTransactionCursor("item1", "cursor-abc")

            // Verify
            coVerify { dao.saveTransactionCursor("item1", "cursor-abc") }
            confirmVerified(dao, transformer)
        }
    }
}
