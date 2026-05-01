package com.lucaskivi.cream.institution

import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.core.institution.model.Transaction
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
            val entity: InstitutionWithAccountsEntity = mockk()
            val domain: LinkedInstitutionWithAccounts = mockk()
            every { dao.observeInstitutionsWithAccounts() } returns flowOf(listOf(entity))
            every { transformer.toLinkedInstitutionWithAccounts(entity) } returns domain
            assertEquals(listOf(domain), dataSource.observeInstitutionsWithAccounts().first())
        }
    }

    @Nested
    inner class ObserveAccount {

        @Test
        fun `emits the transformed account`() = runTest {
            val entity: FinancialAccountEntity = mockk()
            val domain: FinancialAccount = mockk()
            every { dao.observeAccount("acc1") } returns flowOf(entity)
            every { transformer.toFinancialAccount(entity) } returns domain
            assertEquals(domain, dataSource.observeAccount("acc1").first())
        }

        @Test
        fun `emits null when the account doesn't exist`() = runTest {
            every { dao.observeAccount("acc1") } returns flowOf(null)
            assertNull(dataSource.observeAccount("acc1").first())
        }
    }

    @Nested
    inner class ObserveTransactions {

        @Test
        fun `emits the transformed transactions`() = runTest {
            val entity: TransactionEntity = mockk()
            val domain: Transaction = mockk()
            every { dao.observeTransactions("acc1") } returns flowOf(listOf(entity))
            every { transformer.toTransaction(entity) } returns domain
            assertEquals(listOf(domain), dataSource.observeTransactions("acc1").first())
        }
    }

    @Nested
    inner class UpsertInstitutions {

        @Test
        fun `inserts the transformed institutions if absent`() = runTest {
            val domain = LinkedInstitution(
                itemId = "item1",
                institutionId = "ins_3",
                institutionName = "Chase",
            )
            val entity: LinkedInstitutionEntity = mockk()
            every { transformer.toLinkedInstitutionEntity(domain) } returns entity
            coJustRun { dao.insertInstitutionsIfAbsent(any()) }
            coJustRun { dao.updateInstitutionInfo(any(), any(), any()) }
            dataSource.upsertInstitutions(listOf(domain))
            coVerify { dao.insertInstitutionsIfAbsent(listOf(entity)) }
        }

        @Test
        fun `updates each institution's info`() = runTest {
            val domain = LinkedInstitution(
                itemId = "item1",
                institutionId = "ins_3",
                institutionName = "Chase",
            )
            every { transformer.toLinkedInstitutionEntity(domain) } returns mockk()
            coJustRun { dao.insertInstitutionsIfAbsent(any()) }
            coJustRun { dao.updateInstitutionInfo(any(), any(), any()) }
            dataSource.upsertInstitutions(listOf(domain))
            coVerify {
                dao.updateInstitutionInfo(
                    itemId = "item1",
                    institutionId = "ins_3",
                    institutionName = "Chase",
                )
            }
        }
    }

    @Nested
    inner class UpsertAccounts {

        @Test
        fun `upserts the transformed accounts`() = runTest {
            val domain: FinancialAccount = mockk()
            val entity: FinancialAccountEntity = mockk()
            every { transformer.toFinancialAccountEntity(domain) } returns entity
            coJustRun { dao.upsertAccounts(any()) }
            dataSource.upsertAccounts(listOf(domain))
            coVerify { dao.upsertAccounts(listOf(entity)) }
        }
    }

    @Nested
    inner class UpsertTransactions {

        @Test
        fun `upserts the transformed transactions`() = runTest {
            val domain: Transaction = mockk()
            val entity: TransactionEntity = mockk()
            every { transformer.toTransactionEntity(domain) } returns entity
            coJustRun { dao.upsertTransactions(any()) }
            dataSource.upsertTransactions(listOf(domain))
            coVerify { dao.upsertTransactions(listOf(entity)) }
        }
    }

    @Nested
    inner class DeleteInstitution {

        @Test
        fun `deletes the institution`() = runTest {
            coJustRun { dao.deleteInstitution(any()) }
            dataSource.deleteInstitution("item1")
            coVerify { dao.deleteInstitution("item1") }
        }
    }

    @Nested
    inner class DeleteTransactions {

        @Test
        fun `deletes the transactions`() = runTest {
            val transactionIds = listOf("tx1", "tx2")
            coJustRun { dao.deleteTransactions(any()) }
            dataSource.deleteTransactions(transactionIds)
            coVerify { dao.deleteTransactions(transactionIds) }
        }
    }

    @Nested
    inner class GetTransactionCursor {

        @Test
        fun `returns the stored cursor`() = runTest {
            coEvery { dao.getTransactionCursor("item1") } returns "cursor-abc"
            assertEquals("cursor-abc", dataSource.getTransactionCursor("item1"))
        }
    }

    @Nested
    inner class SaveTransactionCursor {

        @Test
        fun `saves the cursor`() = runTest {
            coJustRun { dao.saveTransactionCursor(any(), any()) }
            dataSource.saveTransactionCursor("item1", "cursor-abc")
            coVerify { dao.saveTransactionCursor("item1", "cursor-abc") }
        }
    }
}
