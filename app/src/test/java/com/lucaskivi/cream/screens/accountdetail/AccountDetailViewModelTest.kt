package com.lucaskivi.cream.screens.accountdetail

import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.FinancialAccountType
import com.lucaskivi.cream.core.institution.model.Transaction
import com.lucaskivi.cream.data.repository.institution.InstitutionRepository
import com.lucaskivi.cream.ui.format.CurrencyFormatter
import com.lucaskivi.cream.ui.format.DateFormatter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
    private val currencyFormatter: CurrencyFormatter = mockk()
    private val dateFormatter: DateFormatter = mockk()
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
    private val accountSummary = AccountSummary(
        name = "Total Checking",
        details = "•••• 1234 · Depository",
        balance = "$100.00",
    )
    private val accountFlow = MutableStateFlow<FinancialAccount?>(account)
    private val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())
    private val loadedEmpty = AccountDetailContent.Loaded(accountSummary, emptyList())

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { currencyFormatter.format(100.0, "USD", any()) } returns "$100.00"
    }

    @AfterEach
    fun tearDown() {
        confirmVerified(institutionRepository, currencyFormatter, dateFormatter)
        Dispatchers.resetMain()
    }

    @Nested
    inner class Init {

        @Test
        fun `shows the account and groups its transactions by date in repository order`() {
            // Setup
            val nov9 = LocalDate.of(2026, 11, 9)
            val nov7 = LocalDate.of(2026, 11, 7)
            val newer = transaction("t1", nov9, amount = 12.5)
            val newerSameDay = transaction("t2", nov9, amount = -20.0)
            val older = transaction("t3", nov7, amount = 3.0)
            transactionsFlow.value = listOf(newer, newerSameDay, older)
            stubObservers()
            every { dateFormatter.format(nov9, any()) } returns "Nov 9, 2026"
            every { dateFormatter.format(nov7, any()) } returns "Nov 7, 2026"
            every { currencyFormatter.format(12.5, "USD", any()) } returns "$12.50"
            every { currencyFormatter.format(20.0, "USD", any()) } returns "$20.00"
            every { currencyFormatter.format(3.0, "USD", any()) } returns "$3.00"
            val expectedGroups = listOf(
                TransactionDateGroup(
                    key = "2026-11-09",
                    date = "Nov 9, 2026",
                    transactions = listOf(
                        transactionItem("t1", amount = "-$12.50", isInflow = false),
                        transactionItem("t2", amount = "+$20.00", isInflow = true),
                    ),
                ),
                TransactionDateGroup(
                    key = "2026-11-07",
                    date = "Nov 7, 2026",
                    transactions = listOf(transactionItem("t3", amount = "-$3.00", isInflow = false)),
                ),
            )
            val expected = AccountDetailContent.Loaded(accountSummary, expectedGroups)

            // Test
            val viewModel = createViewModel()

            // Verify
            assertEquals(expected, viewModel.uiState.value.content)
            verify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                currencyFormatter.format(100.0, "USD", any())
                currencyFormatter.format(12.5, "USD", any())
                currencyFormatter.format(20.0, "USD", any())
                dateFormatter.format(nov9, any())
                currencyFormatter.format(3.0, "USD", any())
                dateFormatter.format(nov7, any())
            }
        }

        @Test
        fun `falls back to the account's currency and shows pending and merchant-less transactions`() {
            // Setup
            val date = LocalDate.of(2026, 11, 9)
            val pending = transaction("t1", date, amount = 12.5).copy(
                isoCurrencyCode = null,
                merchantName = null,
                pending = true,
            )
            transactionsFlow.value = listOf(pending)
            stubObservers()
            every { dateFormatter.format(date, any()) } returns "Nov 9, 2026"
            every { currencyFormatter.format(12.5, "USD", any()) } returns "$12.50"
            val expectedItem = transactionItem("t1", amount = "-$12.50", isInflow = false).copy(
                name = "COFFEE SHOP 123",
                isPending = true,
            )

            // Test
            val viewModel = createViewModel()

            // Verify
            val content = viewModel.uiState.value.content as AccountDetailContent.Loaded
            assertEquals(listOf(expectedItem), content.transactionGroups.single().transactions)
            verify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                currencyFormatter.format(100.0, "USD", any())
                currencyFormatter.format(12.5, "USD", any())
                dateFormatter.format(date, any())
            }
        }

        @Test
        fun `stays loading until the account emits`() {
            // Setup
            every { institutionRepository.observeAccount(ACCOUNT_ID) } returns MutableSharedFlow()
            every { institutionRepository.observeTransactions(ACCOUNT_ID) } returns transactionsFlow

            // Test
            val viewModel = createViewModel()

            // Verify
            assertEquals(AccountDetailContent.Loading, viewModel.uiState.value.content)
            verifyObservers()
        }

        @Test
        fun `closes the screen when the account stops existing`() = runTest {
            // Setup
            stubObservers()
            val viewModel = createViewModel()

            // Test
            accountFlow.value = null

            // Verify
            assertEquals(AccountDetailEvent.Close, viewModel.events.first())
            verify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                currencyFormatter.format(100.0, "USD", any())
            }
        }

        @Test
        fun `shows transactions as they arrive`() {
            // Setup
            stubObservers()
            val viewModel = createViewModel()
            val date = LocalDate.of(2026, 11, 9)
            val arrived = transaction("t1", date, amount = 12.5)
            every { dateFormatter.format(date, any()) } returns "Nov 9, 2026"
            every { currencyFormatter.format(12.5, "USD", any()) } returns "$12.50"
            val expectedGroups = listOf(
                TransactionDateGroup(
                    key = "2026-11-09",
                    date = "Nov 9, 2026",
                    transactions = listOf(transactionItem("t1", amount = "-$12.50", isInflow = false)),
                ),
            )
            val expected = AccountDetailContent.Loaded(accountSummary, expectedGroups)

            // Test
            transactionsFlow.value = listOf(arrived)

            // Verify
            assertEquals(expected, viewModel.uiState.value.content)
            verify(exactly = 2) { currencyFormatter.format(100.0, "USD", any()) }
            verify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                currencyFormatter.format(12.5, "USD", any())
                dateFormatter.format(date, any())
            }
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
            assertNull(viewModel.uiState.value.errorMessage)
            coVerify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                currencyFormatter.format(100.0, "USD", any())
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
            val expectedResult = NetworkResult.Success(Unit)

            // Test
            viewModel.refresh()
            val isRefreshingInFlight = viewModel.uiState.value.isRefreshing
            refreshResult.complete(expectedResult)

            // Verify
            assertTrue(isRefreshingInFlight)
            assertFalse(viewModel.uiState.value.isRefreshing)
            coVerify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                currencyFormatter.format(100.0, "USD", any())
                institutionRepository.refreshInstitutions()
                institutionRepository.syncTransactions(ITEM_ID)
            }
        }

        @Test
        fun `skips the sync and shows an error when the balance refresh fails`() {
            // Setup
            stubObservers()
            coEvery {
                institutionRepository.refreshInstitutions()
            } returns NetworkResult.Failure(RuntimeException("network"))
            val viewModel = createViewModel()

            // Test
            viewModel.refresh()

            // Verify
            assertEquals("Couldn't refresh your balance.", viewModel.uiState.value.errorMessage)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals(loadedEmpty, viewModel.uiState.value.content)
            coVerify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                currencyFormatter.format(100.0, "USD", any())
                institutionRepository.refreshInstitutions()
            }
            coVerify(exactly = 0) { institutionRepository.syncTransactions(any()) }
        }

        @Test
        fun `shows an error and keeps the account when the sync fails`() {
            // Setup
            stubObservers()
            coEvery { institutionRepository.refreshInstitutions() } returns NetworkResult.Success(Unit)
            coEvery {
                institutionRepository.syncTransactions(ITEM_ID)
            } returns NetworkResult.Failure(RuntimeException("sync error"))
            val viewModel = createViewModel()

            // Test
            viewModel.refresh()

            // Verify
            assertEquals("Couldn't sync transactions.", viewModel.uiState.value.errorMessage)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals(loadedEmpty, viewModel.uiState.value.content)
            coVerify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                currencyFormatter.format(100.0, "USD", any())
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
            verifyObservers()
            coVerify(exactly = 0) {
                institutionRepository.refreshInstitutions()
                institutionRepository.syncTransactions(any())
            }
        }
    }

    @Nested
    inner class DismissError {

        @Test
        fun `closes the error dialog`() {
            // Setup
            stubObservers()
            coEvery {
                institutionRepository.refreshInstitutions()
            } returns NetworkResult.Failure(RuntimeException("network"))
            val viewModel = createViewModel()
            viewModel.refresh()
            val errorBeforeDismiss = viewModel.uiState.value.errorMessage

            // Test
            viewModel.dismissError()

            // Verify
            assertEquals("Couldn't refresh your balance.", errorBeforeDismiss)
            assertNull(viewModel.uiState.value.errorMessage)
            coVerify {
                institutionRepository.observeAccount(ACCOUNT_ID)
                institutionRepository.observeTransactions(ACCOUNT_ID)
                currencyFormatter.format(100.0, "USD", any())
                institutionRepository.refreshInstitutions()
            }
        }
    }

    private fun stubObservers() {
        every { institutionRepository.observeAccount(ACCOUNT_ID) } returns accountFlow
        every { institutionRepository.observeTransactions(ACCOUNT_ID) } returns transactionsFlow
    }

    private fun verifyObservers() {
        verify {
            institutionRepository.observeAccount(ACCOUNT_ID)
            institutionRepository.observeTransactions(ACCOUNT_ID)
        }
    }

    private fun createViewModel() =
        AccountDetailViewModel(
            accountId = ACCOUNT_ID,
            institutionRepository = institutionRepository,
            currencyFormatter = currencyFormatter,
            dateFormatter = dateFormatter,
        )

    private fun transaction(
        transactionId: String,
        date: LocalDate,
        amount: Double,
    ): Transaction =
        Transaction(
            transactionId = transactionId,
            accountId = ACCOUNT_ID,
            amount = amount,
            isoCurrencyCode = "USD",
            date = date,
            name = "COFFEE SHOP 123",
            merchantName = "Coffee Shop",
            pending = false,
            category = null,
        )

    private fun transactionItem(
        transactionId: String,
        amount: String,
        isInflow: Boolean,
    ): TransactionItem =
        TransactionItem(
            transactionId = transactionId,
            name = "Coffee Shop",
            amount = amount,
            isInflow = isInflow,
            isPending = false,
        )

    private companion object {
        const val ACCOUNT_ID = "acc1"
        const val ITEM_ID = "item1"
    }
}
