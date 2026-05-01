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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Unit tests for [InstitutionRepositoryImpl].
 */
class InstitutionRepositoryImplTest {

    private val remote: InstitutionRemoteDataSource = mockk()
    private val local: InstitutionLocalDataSource = mockk(relaxed = true)
    private val repo = InstitutionRepositoryImpl(remote, local)

    // region observe

    @Test
    fun `observeInstitutionsWithAccounts delegates to local`() = runTest {
        val institutions = listOf(
            LinkedInstitutionWithAccounts(
                institution = LinkedInstitution("item1", "ins_3", "Chase"),
                accounts = emptyList(),
            )
        )
        every { local.observeInstitutionsWithAccounts() } returns flowOf(institutions)
        assertEquals(institutions, repo.observeInstitutionsWithAccounts().first())
    }

    @Test
    fun `observeAccount delegates to local`() = runTest {
        val account = financialAccount()
        every { local.observeAccount("acc1") } returns flowOf(account)
        assertEquals(account, repo.observeAccount("acc1").first())
    }

    @Test
    fun `observeTransactions delegates to local`() = runTest {
        val transactions = listOf(transaction())
        every { local.observeTransactions("acc1") } returns flowOf(transactions)
        assertEquals(transactions, repo.observeTransactions("acc1").first())
    }

    // endregion

    // region linkInstitution

    @Test
    fun `linkInstitution exchanges token then refreshes institutions`() = runTest {
        val institution = LinkedInstitution("item1", "ins_3", "Chase")
        coEvery {
            remote.exchangePublicToken("tok", "ins_3", "Chase")
        } returns NetworkResult.Success(institution)
        coEvery { remote.listInstitutions() } returns NetworkResult.Success(emptyList())

        val result = repo.linkInstitution("tok", "ins_3", "Chase")

        assertInstanceOf(NetworkResult.Success::class.java, result)
        coVerify { remote.exchangePublicToken("tok", "ins_3", "Chase") }
        coVerify { remote.listInstitutions() }
    }

    @Test
    fun `linkInstitution returns failure when exchangePublicToken fails`() = runTest {
        val error = RuntimeException("network error")
        coEvery {
            remote.exchangePublicToken(any(), any(), any())
        } returns NetworkResult.Failure(error)

        val result = repo.linkInstitution("tok", "ins_3", "Chase")

        assertInstanceOf(NetworkResult.Failure::class.java, result)
        assertEquals(error, (result as NetworkResult.Failure).cause)
        coVerify(exactly = 0) { remote.listInstitutions() }
    }

    @Test
    fun `linkInstitution returns failure when listInstitutions fails`() = runTest {
        coEvery {
            remote.exchangePublicToken(any(), any(), any())
        } returns NetworkResult.Success(LinkedInstitution("item1", "ins_3", "Chase"))
        val error = RuntimeException("server error")
        coEvery { remote.listInstitutions() } returns NetworkResult.Failure(error)

        val result = repo.linkInstitution("tok", "ins_3", "Chase")

        assertInstanceOf(NetworkResult.Failure::class.java, result)
    }

    // endregion

    // region syncTransactions

    @Test
    fun `syncTransactions reads cursor, applies delta, saves new cursor`() = runTest {
        coEvery { local.getTransactionCursor("item1") } returns "cursor-old"
        val delta = TransactionDelta(
            added = listOf(transaction(id = "tx1")),
            modified = listOf(transaction(id = "tx2")),
            removedIds = listOf("tx3"),
            nextCursor = "cursor-new",
        )
        coEvery { remote.syncTransactions("item1", "cursor-old") } returns NetworkResult.Success(delta)

        val result = repo.syncTransactions("item1")

        assertInstanceOf(NetworkResult.Success::class.java, result)
        coVerify { local.upsertTransactions(delta.added + delta.modified) }
        coVerify { local.deleteTransactions(listOf("tx3")) }
        coVerify { local.saveTransactionCursor("item1", "cursor-new") }
    }

    @Test
    fun `syncTransactions passes null cursor on first sync`() = runTest {
        coEvery { local.getTransactionCursor("item1") } returns null
        val delta = TransactionDelta(
            added = emptyList(),
            modified = emptyList(),
            removedIds = emptyList(),
            nextCursor = "cursor-first",
        )
        coEvery { remote.syncTransactions("item1", null) } returns NetworkResult.Success(delta)

        repo.syncTransactions("item1")

        coVerify { remote.syncTransactions("item1", null) }
    }

    @Test
    fun `syncTransactions returns failure and skips local writes when remote fails`() = runTest {
        val error = RuntimeException("remote error")
        coEvery { local.getTransactionCursor("item1") } returns null
        coEvery { remote.syncTransactions("item1", null) } returns NetworkResult.Failure(error)

        val result = repo.syncTransactions("item1")

        assertInstanceOf(NetworkResult.Failure::class.java, result)
        coVerify(exactly = 0) { local.upsertTransactions(any()) }
        coVerify(exactly = 0) { local.deleteTransactions(any()) }
        coVerify(exactly = 0) { local.saveTransactionCursor(any(), any()) }
    }

    // endregion

    // region disconnectInstitution

    @Test
    fun `disconnectInstitution calls remote then deletes locally`() = runTest {
        coEvery { remote.disconnectInstitution("item1") } returns NetworkResult.Success(Unit)

        val result = repo.disconnectInstitution("item1")

        assertInstanceOf(NetworkResult.Success::class.java, result)
        coVerify { remote.disconnectInstitution("item1") }
        coVerify { local.deleteInstitution("item1") }
    }

    @Test
    fun `disconnectInstitution returns failure and skips local delete when remote fails`() = runTest {
        val error = RuntimeException("remote error")
        coEvery { remote.disconnectInstitution("item1") } returns NetworkResult.Failure(error)

        val result = repo.disconnectInstitution("item1")

        assertInstanceOf(NetworkResult.Failure::class.java, result)
        coVerify(exactly = 0) { local.deleteInstitution(any()) }
    }

    // endregion

    // region refreshInstitutions

    @Test
    fun `refreshInstitutions upserts institutions and accounts from remote`() = runTest {
        val institution = LinkedInstitution("item1", "ins_3", "Chase")
        val account = financialAccount()
        val institutions = listOf(LinkedInstitutionWithAccounts(institution, listOf(account)))
        coEvery { remote.listInstitutions() } returns NetworkResult.Success(institutions)

        val result = repo.refreshInstitutions()

        assertInstanceOf(NetworkResult.Success::class.java, result)
        coVerify { local.upsertInstitutions(listOf(institution)) }
        coVerify { local.upsertAccounts(listOf(account)) }
    }

    @Test
    fun `refreshInstitutions returns failure when remote fails`() = runTest {
        val error = RuntimeException("remote error")
        coEvery { remote.listInstitutions() } returns NetworkResult.Failure(error)

        val result = repo.refreshInstitutions()

        assertInstanceOf(NetworkResult.Failure::class.java, result)
        coVerify(exactly = 0) { local.upsertInstitutions(any()) }
    }

    // endregion

    // region helpers

    private fun financialAccount(id: String = "acc1") = FinancialAccount(
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

    private fun transaction(id: String = "tx1") = Transaction(
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

    // endregion
}
