package com.lucaskivi.cream.screens.accountdetail

import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.FinancialAccountType
import com.lucaskivi.cream.core.institution.model.Transaction
import com.lucaskivi.cream.core.institution.model.TransactionSyncState
import com.lucaskivi.cream.data.repository.institution.InstitutionRepository
import com.lucaskivi.cream.screens.accountdetail.model.TransactionDateGroup
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailViewModelTest {

    private val institutionRepository: InstitutionRepository = mockk()
    private val account = FinancialAccount(
        accountId = ACCOUNT_ID,
        itemId = ITEM_ID,
        displayName = "Total Checking",
        mask = "1234",
        type = FinancialAccountType.DEPOSITORY,
        subtype = "checking",
        balanceCurrent = 100.0,
        balanceAvailable = 90.0,
        isoCurrencyCode = "USD",
    )
    private val accountFlow = MutableStateFlow<FinancialAccount?>(account)
    private val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())
    private val syncStateFlow = MutableStateFlow<TransactionSyncState>(TransactionSyncState.Idle)
    private val syncFailure = TransactionSyncState.Failed(RuntimeException("sync error"))

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        confirmVerified(institutionRepository)
        Dispatchers.resetMain()
    }

    @Nested
    inner class Init {

        @Test
        fun `maps the account and groups its transactions by date in repository order`() {
            // Setup
            val newer = transaction("t1", LocalDate.of(2026, 11, 9))
            val newerSameDay = transaction("t2", LocalDate.of(2026, 11, 9))
            val older = transaction("t3", LocalDate.of(2026, 11, 7))
            transactionsFlow.value = listOf(newer, newerSameDay, older)
            stubObservers()
            val expectedGroups = listOf(
                TransactionDateGroup(LocalDate.of(2026, 11, 9), listOf(newer, newerSameDay)),
                TransactionDateGroup(LocalDate.of(2026, 11, 7), listOf(older)),
            )

            // Test
            val viewModel = createViewModel()

            // Verify
            val uiState = viewModel.uiState.value
            assertFalse(uiState.isLoading)
            assertEquals(account, uiState.account)
            assertFalse(uiState.isAccountMissing)
            assertEquals(expectedGroups, uiState.transactionGroups)
            assertFalse(uiState.isSyncingTransactions)
            assertFalse(uiState.isTransactionSyncFailed)
            verifyObservers()
        }

        @Test
        fun `stays loading until the account emits`() {
            // Setup
            every { institutionRepository.observeAccount(ACCOUNT_ID) } returns MutableSharedFlow()
            every { institutionRepository.observeTransactions(ACCOUNT_ID) } returns transactionsFlow

            // Test
            val viewModel = createViewModel()

            // Verify
            assertTrue(viewModel.uiState.value.isLoading)
            verify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
            }
        }

        @Test
        fun `flags the account as missing when it stops existing`() {
            // Setup
            stubObservers()
            val viewModel = createViewModel()

            // Test
            accountFlow.value = null

            // Verify
            assertTrue(viewModel.uiState.value.isAccountMissing)
            assertNull(viewModel.uiState.value.account)
            verifyObservers()
        }

        @Test
        fun `shows syncing while the account's institution syncs`() {
            // Setup
            syncStateFlow.value = TransactionSyncState.Syncing
            stubObservers()

            // Test
            val viewModel = createViewModel()

            // Verify
            assertTrue(viewModel.uiState.value.isSyncingTransactions)
            assertFalse(viewModel.uiState.value.isTransactionSyncFailed)
            verifyObservers()
        }

        @Test
        fun `shows the inline error after a background sync fails`() {
            // Setup
            syncStateFlow.value = syncFailure
            stubObservers()

            // Test
            val viewModel = createViewModel()

            // Verify
            assertTrue(viewModel.uiState.value.isTransactionSyncFailed)
            assertFalse(viewModel.uiState.value.isSyncingTransactions)
            verifyObservers()
        }

        @Test
        fun `follows the sync state of the account's current institution`() {
            // Setup
            val otherItemState = MutableStateFlow<TransactionSyncState>(TransactionSyncState.Syncing)
            stubObservers()
            every { institutionRepository.observeTransactionSyncState(OTHER_ITEM_ID) } returns otherItemState
            val viewModel = createViewModel()

            // Test
            accountFlow.value = account.copy(itemId = OTHER_ITEM_ID)

            // Verify
            assertTrue(viewModel.uiState.value.isSyncingTransactions)
            verify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                institutionRepository.observeTransactionSyncState(ITEM_ID)
                institutionRepository.observeTransactionSyncState(OTHER_ITEM_ID)
            }
        }

        @Test
        fun `shows transactions that arrive mid-sync`() {
            // Setup
            syncStateFlow.value = TransactionSyncState.Syncing
            stubObservers()
            val viewModel = createViewModel()
            val arrived = transaction("t1", LocalDate.of(2026, 11, 9))
            val expectedGroups = listOf(TransactionDateGroup(arrived.date, listOf(arrived)))

            // Test
            transactionsFlow.value = listOf(arrived)

            // Verify
            assertEquals(expectedGroups, viewModel.uiState.value.transactionGroups)
            assertTrue(viewModel.uiState.value.isSyncingTransactions)
            verifyObservers()
        }
    }

    @Nested
    inner class Refresh {

        @Test
        fun `refreshes the balance, then syncs the account's institution`() {
            // Setup
            stubObservers()
            coEvery { institutionRepository.refreshInstitutions() } returns NetworkResult.Success(Unit)
            coEvery { institutionRepository.syncTransactions(ITEM_ID) } returns NetworkResult.Success(Unit)
            val viewModel = createViewModel()

            // Test
            viewModel.refresh()

            // Verify
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertNull(viewModel.uiState.value.error)
            coVerifyOrder {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                institutionRepository.observeTransactionSyncState(ITEM_ID)
                institutionRepository.refreshInstitutions()
                institutionRepository.syncTransactions(ITEM_ID)
            }
        }

        @Test
        fun `shows refreshing until the refresh finishes`() {
            // Setup
            val refreshResult = CompletableDeferred<NetworkResult<Unit>>()
            stubObservers()
            coEvery { institutionRepository.refreshInstitutions() } coAnswers { refreshResult.await() }
            coEvery { institutionRepository.syncTransactions(ITEM_ID) } returns NetworkResult.Success(Unit)
            val viewModel = createViewModel()

            // Test
            viewModel.refresh()
            val isRefreshingInFlight = viewModel.uiState.value.isRefreshing
            refreshResult.complete(NetworkResult.Success(Unit))

            // Verify
            assertTrue(isRefreshingInFlight)
            assertFalse(viewModel.uiState.value.isRefreshing)
            coVerify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                institutionRepository.observeTransactionSyncState(ITEM_ID)
                institutionRepository.refreshInstitutions()
                institutionRepository.syncTransactions(ITEM_ID)
            }
        }

        @Test
        fun `skips the sync and shows an error when the balance refresh fails`() {
            // Setup
            val cached = transaction("t1", LocalDate.of(2026, 11, 9))
            transactionsFlow.value = listOf(cached)
            stubObservers()
            coEvery {
                institutionRepository.refreshInstitutions()
            } returns NetworkResult.Failure(RuntimeException("network"))
            val viewModel = createViewModel()
            val expectedGroups = listOf(TransactionDateGroup(cached.date, listOf(cached)))

            // Test
            viewModel.refresh()

            // Verify
            assertEquals("Couldn't refresh your balance.", viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals(account, viewModel.uiState.value.account)
            assertEquals(expectedGroups, viewModel.uiState.value.transactionGroups)
            coVerify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                institutionRepository.observeTransactionSyncState(ITEM_ID)
                institutionRepository.refreshInstitutions()
            }
            coVerify(exactly = 0) { institutionRepository.syncTransactions(any()) }
        }

        @Test
        fun `reports its own sync failure only in the error message`() {
            // Setup
            val cached = transaction("t1", LocalDate.of(2026, 11, 9))
            val syncResult = CompletableDeferred<NetworkResult<Unit>>()
            transactionsFlow.value = listOf(cached)
            stubObservers()
            coEvery { institutionRepository.refreshInstitutions() } returns NetworkResult.Success(Unit)
            coEvery { institutionRepository.syncTransactions(ITEM_ID) } coAnswers { syncResult.await() }
            val viewModel = createViewModel()
            val expectedGroups = listOf(TransactionDateGroup(cached.date, listOf(cached)))

            // Test
            viewModel.refresh()
            syncStateFlow.value = TransactionSyncState.Syncing
            val wasSyncingShown = viewModel.uiState.value.isSyncingTransactions
            syncStateFlow.value = syncFailure
            syncResult.complete(NetworkResult.Failure(syncFailure.cause))

            // Verify
            assertTrue(wasSyncingShown)
            assertEquals("Couldn't sync transactions.", viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isTransactionSyncFailed)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals(expectedGroups, viewModel.uiState.value.transactionGroups)
            coVerify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                institutionRepository.observeTransactionSyncState(ITEM_ID)
                institutionRepository.refreshInstitutions()
                institutionRepository.syncTransactions(ITEM_ID)
            }
        }

        @Test
        fun `reports a failed background sync it joined only in the error message`() {
            // Setup
            syncStateFlow.value = TransactionSyncState.Syncing
            stubObservers()
            coEvery { institutionRepository.refreshInstitutions() } returns NetworkResult.Success(Unit)
            coEvery { institutionRepository.syncTransactions(ITEM_ID) } coAnswers {
                syncStateFlow.value = syncFailure
                NetworkResult.Failure(syncFailure.cause)
            }
            val viewModel = createViewModel()

            // Test
            viewModel.refresh()

            // Verify
            assertEquals("Couldn't sync transactions.", viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isTransactionSyncFailed)
            coVerify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                institutionRepository.observeTransactionSyncState(ITEM_ID)
                institutionRepository.refreshInstitutions()
                institutionRepository.syncTransactions(ITEM_ID)
            }
        }

        @Test
        fun `clears the inline error as soon as it starts`() {
            // Setup
            val refreshResult = CompletableDeferred<NetworkResult<Unit>>()
            syncStateFlow.value = syncFailure
            stubObservers()
            coEvery { institutionRepository.refreshInstitutions() } coAnswers { refreshResult.await() }
            val viewModel = createViewModel()

            // Test
            viewModel.refresh()

            // Verify
            assertFalse(viewModel.uiState.value.isTransactionSyncFailed)
            coVerify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                institutionRepository.observeTransactionSyncState(ITEM_ID)
                institutionRepository.refreshInstitutions()
            }
        }

        @Test
        fun `shows a later background failure inline again`() {
            // Setup
            syncStateFlow.value = syncFailure
            stubObservers()
            coEvery {
                institutionRepository.refreshInstitutions()
            } returns NetworkResult.Failure(RuntimeException("network"))
            val viewModel = createViewModel()
            viewModel.refresh()

            // Test
            syncStateFlow.value = TransactionSyncState.Syncing
            syncStateFlow.value = syncFailure

            // Verify
            assertTrue(viewModel.uiState.value.isTransactionSyncFailed)
            coVerify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                institutionRepository.observeTransactionSyncState(ITEM_ID)
                institutionRepository.refreshInstitutions()
            }
        }

        @Test
        fun `shows a background failure right after a successful refresh inline`() {
            // Setup
            stubObservers()
            coEvery { institutionRepository.refreshInstitutions() } returns NetworkResult.Success(Unit)
            coEvery { institutionRepository.syncTransactions(ITEM_ID) } returns NetworkResult.Success(Unit)
            val viewModel = createViewModel()
            viewModel.refresh()

            // Test
            syncStateFlow.value = syncFailure

            // Verify
            assertTrue(viewModel.uiState.value.isTransactionSyncFailed)
            coVerify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                institutionRepository.observeTransactionSyncState(ITEM_ID)
                institutionRepository.refreshInstitutions()
                institutionRepository.syncTransactions(ITEM_ID)
            }
        }

        @Test
        fun `does nothing before the account loads`() {
            // Setup
            every { institutionRepository.observeAccount(ACCOUNT_ID) } returns MutableSharedFlow()
            every { institutionRepository.observeTransactions(ACCOUNT_ID) } returns transactionsFlow
            val viewModel = createViewModel()

            // Test
            viewModel.refresh()

            // Verify
            assertFalse(viewModel.uiState.value.isRefreshing)
            verify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
            }
            coVerify(exactly = 0) {
                institutionRepository.refreshInstitutions()
                institutionRepository.syncTransactions(any())
            }
        }
    }

    @Nested
    inner class ClearError {

        @Test
        fun `clears the error`() {
            // Setup
            stubObservers()
            coEvery {
                institutionRepository.refreshInstitutions()
            } returns NetworkResult.Failure(RuntimeException("network"))
            val viewModel = createViewModel()
            viewModel.refresh()
            val errorBeforeClear = viewModel.uiState.value.error

            // Test
            viewModel.clearError()

            // Verify
            assertEquals("Couldn't refresh your balance.", errorBeforeClear)
            assertNull(viewModel.uiState.value.error)
            coVerify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                institutionRepository.observeTransactionSyncState(ITEM_ID)
                institutionRepository.refreshInstitutions()
            }
        }
    }

    private fun stubObservers() {
        every { institutionRepository.observeAccount(ACCOUNT_ID) } returns accountFlow
        every { institutionRepository.observeTransactions(ACCOUNT_ID) } returns transactionsFlow
        every { institutionRepository.observeTransactionSyncState(ITEM_ID) } returns syncStateFlow
    }

    private fun verifyObservers() {
        verify {
            institutionRepository.observeAccount(ACCOUNT_ID)
            institutionRepository.observeTransactions(ACCOUNT_ID)
            institutionRepository.observeTransactionSyncState(ITEM_ID)
        }
    }

    private fun createViewModel() =
        AccountDetailViewModel(
            accountId = ACCOUNT_ID,
            institutionRepository = institutionRepository,
        )

    private fun transaction(
        transactionId: String,
        date: LocalDate,
    ): Transaction =
        Transaction(
            transactionId = transactionId,
            accountId = ACCOUNT_ID,
            amount = 12.5,
            isoCurrencyCode = "USD",
            date = date,
            name = "COFFEE SHOP 123",
            merchantName = "Coffee Shop",
            pending = false,
            category = null,
        )

    private companion object {
        const val ACCOUNT_ID = "acc1"
        const val ITEM_ID = "item1"
        const val OTHER_ITEM_ID = "item2"
    }
}
