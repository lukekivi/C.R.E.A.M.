package com.lucaskivi.cream.data.repository.institution

import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.FinancialAccountType
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.core.institution.model.Transaction
import com.lucaskivi.cream.institution.InstitutionLocalDataSource
import com.lucaskivi.cream.institution.InstitutionRemoteDataSource
import com.lucaskivi.cream.institution.model.TransactionDelta
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class InstitutionRepositoryImplTest {

    private val remote: InstitutionRemoteDataSource = mockk()
    private val local: InstitutionLocalDataSource = mockk(relaxed = true)
    private val repo = InstitutionRepositoryImpl(
        remoteDataSource = remote,
        localDataSource = local,
    )

    @Nested
    inner class ObserveInstitutionsWithAccounts {

        @Test
        fun `delegates to local`() = runTest {
            // Setup
            val institution = LinkedInstitutionWithAccounts(
                institution = LinkedInstitution("item1", "ins_3", "Chase"),
                accounts = emptyList(),
            )
            val institutions = listOf(institution)
            every { local.observeInstitutionsWithAccounts() } returns flowOf(institutions)

            // Test
            val observed = repo
                .observeInstitutionsWithAccounts()
                .first()

            // Verify
            assertEquals(institutions, observed)
            verify { local.observeInstitutionsWithAccounts() }
            confirmVerified(remote, local)
        }
    }

    @Nested
    inner class ObserveAccount {

        @Test
        fun `delegates to local`() = runTest {
            // Setup
            val account = financialAccount()
            every { local.observeAccount("acc1") } returns flowOf(account)

            // Test
            val observed = repo
                .observeAccount("acc1")
                .first()

            // Verify
            assertEquals(account, observed)
            verify { local.observeAccount("acc1") }
            confirmVerified(remote, local)
        }
    }

    @Nested
    inner class ObserveTransactions {

        @Test
        fun `delegates to local`() = runTest {
            // Setup
            val transactions = listOf(transaction())
            every { local.observeTransactions("acc1") } returns flowOf(transactions)

            // Test
            val observed = repo
                .observeTransactions("acc1")
                .first()

            // Verify
            assertEquals(transactions, observed)
            verify { local.observeTransactions("acc1") }
            confirmVerified(remote, local)
        }
    }

    @Nested
    inner class LinkInstitution {

        @Test
        fun `exchanges token then refreshes institutions`() = runTest {
            // Setup
            val institution = LinkedInstitution("item1", "ins_3", "Chase")
            coEvery {
                remote.exchangePublicToken("tok", "ins_3", "Chase")
            } returns NetworkResult.Success(institution)
            coEvery { remote.listInstitutions() } returns NetworkResult.Success(emptyList())

            // Test
            val result = repo.linkInstitution("tok", "ins_3", "Chase")

            // Verify
            assertInstanceOf(NetworkResult.Success::class.java, result)
            coVerify { remote.exchangePublicToken("tok", "ins_3", "Chase") }
            coVerify { remote.listInstitutions() }
            coVerify { local.upsertInstitutions(emptyList()) }
            coVerify { local.upsertAccounts(emptyList()) }
            confirmVerified(remote, local)
        }

        @Test
        fun `returns failure when exchangePublicToken fails`() = runTest {
            // Setup
            val error = RuntimeException("network error")
            coEvery {
                remote.exchangePublicToken(any(), any(), any())
            } returns NetworkResult.Failure(error)

            // Test
            val result = repo.linkInstitution("tok", "ins_3", "Chase")

            // Verify
            assertInstanceOf(NetworkResult.Failure::class.java, result)
            assertEquals(error, (result as NetworkResult.Failure).cause)
            coVerify(exactly = 0) { remote.listInstitutions() }
            coVerify { remote.exchangePublicToken("tok", "ins_3", "Chase") }
            confirmVerified(remote, local)
        }

        @Test
        fun `returns failure when listInstitutions fails`() = runTest {
            // Setup
            coEvery {
                remote.exchangePublicToken(any(), any(), any())
            } returns NetworkResult.Success(LinkedInstitution("item1", "ins_3", "Chase"))
            val error = RuntimeException("server error")
            coEvery { remote.listInstitutions() } returns NetworkResult.Failure(error)

            // Test
            val result = repo.linkInstitution("tok", "ins_3", "Chase")

            // Verify
            assertInstanceOf(NetworkResult.Failure::class.java, result)
            coVerify { remote.exchangePublicToken("tok", "ins_3", "Chase") }
            coVerify { remote.listInstitutions() }
            confirmVerified(remote, local)
        }
    }

    @Nested
    inner class SyncTransactions {

        @Test
        fun `reads cursor, applies delta, saves new cursor`() = runTest {
            // Setup
            coEvery { local.getTransactionCursor("item1") } returns "cursor-old"
            val delta = TransactionDelta(
                added = listOf(transaction(id = "tx1")),
                modified = listOf(transaction(id = "tx2")),
                removedIds = listOf("tx3"),
                nextCursor = "cursor-new",
            )
            coEvery { remote.syncTransactions("item1", "cursor-old") } returns NetworkResult.Success(delta)

            // Test
            val result = repo.syncTransactions("item1")

            // Verify
            assertInstanceOf(NetworkResult.Success::class.java, result)
            coVerify { local.upsertTransactions(delta.added + delta.modified) }
            coVerify { local.deleteTransactions(delta.removedIds) }
            coVerify { local.saveTransactionCursor("item1", "cursor-new") }
            coVerify { local.getTransactionCursor("item1") }
            coVerify { remote.syncTransactions("item1", "cursor-old") }
            confirmVerified(remote, local)
        }

        @Test
        fun `passes null cursor on first sync`() = runTest {
            // Setup
            coEvery { local.getTransactionCursor("item1") } returns null
            val delta = TransactionDelta(
                added = emptyList(),
                modified = emptyList(),
                removedIds = emptyList(),
                nextCursor = "cursor-first",
            )
            coEvery { remote.syncTransactions("item1", null) } returns NetworkResult.Success(delta)

            // Test
            repo.syncTransactions("item1")

            // Verify
            coVerify { remote.syncTransactions("item1", null) }
            coVerify { local.getTransactionCursor("item1") }
            coVerify { local.upsertTransactions(delta.added + delta.modified) }
            coVerify { local.deleteTransactions(delta.removedIds) }
            coVerify { local.saveTransactionCursor("item1", "cursor-first") }
            confirmVerified(remote, local)
        }

        @Test
        fun `returns failure and skips local writes when remote fails`() = runTest {
            // Setup
            val error = RuntimeException("remote error")
            coEvery { local.getTransactionCursor("item1") } returns null
            coEvery { remote.syncTransactions("item1", null) } returns NetworkResult.Failure(error)

            // Test
            val result = repo.syncTransactions("item1")

            // Verify
            assertInstanceOf(NetworkResult.Failure::class.java, result)
            coVerify(exactly = 0) { local.upsertTransactions(any()) }
            coVerify(exactly = 0) { local.deleteTransactions(any()) }
            coVerify(exactly = 0) { local.saveTransactionCursor(any(), any()) }
            coVerify { local.getTransactionCursor("item1") }
            coVerify { remote.syncTransactions("item1", null) }
            confirmVerified(remote, local)
        }
    }

    @Nested
    inner class DisconnectInstitution {

        @Test
        fun `calls remote then deletes locally`() = runTest {
            // Setup
            coEvery { remote.disconnectInstitution("item1") } returns NetworkResult.Success(Unit)

            // Test
            val result = repo.disconnectInstitution("item1")

            // Verify
            assertInstanceOf(NetworkResult.Success::class.java, result)
            coVerify { remote.disconnectInstitution("item1") }
            coVerify { local.deleteInstitution("item1") }
            confirmVerified(remote, local)
        }

        @Test
        fun `returns failure and skips local delete when remote fails`() = runTest {
            // Setup
            val error = RuntimeException("remote error")
            coEvery { remote.disconnectInstitution("item1") } returns NetworkResult.Failure(error)

            // Test
            val result = repo.disconnectInstitution("item1")

            // Verify
            assertInstanceOf(NetworkResult.Failure::class.java, result)
            coVerify(exactly = 0) { local.deleteInstitution(any()) }
            coVerify { remote.disconnectInstitution("item1") }
            confirmVerified(remote, local)
        }
    }

    @Nested
    inner class RefreshInstitutions {

        @Test
        fun `upserts institutions and accounts from remote`() = runTest {
            // Setup
            val institution = LinkedInstitution("item1", "ins_3", "Chase")
            val accounts = listOf(financialAccount())
            val institutions = listOf(LinkedInstitutionWithAccounts(institution, accounts))
            coEvery { remote.listInstitutions() } returns NetworkResult.Success(institutions)

            // Test
            val result = repo.refreshInstitutions()

            // Verify
            assertInstanceOf(NetworkResult.Success::class.java, result)
            coVerify { local.upsertInstitutions(listOf(institution)) }
            coVerify { local.upsertAccounts(accounts) }
            coVerify { remote.listInstitutions() }
            confirmVerified(remote, local)
        }

        @Test
        fun `returns failure when remote fails`() = runTest {
            // Setup
            val error = RuntimeException("remote error")
            coEvery { remote.listInstitutions() } returns NetworkResult.Failure(error)

            // Test
            val result = repo.refreshInstitutions()

            // Verify
            assertInstanceOf(NetworkResult.Failure::class.java, result)
            coVerify(exactly = 0) { local.upsertInstitutions(any()) }
            coVerify { remote.listInstitutions() }
            confirmVerified(remote, local)
        }
    }

    private fun financialAccount(id: String = "acc1"): FinancialAccount =
        FinancialAccount(
            accountId = id,
            itemId = "item1",
            displayName = "Checking",
            mask = null,
            type = FinancialAccountType.DEPOSITORY,
            subtype = null,
            balanceCurrent = null,
            balanceAvailable = null,
            isoCurrencyCode = null,
        )

    private fun transaction(id: String = "tx1"): Transaction =
        Transaction(
            transactionId = id,
            accountId = "acc1",
            amount = 10.0,
            isoCurrencyCode = null,
            date = LocalDate.of(2024, 1, 1),
            name = "Store",
            merchantName = null,
            pending = false,
            category = null,
        )
}
