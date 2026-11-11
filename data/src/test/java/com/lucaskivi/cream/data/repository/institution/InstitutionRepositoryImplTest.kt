package com.lucaskivi.cream.data.repository.institution

import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.FinancialAccountType
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.core.institution.model.Transaction
import com.lucaskivi.cream.core.institution.model.TransactionSyncState
import com.lucaskivi.cream.institution.InstitutionLocalDataSource
import com.lucaskivi.cream.institution.InstitutionRemoteDataSource
import com.lucaskivi.cream.institution.model.TransactionDelta
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class InstitutionRepositoryImplTest {

    private val remote: InstitutionRemoteDataSource = mockk()
    private val local: InstitutionLocalDataSource = mockk(relaxed = true)
    private val testScope = TestScope()
    private val repo = InstitutionRepositoryImpl(
        remoteDataSource = remote,
        localDataSource = local,
        externalScope = testScope.backgroundScope,
    )

    @AfterEach
    fun tearDown() {
        confirmVerified(remote, local)
    }

    @Nested
    inner class ObserveInstitutionsWithAccounts {

        @Test
        fun `delegates to local`() = testScope.runTest {
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
        }
    }

    @Nested
    inner class ObserveAccount {

        @Test
        fun `delegates to local`() = testScope.runTest {
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
        }
    }

    @Nested
    inner class ObserveTransactions {

        @Test
        fun `delegates to local`() = testScope.runTest {
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
        }
    }

    @Nested
    inner class ObserveTransactionSyncState {

        @Test
        fun `emits Idle for an item that hasn't synced`() = testScope.runTest {
            // Test
            val state = repo
                .observeTransactionSyncState("unknown")
                .first()

            // Verify
            assertEquals(TransactionSyncState.Idle, state)
        }

        @Test
        fun `is Syncing while a sync runs, then Idle after it succeeds`() = testScope.runTest {
            // Setup
            val states = collectSyncStates("item1")
            val gate = CompletableDeferred<NetworkResult<TransactionDelta>>()
            coEvery { local.getTransactionCursor("item1") } returns null
            coEvery { remote.syncTransactions("item1", null) } coAnswers { gate.await() }
            val delta = transactionDelta()
            val expectedWhileSyncing = listOf(TransactionSyncState.Idle, TransactionSyncState.Syncing)
            val expected = expectedWhileSyncing + TransactionSyncState.Idle

            // Test
            val sync = launch { repo.syncTransactions("item1") }
            runCurrent()
            val statesWhileSyncing = states.toList()
            gate.complete(NetworkResult.Success(delta))
            sync.join()

            // Verify
            assertEquals(expectedWhileSyncing, statesWhileSyncing)
            assertEquals(expected, states)
            coVerify {
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", null)
                local.upsertTransactions(delta.added + delta.modified)
                local.deleteTransactions(delta.removedIds)
                local.saveTransactionCursor("item1", delta.nextCursor)
            }
        }

        @Test
        fun `is Failed with the remote's cause after a sync fails`() = testScope.runTest {
            // Setup
            val states = collectSyncStates("item1")
            val error = RuntimeException("remote error")
            coEvery { local.getTransactionCursor("item1") } returns null
            coEvery { remote.syncTransactions("item1", null) } returns NetworkResult.Failure(error)
            val expected = listOf(
                TransactionSyncState.Idle,
                TransactionSyncState.Syncing,
                TransactionSyncState.Failed(error),
            )

            // Test
            repo.syncTransactions("item1")

            // Verify
            assertEquals(expected, states)
            coVerify {
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", null)
            }
        }

        @Test
        fun `resets from Failed to Syncing when a new sync starts`() = testScope.runTest {
            // Setup
            val states = collectSyncStates("item1")
            val error = RuntimeException("remote error")
            val gate = CompletableDeferred<NetworkResult<TransactionDelta>>()
            coEvery { local.getTransactionCursor("item1") } returns null
            coEvery {
                remote.syncTransactions("item1", null)
            } returns NetworkResult.Failure(error) coAndThen { gate.await() }
            val expected = listOf(
                TransactionSyncState.Idle,
                TransactionSyncState.Syncing,
                TransactionSyncState.Failed(error),
                TransactionSyncState.Syncing,
            )

            // Test
            repo.syncTransactions("item1")
            backgroundScope.launch { repo.syncTransactions("item1") }
            runCurrent()

            // Verify
            assertEquals(expected, states)
            coVerify(exactly = 2) {
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", null)
            }
        }

        @Test
        fun `reports each item's state independently`() = testScope.runTest {
            // Setup
            val itemTwoStates = collectSyncStates("item2")
            val gate = CompletableDeferred<NetworkResult<TransactionDelta>>()
            val delta = transactionDelta()
            coEvery { local.getTransactionCursor(any()) } returns null
            coEvery { remote.syncTransactions("item1", null) } coAnswers { gate.await() }
            coEvery { remote.syncTransactions("item2", null) } returns NetworkResult.Success(delta)
            val expectedItemTwoStates = listOf(
                TransactionSyncState.Idle,
                TransactionSyncState.Syncing,
                TransactionSyncState.Idle,
            )

            // Test
            backgroundScope.launch { repo.syncTransactions("item1") }
            runCurrent()
            val itemTwoResult = repo.syncTransactions("item2")
            val itemOneState = repo
                .observeTransactionSyncState("item1")
                .first()

            // Verify
            assertEquals(NetworkResult.Success(Unit), itemTwoResult)
            assertEquals(TransactionSyncState.Syncing, itemOneState)
            assertEquals(expectedItemTwoStates, itemTwoStates)
            coVerify {
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", null)
                local.getTransactionCursor("item2")
                remote.syncTransactions("item2", null)
                local.upsertTransactions(delta.added + delta.modified)
                local.deleteTransactions(delta.removedIds)
                local.saveTransactionCursor("item2", delta.nextCursor)
            }
        }
    }

    @Nested
    inner class CreateLinkToken {

        @Test
        fun `delegates to remote`() = testScope.runTest {
            // Setup
            val expected = NetworkResult.Success("lt-abc")
            coEvery { remote.createLinkToken() } returns expected

            // Test
            val result = repo.createLinkToken()

            // Verify
            assertEquals(expected, result)
            coVerify { remote.createLinkToken() }
        }
    }

    @Nested
    inner class LinkInstitution {

        @Test
        fun `succeeds while the new item syncs in the background, then syncs it`() = testScope.runTest {
            // Setup
            val institution = LinkedInstitution("item1", "ins_3", "Chase")
            val gate = CompletableDeferred<NetworkResult<TransactionDelta>>()
            val delta = transactionDelta()
            stubLink(institution)
            coEvery { remote.syncTransactions("item1", null) } coAnswers { gate.await() }
            val expected = NetworkResult.Success(Unit)

            // Test
            val result = repo.linkInstitution("tok", "ins_3", "Chase")
            val stateAfterLink = repo
                .observeTransactionSyncState("item1")
                .first()
            gate.complete(NetworkResult.Success(delta))
            runCurrent()
            val stateAfterSync = repo
                .observeTransactionSyncState("item1")
                .first()

            // Verify
            assertEquals(expected, result)
            assertEquals(TransactionSyncState.Syncing, stateAfterLink)
            assertEquals(TransactionSyncState.Idle, stateAfterSync)
            coVerify {
                remote.exchangePublicToken("tok", "ins_3", "Chase")
                remote.listInstitutions()
                local.upsertInstitutions(emptyList())
                local.upsertAccounts(emptyList())
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", null)
                local.upsertTransactions(delta.added + delta.modified)
                local.deleteTransactions(delta.removedIds)
                local.saveTransactionCursor("item1", delta.nextCursor)
            }
        }

        @Test
        fun `succeeds and leaves the item Failed when the background sync fails`() = testScope.runTest {
            // Setup
            val institution = LinkedInstitution("item1", "ins_3", "Chase")
            val error = RuntimeException("sync error")
            stubLink(institution)
            coEvery { remote.syncTransactions("item1", null) } returns NetworkResult.Failure(error)
            val expected = NetworkResult.Success(Unit)
            val expectedState = TransactionSyncState.Failed(error)

            // Test
            val result = repo.linkInstitution("tok", "ins_3", "Chase")
            runCurrent()
            val state = repo
                .observeTransactionSyncState("item1")
                .first()

            // Verify
            assertEquals(expected, result)
            assertEquals(expectedState, state)
            coVerify {
                remote.exchangePublicToken("tok", "ins_3", "Chase")
                remote.listInstitutions()
                local.upsertInstitutions(emptyList())
                local.upsertAccounts(emptyList())
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", null)
            }
        }

        @Test
        fun `returns failure and doesn't sync when exchangePublicToken fails`() = testScope.runTest {
            // Setup
            val error = RuntimeException("network error")
            coEvery {
                remote.exchangePublicToken(any(), any(), any())
            } returns NetworkResult.Failure(error)
            val expected = NetworkResult.Failure(error)

            // Test
            val result = repo.linkInstitution("tok", "ins_3", "Chase")
            runCurrent()

            // Verify
            assertEquals(expected, result)
            coVerify(exactly = 0) {
                remote.listInstitutions()
                remote.syncTransactions(any(), any())
            }
            coVerify { remote.exchangePublicToken("tok", "ins_3", "Chase") }
        }

        @Test
        fun `returns failure and doesn't sync when listInstitutions fails`() = testScope.runTest {
            // Setup
            coEvery {
                remote.exchangePublicToken(any(), any(), any())
            } returns NetworkResult.Success(LinkedInstitution("item1", "ins_3", "Chase"))
            val error = RuntimeException("server error")
            coEvery { remote.listInstitutions() } returns NetworkResult.Failure(error)
            val expected = NetworkResult.Failure(error)

            // Test
            val result = repo.linkInstitution("tok", "ins_3", "Chase")
            runCurrent()

            // Verify
            assertEquals(expected, result)
            coVerify(exactly = 0) { remote.syncTransactions(any(), any()) }
            coVerify {
                remote.exchangePublicToken("tok", "ins_3", "Chase")
                remote.listInstitutions()
            }
        }
    }

    @Nested
    inner class SyncTransactions {

        @Test
        fun `reads cursor, applies delta, saves new cursor`() = testScope.runTest {
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
            coVerify {
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", "cursor-old")
                local.upsertTransactions(delta.added + delta.modified)
                local.deleteTransactions(delta.removedIds)
                local.saveTransactionCursor("item1", "cursor-new")
            }
        }

        @Test
        fun `passes null cursor on first sync`() = testScope.runTest {
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
            coVerify {
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", null)
                local.upsertTransactions(delta.added + delta.modified)
                local.deleteTransactions(delta.removedIds)
                local.saveTransactionCursor("item1", "cursor-first")
            }
        }

        @Test
        fun `returns failure and skips local writes when remote fails`() = testScope.runTest {
            // Setup
            val error = RuntimeException("remote error")
            coEvery { local.getTransactionCursor("item1") } returns null
            coEvery { remote.syncTransactions("item1", null) } returns NetworkResult.Failure(error)

            // Test
            val result = repo.syncTransactions("item1")

            // Verify
            assertInstanceOf(NetworkResult.Failure::class.java, result)
            coVerify(exactly = 0) {
                local.upsertTransactions(any())
                local.deleteTransactions(any())
                local.saveTransactionCursor(any(), any())
            }
            coVerify {
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", null)
            }
        }

        @Test
        fun `returns failure when a local write throws`() = testScope.runTest {
            // Setup
            val error = IllegalStateException("constraint failed")
            val delta = transactionDelta()
            coEvery { local.getTransactionCursor("item1") } returns null
            coEvery { remote.syncTransactions("item1", null) } returns NetworkResult.Success(delta)
            coEvery { local.upsertTransactions(any()) } throws error
            val expected = NetworkResult.Failure(error)

            // Test
            val result = repo.syncTransactions("item1")

            // Verify
            assertEquals(expected, result)
            coVerify(exactly = 0) { local.saveTransactionCursor(any(), any()) }
            coVerify {
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", null)
                local.upsertTransactions(delta.added + delta.modified)
            }
        }

        @Test
        fun `returns failure and leaves the item Failed when saving the cursor throws`() = testScope.runTest {
            // Setup
            val error = IllegalStateException("disk full")
            val delta = transactionDelta()
            coEvery { local.getTransactionCursor("item1") } returns null
            coEvery { remote.syncTransactions("item1", null) } returns NetworkResult.Success(delta)
            coEvery { local.saveTransactionCursor(any(), any()) } throws error
            val expected = NetworkResult.Failure(error)
            val expectedState = TransactionSyncState.Failed(error)

            // Test
            val result = repo.syncTransactions("item1")
            val state = repo
                .observeTransactionSyncState("item1")
                .first()

            // Verify
            assertEquals(expected, result)
            assertEquals(expectedState, state)
            coVerify {
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", null)
                local.upsertTransactions(delta.added + delta.modified)
                local.deleteTransactions(delta.removedIds)
                local.saveTransactionCursor("item1", delta.nextCursor)
            }
        }

        @Test
        fun `returns failure without calling remote when the cursor read throws`() = testScope.runTest {
            // Setup
            val error = IllegalStateException("disk error")
            coEvery { local.getTransactionCursor("item1") } throws error
            val expected = NetworkResult.Failure(error)

            // Test
            val result = repo.syncTransactions("item1")

            // Verify
            assertEquals(expected, result)
            coVerify(exactly = 0) { remote.syncTransactions(any(), any()) }
            coVerify { local.getTransactionCursor("item1") }
        }

        @Test
        fun `joins the sync already in flight instead of starting another`() = testScope.runTest {
            // Setup
            val gate = CompletableDeferred<NetworkResult<TransactionDelta>>()
            val delta = transactionDelta()
            coEvery { local.getTransactionCursor("item1") } returns null
            coEvery { remote.syncTransactions("item1", null) } coAnswers { gate.await() }
            val expected = NetworkResult.Success(Unit)

            // Test
            val first = async { repo.syncTransactions("item1") }
            val second = async { repo.syncTransactions("item1") }
            runCurrent()
            gate.complete(NetworkResult.Success(delta))
            val firstResult = first.await()
            val secondResult = second.await()

            // Verify
            assertEquals(expected, firstResult)
            assertEquals(expected, secondResult)
            coVerify(exactly = 1) {
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", null)
                local.upsertTransactions(delta.added + delta.modified)
                local.deleteTransactions(delta.removedIds)
                local.saveTransactionCursor("item1", delta.nextCursor)
            }
        }

        @Test
        fun `starts a fresh sync once the previous one has ended`() = testScope.runTest {
            // Setup
            val delta = transactionDelta()
            coEvery { local.getTransactionCursor("item1") } returns null
            coEvery { remote.syncTransactions("item1", null) } returns NetworkResult.Success(delta)

            // Test
            repo.syncTransactions("item1")
            repo.syncTransactions("item1")

            // Verify
            coVerify(exactly = 2) {
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", null)
                local.upsertTransactions(delta.added + delta.modified)
                local.deleteTransactions(delta.removedIds)
                local.saveTransactionCursor("item1", delta.nextCursor)
            }
        }

        @Test
        fun `keeps syncing for other callers when one joined caller is cancelled`() = testScope.runTest {
            // Setup
            val gate = CompletableDeferred<NetworkResult<TransactionDelta>>()
            val delta = transactionDelta()
            coEvery { local.getTransactionCursor("item1") } returns null
            coEvery { remote.syncTransactions("item1", null) } coAnswers { gate.await() }
            val expected = NetworkResult.Success(Unit)

            // Test
            val cancelled = launch { repo.syncTransactions("item1") }
            val remaining = async { repo.syncTransactions("item1") }
            runCurrent()
            cancelled.cancelAndJoin()
            gate.complete(NetworkResult.Success(delta))
            val result = remaining.await()

            // Verify
            assertEquals(expected, result)
            coVerify(exactly = 1) {
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", null)
                local.upsertTransactions(delta.added + delta.modified)
                local.deleteTransactions(delta.removedIds)
                local.saveTransactionCursor("item1", delta.nextCursor)
            }
        }

        @Test
        fun `ends Idle, not Failed, when cancelled during a local write`() = testScope.runTest {
            // Setup
            val delta = transactionDelta()
            coEvery { local.getTransactionCursor("item1") } returns null
            coEvery { remote.syncTransactions("item1", null) } returns NetworkResult.Success(delta)
            coEvery { local.upsertTransactions(any()) } coAnswers { awaitCancellation() }

            // Test
            val caller = async { repo.syncTransactions("item1") }
            runCurrent()
            repo.clearLocalCache()
            val result = caller.await()
            val state = repo
                .observeTransactionSyncState("item1")
                .first()

            // Verify
            assertInstanceOf(CancellationException::class.java, (result as NetworkResult.Failure).cause)
            assertEquals(TransactionSyncState.Idle, state)
            coVerify(exactly = 0) { local.saveTransactionCursor(any(), any()) }
            coVerify {
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", null)
                local.upsertTransactions(delta.added + delta.modified)
                local.deleteAllInstitutions()
            }
        }

        @Test
        fun `keeps syncing when the caller is cancelled`() = testScope.runTest {
            // Setup
            val gate = CompletableDeferred<NetworkResult<TransactionDelta>>()
            val delta = transactionDelta()
            coEvery { local.getTransactionCursor("item1") } returns null
            coEvery { remote.syncTransactions("item1", null) } coAnswers { gate.await() }

            // Test
            val caller = launch { repo.syncTransactions("item1") }
            runCurrent()
            caller.cancelAndJoin()
            gate.complete(NetworkResult.Success(delta))
            runCurrent()
            val state = repo
                .observeTransactionSyncState("item1")
                .first()

            // Verify
            assertEquals(TransactionSyncState.Idle, state)
            coVerify {
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", null)
                local.upsertTransactions(delta.added + delta.modified)
                local.deleteTransactions(delta.removedIds)
                local.saveTransactionCursor("item1", delta.nextCursor)
            }
        }

        @Test
        fun `returns failure when the sync is cancelled by clearLocalCache`() = testScope.runTest {
            // Setup
            coEvery { local.getTransactionCursor("item1") } returns null
            coEvery { remote.syncTransactions("item1", null) } coAnswers { awaitCancellation() }

            // Test
            val caller = async { repo.syncTransactions("item1") }
            runCurrent()
            repo.clearLocalCache()
            val result = caller.await()

            // Verify
            assertInstanceOf(NetworkResult.Failure::class.java, result)
            assertInstanceOf(CancellationException::class.java, (result as NetworkResult.Failure).cause)
            coVerify {
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", null)
                local.deleteAllInstitutions()
            }
        }
    }

    @Nested
    inner class DisconnectInstitution {

        @Test
        fun `calls remote then deletes locally`() = testScope.runTest {
            // Setup
            coEvery { remote.disconnectInstitution("item1") } returns NetworkResult.Success(Unit)

            // Test
            val result = repo.disconnectInstitution("item1")

            // Verify
            assertInstanceOf(NetworkResult.Success::class.java, result)
            coVerify {
                remote.disconnectInstitution("item1")
                local.deleteInstitution("item1")
            }
        }

        @Test
        fun `returns failure and skips local delete when remote fails`() = testScope.runTest {
            // Setup
            val error = RuntimeException("remote error")
            coEvery { remote.disconnectInstitution("item1") } returns NetworkResult.Failure(error)

            // Test
            val result = repo.disconnectInstitution("item1")

            // Verify
            assertInstanceOf(NetworkResult.Failure::class.java, result)
            coVerify(exactly = 0) { local.deleteInstitution(any()) }
            coVerify { remote.disconnectInstitution("item1") }
        }
    }

    @Nested
    inner class RefreshInstitutions {

        @Test
        fun `upserts institutions and accounts from remote`() = testScope.runTest {
            // Setup
            val institution = LinkedInstitution("item1", "ins_3", "Chase")
            val accounts = listOf(financialAccount())
            val institutions = listOf(LinkedInstitutionWithAccounts(institution, accounts))
            coEvery { remote.listInstitutions() } returns NetworkResult.Success(institutions)

            // Test
            val result = repo.refreshInstitutions()

            // Verify
            assertInstanceOf(NetworkResult.Success::class.java, result)
            coVerify {
                remote.listInstitutions()
                local.upsertInstitutions(listOf(institution))
                local.upsertAccounts(accounts)
            }
        }

        @Test
        fun `returns failure when remote fails`() = testScope.runTest {
            // Setup
            val error = RuntimeException("remote error")
            coEvery { remote.listInstitutions() } returns NetworkResult.Failure(error)

            // Test
            val result = repo.refreshInstitutions()

            // Verify
            assertInstanceOf(NetworkResult.Failure::class.java, result)
            coVerify(exactly = 0) { local.upsertInstitutions(any()) }
            coVerify { remote.listInstitutions() }
        }
    }

    @Nested
    inner class ClearLocalCache {

        @Test
        fun `deletes all institutions locally without calling remote`() = testScope.runTest {
            // Setup
            val expected = NetworkResult.Success(Unit)

            // Test
            val result = repo.clearLocalCache()

            // Verify
            assertEquals(expected, result)
            coVerify { local.deleteAllInstitutions() }
        }

        @Test
        fun `returns failure when the local delete throws`() = testScope.runTest {
            // Setup
            val error = RuntimeException("disk error")
            coEvery { local.deleteAllInstitutions() } throws error
            val expected = NetworkResult.Failure(error)

            // Test
            val result = repo.clearLocalCache()

            // Verify
            assertEquals(expected, result)
            coVerify { local.deleteAllInstitutions() }
        }

        @Test
        fun `cancels an in-flight background sync before deleting`() = testScope.runTest {
            // Setup
            val gate = CompletableDeferred<NetworkResult<TransactionDelta>>()
            stubLink(LinkedInstitution("item1", "ins_3", "Chase"))
            coEvery { remote.syncTransactions("item1", null) } coAnswers { gate.await() }
            val expected = NetworkResult.Success(Unit)

            // Test
            repo.linkInstitution("tok", "ins_3", "Chase")
            runCurrent()
            val result = repo.clearLocalCache()
            gate.complete(NetworkResult.Success(transactionDelta()))
            runCurrent()
            val state = repo
                .observeTransactionSyncState("item1")
                .first()

            // Verify
            assertEquals(expected, result)
            assertEquals(TransactionSyncState.Idle, state)
            coVerify(exactly = 0) {
                local.upsertTransactions(any())
                local.deleteTransactions(any())
                local.saveTransactionCursor(any(), any())
            }
            coVerify {
                remote.exchangePublicToken("tok", "ins_3", "Chase")
                remote.listInstitutions()
                local.upsertInstitutions(emptyList())
                local.upsertAccounts(emptyList())
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", null)
                local.deleteAllInstitutions()
            }
        }

        @Test
        fun `resets a Failed item to Idle`() = testScope.runTest {
            // Setup
            val error = RuntimeException("remote error")
            coEvery { local.getTransactionCursor("item1") } returns null
            coEvery { remote.syncTransactions("item1", null) } returns NetworkResult.Failure(error)
            val expectedBeforeClear = TransactionSyncState.Failed(error)

            // Test
            repo.syncTransactions("item1")
            val stateBeforeClear = repo
                .observeTransactionSyncState("item1")
                .first()
            repo.clearLocalCache()
            val stateAfterClear = repo
                .observeTransactionSyncState("item1")
                .first()

            // Verify
            assertEquals(expectedBeforeClear, stateBeforeClear)
            assertEquals(TransactionSyncState.Idle, stateAfterClear)
            coVerify {
                local.getTransactionCursor("item1")
                remote.syncTransactions("item1", null)
                local.deleteAllInstitutions()
            }
        }
    }

    private fun TestScope.collectSyncStates(
        itemId: String,
    ): List<TransactionSyncState> {
        val states = mutableListOf<TransactionSyncState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo
                .observeTransactionSyncState(itemId)
                .toList(states)
        }
        return states
    }

    private fun stubLink(
        institution: LinkedInstitution,
    ) {
        coEvery {
            remote.exchangePublicToken("tok", institution.institutionId, institution.institutionName)
        } returns NetworkResult.Success(institution)
        coEvery { remote.listInstitutions() } returns NetworkResult.Success(emptyList())
        coEvery { local.getTransactionCursor(institution.itemId) } returns null
    }

    private fun transactionDelta(): TransactionDelta =
        TransactionDelta(
            added = listOf(transaction(id = "tx1")),
            modified = listOf(transaction(id = "tx2")),
            removedIds = listOf("tx3"),
            nextCursor = "cursor-new",
        )

    private fun financialAccount(
        id: String = "acc1",
    ): FinancialAccount =
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

    private fun transaction(
        id: String = "tx1",
    ): Transaction =
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
