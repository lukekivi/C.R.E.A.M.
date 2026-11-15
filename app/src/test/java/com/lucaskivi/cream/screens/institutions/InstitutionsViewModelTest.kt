package com.lucaskivi.cream.screens.institutions

import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.FinancialAccountType
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.core.institution.model.TransactionSyncState
import com.lucaskivi.cream.data.repository.institution.InstitutionRepository
import com.lucaskivi.cream.link.model.InstitutionLinkResult
import com.lucaskivi.cream.ui.format.CurrencyFormatter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class InstitutionsViewModelTest {

    private val institutionRepository: InstitutionRepository = mockk()
    private val currencyFormatter: CurrencyFormatter = mockk()
    private val chase = institutionWithAccounts("item1", "Chase")
    private val amex = institutionWithAccounts("item2", "Amex")
    private val chaseItem = InstitutionItem(chase.institution, emptyList(), InstitutionSyncStatus.Idle)
    private val amexItem = InstitutionItem(amex.institution, emptyList(), InstitutionSyncStatus.Idle)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        confirmVerified(institutionRepository, currencyFormatter)
        Dispatchers.resetMain()
    }

    @Nested
    inner class Init {

        @Test
        fun `lists every linked institution`() {
            // Setup
            stubInstitutions(listOf(chase, amex))
            val expected = InstitutionsContent.Loaded(listOf(chaseItem, amexItem))

            // Test
            val viewModel = createViewModel()

            // Verify
            assertEquals(expected, viewModel.uiState.value.content)
            verify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.observeTransactionSyncState("item1")
                institutionRepository.observeTransactionSyncState("item2")
            }
        }

        @Test
        fun `shows empty when no institutions are linked`() {
            // Setup
            stubInstitutions(emptyList())

            // Test
            val viewModel = createViewModel()

            // Verify
            assertEquals(InstitutionsContent.Empty, viewModel.uiState.value.content)
            verify { institutionRepository.observeInstitutionsWithAccounts() }
        }

        @Test
        fun `lists an institution added to the list`() {
            // Setup
            val institutions = MutableStateFlow(listOf(chase))
            every { institutionRepository.observeInstitutionsWithAccounts() } returns institutions
            stubSyncStates()
            val viewModel = createViewModel()
            val expected = InstitutionsContent.Loaded(listOf(chaseItem, amexItem))

            // Test
            institutions.value = listOf(chase, amex)

            // Verify
            assertEquals(expected, viewModel.uiState.value.content)
            verify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.observeTransactionSyncState("item1")
                institutionRepository.observeTransactionSyncState("item2")
            }
        }

        @Test
        fun `shows each institution's sync status`() {
            // Setup
            stubInstitutions(listOf(chase, amex))
            every {
                institutionRepository.observeTransactionSyncState("item1")
            } returns flowOf(TransactionSyncState.Syncing)
            every {
                institutionRepository.observeTransactionSyncState("item2")
            } returns flowOf(TransactionSyncState.Failed(RuntimeException("sync error")))
            val expected = InstitutionsContent.Loaded(
                listOf(
                    chaseItem.copy(
                        syncStatus = InstitutionSyncStatus.Syncing,
                    ),
                    amexItem.copy(
                        syncStatus = InstitutionSyncStatus.Failed,
                    ),
                ),
            )

            // Test
            val viewModel = createViewModel()

            // Verify
            assertEquals(expected, viewModel.uiState.value.content)
            verify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.observeTransactionSyncState("item1")
                institutionRepository.observeTransactionSyncState("item2")
            }
        }

        @Test
        fun `updates an institution's sync status as it changes`() {
            // Setup
            val chaseSyncState = MutableStateFlow<TransactionSyncState>(TransactionSyncState.Syncing)
            stubInstitutions(listOf(chase, amex))
            every { institutionRepository.observeTransactionSyncState("item1") } returns chaseSyncState
            val viewModel = createViewModel()
            val expected = InstitutionsContent.Loaded(listOf(chaseItem, amexItem))

            // Test
            chaseSyncState.value = TransactionSyncState.Idle

            // Verify
            assertEquals(expected, viewModel.uiState.value.content)
            verify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.observeTransactionSyncState("item1")
                institutionRepository.observeTransactionSyncState("item2")
            }
        }

        @Test
        fun `shows each account's details and formatted balance`() {
            // Setup
            val account = FinancialAccount(
                accountId = "acc1",
                itemId = "item1",
                displayName = "Total Checking",
                mask = "1234",
                type = FinancialAccountType.DEPOSITORY,
                subtype = "checking",
                balanceCurrent = 1234.5,
                balanceAvailable = null,
                isoCurrencyCode = "USD",
            )
            stubInstitutions(listOf(chase.copy(accounts = listOf(account))))
            every { currencyFormatter.format(1234.5, "USD", any()) } returns "$1,234.50"
            val expectedAccount = AccountItem(
                accountId = "acc1",
                name = "Total Checking",
                details = "•••• 1234 · Depository",
                balance = "$1,234.50",
            )
            val expected = InstitutionsContent.Loaded(
                listOf(chaseItem.copy(accounts = listOf(expectedAccount))),
            )

            // Test
            val viewModel = createViewModel()

            // Verify
            assertEquals(expected, viewModel.uiState.value.content)
            verify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.observeTransactionSyncState("item1")
                currencyFormatter.format(1234.5, "USD", any())
            }
        }
    }

    @Nested
    inner class LinkInstitution {

        @Test
        fun `disables linking, then opens the link flow with the link token`() = runTest {
            // Setup
            stubInstitutions(emptyList())
            coEvery { institutionRepository.createLinkToken() } returns NetworkResult.Success("lt-abc")
            val viewModel = createViewModel()
            val expected = InstitutionsEvent.OpenLink("lt-abc")

            // Test
            viewModel.linkInstitution()
            val event = viewModel.events.first()

            // Verify
            assertEquals(expected, event)
            assertFalse(viewModel.uiState.value.isLinkEnabled)
            assertNull(viewModel.uiState.value.dialog)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.createLinkToken()
            }
        }

        @Test
        fun `stops linking with an error dialog when the link token fails`() {
            // Setup
            stubInstitutions(emptyList())
            coEvery {
                institutionRepository.createLinkToken()
            } returns NetworkResult.Failure(RuntimeException("network"))
            val viewModel = createViewModel()
            val expected = InstitutionsDialog.Error("Couldn't start linking. Please try again.")

            // Test
            viewModel.linkInstitution()

            // Verify
            assertEquals(expected, viewModel.uiState.value.dialog)
            assertTrue(viewModel.uiState.value.isLinkEnabled)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.createLinkToken()
            }
        }
    }

    @Nested
    inner class OnLinkResult {

        @Test
        fun `completes the link, then stops linking`() {
            // Setup
            stubInstitutions(emptyList())
            val gate = CompletableDeferred<NetworkResult<Unit>>()
            coEvery { institutionRepository.createLinkToken() } returns NetworkResult.Success("lt-abc")
            coEvery { institutionRepository.linkInstitution("pt", "ins_3", "Chase") } coAnswers { gate.await() }
            val viewModel = createViewModel()
            viewModel.linkInstitution()
            val result = InstitutionLinkResult.Success("pt", "ins_3", "Chase")

            // Test
            viewModel.onLinkResult(result)
            val isLinkEnabledWhileCompleting = viewModel.uiState.value.isLinkEnabled
            gate.complete(NetworkResult.Success(Unit))

            // Verify
            assertFalse(isLinkEnabledWhileCompleting)
            assertTrue(viewModel.uiState.value.isLinkEnabled)
            assertNull(viewModel.uiState.value.dialog)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.createLinkToken()
                institutionRepository.linkInstitution("pt", "ins_3", "Chase")
            }
        }

        @Test
        fun `stops linking with an error dialog when linking fails`() {
            // Setup
            stubInstitutions(emptyList())
            coEvery {
                institutionRepository.linkInstitution("pt", "ins_3", "Chase")
            } returns NetworkResult.Failure(RuntimeException("exchange failed"))
            val viewModel = createViewModel()
            val result = InstitutionLinkResult.Success("pt", "ins_3", "Chase")
            val expected = InstitutionsDialog.Error("Couldn't link the institution. Please try again.")

            // Test
            viewModel.onLinkResult(result)

            // Verify
            assertTrue(viewModel.uiState.value.isLinkEnabled)
            assertEquals(expected, viewModel.uiState.value.dialog)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.linkInstitution("pt", "ins_3", "Chase")
            }
        }

        @Test
        fun `stops linking without a dialog when cancelled`() {
            // Setup
            stubInstitutions(emptyList())
            coEvery { institutionRepository.createLinkToken() } returns NetworkResult.Success("lt-abc")
            val viewModel = createViewModel()
            viewModel.linkInstitution()

            // Test
            viewModel.onLinkResult(InstitutionLinkResult.Cancelled)

            // Verify
            assertTrue(viewModel.uiState.value.isLinkEnabled)
            assertNull(viewModel.uiState.value.dialog)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.createLinkToken()
            }
        }

        @Test
        fun `stops linking and shows the link flow's message on error`() {
            // Setup
            stubInstitutions(emptyList())
            coEvery { institutionRepository.createLinkToken() } returns NetworkResult.Success("lt-abc")
            val viewModel = createViewModel()
            viewModel.linkInstitution()
            val expected = InstitutionsDialog.Error("The bank is unavailable.")

            // Test
            viewModel.onLinkResult(InstitutionLinkResult.Error("The bank is unavailable."))

            // Verify
            assertTrue(viewModel.uiState.value.isLinkEnabled)
            assertEquals(expected, viewModel.uiState.value.dialog)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.createLinkToken()
            }
        }
    }

    @Nested
    inner class Refresh {

        @Test
        fun `refreshes then syncs every institution`() {
            // Setup
            stubInstitutions(listOf(chase, amex))
            val refreshGate = CompletableDeferred<NetworkResult<Unit>>()
            coEvery { institutionRepository.refreshInstitutions() } coAnswers { refreshGate.await() }
            val syncedItemIds = mutableListOf<String>()
            coEvery { institutionRepository.syncTransactions(any()) } coAnswers {
                syncedItemIds += firstArg<String>()
                NetworkResult.Success(Unit)
            }
            val viewModel = createViewModel()

            // Test
            viewModel.refresh()
            val isRefreshingWhileRefreshing = viewModel.uiState.value.isRefreshing
            val syncedWhileRefreshing = syncedItemIds.toList()
            refreshGate.complete(NetworkResult.Success(Unit))

            // Verify
            assertTrue(isRefreshingWhileRefreshing)
            assertTrue(syncedWhileRefreshing.isEmpty())
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertNull(viewModel.uiState.value.dialog)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.observeTransactionSyncState("item1")
                institutionRepository.observeTransactionSyncState("item2")
                institutionRepository.refreshInstitutions()
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.syncTransactions("item1")
                institutionRepository.syncTransactions("item2")
            }
        }

        @Test
        fun `stops with an error dialog and doesn't sync when the refresh fails`() {
            // Setup
            stubInstitutions(listOf(chase))
            coEvery {
                institutionRepository.refreshInstitutions()
            } returns NetworkResult.Failure(RuntimeException("network"))
            val viewModel = createViewModel()
            val expected = InstitutionsDialog.Error("Couldn't refresh your institutions.")

            // Test
            viewModel.refresh()

            // Verify
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals(expected, viewModel.uiState.value.dialog)
            coVerify(exactly = 0) { institutionRepository.syncTransactions(any()) }
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.observeTransactionSyncState("item1")
                institutionRepository.refreshInstitutions()
            }
        }

        @Test
        fun `shows one error dialog and still syncs the others when a sync fails`() {
            // Setup
            stubInstitutions(listOf(chase, amex))
            coEvery { institutionRepository.refreshInstitutions() } returns NetworkResult.Success(Unit)
            coEvery {
                institutionRepository.syncTransactions("item1")
            } returns NetworkResult.Failure(RuntimeException("sync error"))
            coEvery { institutionRepository.syncTransactions("item2") } returns NetworkResult.Success(Unit)
            val viewModel = createViewModel()
            val expected = InstitutionsDialog.Error("Couldn't sync transactions for some institutions.")

            // Test
            viewModel.refresh()

            // Verify
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals(expected, viewModel.uiState.value.dialog)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.observeTransactionSyncState("item1")
                institutionRepository.observeTransactionSyncState("item2")
                institutionRepository.refreshInstitutions()
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.syncTransactions("item1")
                institutionRepository.syncTransactions("item2")
            }
        }
    }

    @Nested
    inner class RequestDisconnect {

        @Test
        fun `asks to confirm disconnecting the institution`() {
            // Setup
            stubInstitutions(emptyList())
            val viewModel = createViewModel()
            val expected = InstitutionsDialog.ConfirmDisconnect(chase.institution)

            // Test
            viewModel.requestDisconnect(chase.institution)

            // Verify
            assertEquals(expected, viewModel.uiState.value.dialog)
            verify { institutionRepository.observeInstitutionsWithAccounts() }
        }
    }

    @Nested
    inner class ConfirmDisconnect {

        @Test
        fun `disconnects the institution awaiting confirmation`() {
            // Setup
            stubInstitutions(emptyList())
            coEvery { institutionRepository.disconnectInstitution("item1") } returns NetworkResult.Success(Unit)
            val viewModel = createViewModel()
            viewModel.requestDisconnect(chase.institution)

            // Test
            viewModel.confirmDisconnect()

            // Verify
            assertNull(viewModel.uiState.value.dialog)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.disconnectInstitution("item1")
            }
        }

        @Test
        fun `shows an error dialog when disconnecting fails`() {
            // Setup
            stubInstitutions(emptyList())
            coEvery {
                institutionRepository.disconnectInstitution("item1")
            } returns NetworkResult.Failure(RuntimeException("network"))
            val viewModel = createViewModel()
            viewModel.requestDisconnect(chase.institution)
            val expected = InstitutionsDialog.Error("Couldn't disconnect Chase. Please try again.")

            // Test
            viewModel.confirmDisconnect()

            // Verify
            assertEquals(expected, viewModel.uiState.value.dialog)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.disconnectInstitution("item1")
            }
        }

        @Test
        fun `does nothing when no disconnect is awaiting confirmation`() {
            // Setup
            stubInstitutions(emptyList())
            val viewModel = createViewModel()

            // Test
            viewModel.confirmDisconnect()

            // Verify
            assertNull(viewModel.uiState.value.dialog)
            coVerify(exactly = 0) { institutionRepository.disconnectInstitution(any()) }
            verify { institutionRepository.observeInstitutionsWithAccounts() }
        }
    }

    @Nested
    inner class DismissDialog {

        @Test
        fun `closes the disconnect confirmation without disconnecting`() {
            // Setup
            stubInstitutions(emptyList())
            val viewModel = createViewModel()
            viewModel.requestDisconnect(chase.institution)

            // Test
            viewModel.dismissDialog()

            // Verify
            assertNull(viewModel.uiState.value.dialog)
            coVerify(exactly = 0) { institutionRepository.disconnectInstitution(any()) }
            verify { institutionRepository.observeInstitutionsWithAccounts() }
        }

        @Test
        fun `closes the error dialog`() {
            // Setup
            stubInstitutions(emptyList())
            val viewModel = createViewModel()
            viewModel.onLinkResult(InstitutionLinkResult.Error("The bank is unavailable."))

            // Test
            viewModel.dismissDialog()

            // Verify
            assertNull(viewModel.uiState.value.dialog)
            verify { institutionRepository.observeInstitutionsWithAccounts() }
        }
    }

    private fun stubInstitutions(
        institutions: List<LinkedInstitutionWithAccounts>,
    ) {
        every { institutionRepository.observeInstitutionsWithAccounts() } returns flowOf(institutions)
        stubSyncStates()
    }

    private fun stubSyncStates() {
        every {
            institutionRepository.observeTransactionSyncState(any())
        } returns flowOf(TransactionSyncState.Idle)
    }

    private fun createViewModel() =
        InstitutionsViewModel(
            institutionRepository = institutionRepository,
            currencyFormatter = currencyFormatter,
        )

    private fun institutionWithAccounts(
        itemId: String,
        name: String,
    ): LinkedInstitutionWithAccounts =
        LinkedInstitutionWithAccounts(
            institution = LinkedInstitution(itemId, "ins_$itemId", name),
            accounts = emptyList(),
        )
}
