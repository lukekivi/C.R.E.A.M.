package com.lucaskivi.cream.screens.institutions

import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.data.repository.institution.InstitutionRepository
import com.lucaskivi.cream.link.model.InstitutionLinkResult
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
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class InstitutionsViewModelTest {

    private val institutionRepository: InstitutionRepository = mockk()
    private val chase = institutionWithAccounts("item1", "Chase")
    private val amex = institutionWithAccounts("item2", "Amex")

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
        fun `lists every linked institution`() {
            // Setup
            stubInstitutions(listOf(chase, amex))

            // Test
            val viewModel = createViewModel()

            // Verify
            assertEquals(listOf(chase, amex), viewModel.uiState.value.institutions)
            assertFalse(viewModel.uiState.value.isLoading)
            verify { institutionRepository.observeInstitutionsWithAccounts() }
        }

        @Test
        fun `clears loading when no institutions are linked`() {
            // Setup
            stubInstitutions(emptyList())

            // Test
            val viewModel = createViewModel()

            // Verify
            assertFalse(viewModel.uiState.value.isLoading)
            assertTrue(viewModel.uiState.value.institutions.isEmpty())
            verify { institutionRepository.observeInstitutionsWithAccounts() }
        }

        @Test
        fun `lists an institution added to the list`() {
            // Setup
            val institutions = MutableStateFlow(listOf(chase))
            every { institutionRepository.observeInstitutionsWithAccounts() } returns institutions
            val viewModel = createViewModel()

            // Test
            institutions.value = listOf(chase, amex)

            // Verify
            assertEquals(listOf(chase, amex), viewModel.uiState.value.institutions)
            verify { institutionRepository.observeInstitutionsWithAccounts() }
        }
    }

    @Nested
    inner class LinkInstitution {

        @Test
        fun `exposes the link token while linking`() {
            // Setup
            stubInstitutions(emptyList())
            coEvery { institutionRepository.createLinkToken() } returns NetworkResult.Success("lt-abc")
            val viewModel = createViewModel()

            // Test
            viewModel.linkInstitution()

            // Verify
            assertEquals("lt-abc", viewModel.uiState.value.pendingLinkToken)
            assertTrue(viewModel.uiState.value.isLinking)
            assertNull(viewModel.uiState.value.error)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.createLinkToken()
            }
        }

        @Test
        fun `stops linking with an error when the link token fails`() {
            // Setup
            stubInstitutions(emptyList())
            coEvery {
                institutionRepository.createLinkToken()
            } returns NetworkResult.Failure(RuntimeException("network"))
            val viewModel = createViewModel()

            // Test
            viewModel.linkInstitution()

            // Verify
            assertEquals("Couldn't start linking. Please try again.", viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLinking)
            assertNull(viewModel.uiState.value.pendingLinkToken)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.createLinkToken()
            }
        }
    }

    @Nested
    inner class OnLinkLaunched {

        @Test
        fun `clears the link token and keeps linking`() {
            // Setup
            stubInstitutions(emptyList())
            coEvery { institutionRepository.createLinkToken() } returns NetworkResult.Success("lt-abc")
            val viewModel = createViewModel()
            viewModel.linkInstitution()

            // Test
            viewModel.onLinkLaunched()

            // Verify
            assertNull(viewModel.uiState.value.pendingLinkToken)
            assertTrue(viewModel.uiState.value.isLinking)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.createLinkToken()
            }
        }
    }

    @Nested
    inner class OnLinkResult {

        @Test
        fun `links the institution and stays linking until it finishes`() {
            // Setup
            stubInstitutions(emptyList())
            val gate = CompletableDeferred<NetworkResult<Unit>>()
            coEvery { institutionRepository.createLinkToken() } returns NetworkResult.Success("lt-abc")
            coEvery { institutionRepository.linkInstitution("pt", "ins_3", "Chase") } coAnswers { gate.await() }
            val viewModel = createViewModel()
            viewModel.linkInstitution()
            viewModel.onLinkLaunched()
            val result = InstitutionLinkResult.Success("pt", "ins_3", "Chase")

            // Test
            viewModel.onLinkResult(result)
            val isLinkingWhileLinking = viewModel.uiState.value.isLinking
            gate.complete(NetworkResult.Success(Unit))

            // Verify
            assertTrue(isLinkingWhileLinking)
            assertFalse(viewModel.uiState.value.isLinking)
            assertNull(viewModel.uiState.value.error)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.createLinkToken()
                institutionRepository.linkInstitution("pt", "ins_3", "Chase")
            }
        }

        @Test
        fun `stops linking with an error when linking fails`() {
            // Setup
            stubInstitutions(emptyList())
            coEvery {
                institutionRepository.linkInstitution("pt", "ins_3", "Chase")
            } returns NetworkResult.Failure(RuntimeException("exchange failed"))
            val viewModel = createViewModel()
            val result = InstitutionLinkResult.Success("pt", "ins_3", "Chase")

            // Test
            viewModel.onLinkResult(result)

            // Verify
            assertFalse(viewModel.uiState.value.isLinking)
            assertEquals("Couldn't link the institution. Please try again.", viewModel.uiState.value.error)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.linkInstitution("pt", "ins_3", "Chase")
            }
        }

        @Test
        fun `stops linking without an error when cancelled`() {
            // Setup
            stubInstitutions(emptyList())
            coEvery { institutionRepository.createLinkToken() } returns NetworkResult.Success("lt-abc")
            val viewModel = createViewModel()
            viewModel.linkInstitution()
            viewModel.onLinkLaunched()

            // Test
            viewModel.onLinkResult(InstitutionLinkResult.Cancelled)

            // Verify
            assertFalse(viewModel.uiState.value.isLinking)
            assertNull(viewModel.uiState.value.error)
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
            viewModel.onLinkLaunched()

            // Test
            viewModel.onLinkResult(InstitutionLinkResult.Error("The bank is unavailable."))

            // Verify
            assertFalse(viewModel.uiState.value.isLinking)
            assertEquals("The bank is unavailable.", viewModel.uiState.value.error)
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
            coEvery { institutionRepository.syncTransactions(any()) } returns NetworkResult.Success(Unit)
            val viewModel = createViewModel()

            // Test
            viewModel.refresh()
            val isRefreshingWhileRefreshing = viewModel.uiState.value.isRefreshing
            coVerify(exactly = 0) { institutionRepository.syncTransactions(any()) }
            refreshGate.complete(NetworkResult.Success(Unit))

            // Verify
            assertTrue(isRefreshingWhileRefreshing)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertNull(viewModel.uiState.value.error)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.refreshInstitutions()
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.syncTransactions("item1")
                institutionRepository.syncTransactions("item2")
            }
        }

        @Test
        fun `stops with an error and doesn't sync when the refresh fails`() {
            // Setup
            stubInstitutions(listOf(chase))
            coEvery {
                institutionRepository.refreshInstitutions()
            } returns NetworkResult.Failure(RuntimeException("network"))
            val viewModel = createViewModel()

            // Test
            viewModel.refresh()

            // Verify
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals("Couldn't refresh your institutions.", viewModel.uiState.value.error)
            coVerify(exactly = 0) { institutionRepository.syncTransactions(any()) }
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.refreshInstitutions()
            }
        }

        @Test
        fun `shows one error and still syncs the others when a sync fails`() {
            // Setup
            stubInstitutions(listOf(chase, amex))
            coEvery { institutionRepository.refreshInstitutions() } returns NetworkResult.Success(Unit)
            coEvery {
                institutionRepository.syncTransactions("item1")
            } returns NetworkResult.Failure(RuntimeException("sync error"))
            coEvery { institutionRepository.syncTransactions("item2") } returns NetworkResult.Success(Unit)
            val viewModel = createViewModel()

            // Test
            viewModel.refresh()

            // Verify
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals("Couldn't sync transactions for some institutions.", viewModel.uiState.value.error)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
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

            // Test
            viewModel.requestDisconnect(chase.institution)

            // Verify
            assertEquals(chase.institution, viewModel.uiState.value.pendingDisconnect)
            verify { institutionRepository.observeInstitutionsWithAccounts() }
        }
    }

    @Nested
    inner class DismissDisconnect {

        @Test
        fun `clears the pending disconnect without disconnecting`() {
            // Setup
            stubInstitutions(emptyList())
            val viewModel = createViewModel()
            viewModel.requestDisconnect(chase.institution)

            // Test
            viewModel.dismissDisconnect()

            // Verify
            assertNull(viewModel.uiState.value.pendingDisconnect)
            coVerify(exactly = 0) { institutionRepository.disconnectInstitution(any()) }
            verify { institutionRepository.observeInstitutionsWithAccounts() }
        }
    }

    @Nested
    inner class ConfirmDisconnect {

        @Test
        fun `disconnects the pending institution`() {
            // Setup
            stubInstitutions(emptyList())
            coEvery { institutionRepository.disconnectInstitution("item1") } returns NetworkResult.Success(Unit)
            val viewModel = createViewModel()
            viewModel.requestDisconnect(chase.institution)

            // Test
            viewModel.confirmDisconnect()

            // Verify
            assertNull(viewModel.uiState.value.pendingDisconnect)
            assertNull(viewModel.uiState.value.error)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.disconnectInstitution("item1")
            }
        }

        @Test
        fun `shows an error when disconnecting fails`() {
            // Setup
            stubInstitutions(emptyList())
            coEvery {
                institutionRepository.disconnectInstitution("item1")
            } returns NetworkResult.Failure(RuntimeException("network"))
            val viewModel = createViewModel()
            viewModel.requestDisconnect(chase.institution)

            // Test
            viewModel.confirmDisconnect()

            // Verify
            assertEquals("Couldn't disconnect Chase. Please try again.", viewModel.uiState.value.error)
            coVerify {
                institutionRepository.observeInstitutionsWithAccounts()
                institutionRepository.disconnectInstitution("item1")
            }
        }

        @Test
        fun `does nothing when no disconnect is pending`() {
            // Setup
            stubInstitutions(emptyList())
            val viewModel = createViewModel()

            // Test
            viewModel.confirmDisconnect()

            // Verify
            assertNull(viewModel.uiState.value.error)
            coVerify(exactly = 0) { institutionRepository.disconnectInstitution(any()) }
            verify { institutionRepository.observeInstitutionsWithAccounts() }
        }
    }

    @Nested
    inner class ClearError {

        @Test
        fun `clears the error`() {
            // Setup
            stubInstitutions(emptyList())
            val viewModel = createViewModel()
            viewModel.onLinkResult(InstitutionLinkResult.Error("The bank is unavailable."))

            // Test
            viewModel.clearError()

            // Verify
            assertNull(viewModel.uiState.value.error)
            verify { institutionRepository.observeInstitutionsWithAccounts() }
        }
    }

    private fun stubInstitutions(
        institutions: List<LinkedInstitutionWithAccounts>,
    ) {
        every { institutionRepository.observeInstitutionsWithAccounts() } returns flowOf(institutions)
    }

    private fun createViewModel() =
        InstitutionsViewModel(institutionRepository = institutionRepository)

    private fun institutionWithAccounts(
        itemId: String,
        name: String,
    ): LinkedInstitutionWithAccounts =
        LinkedInstitutionWithAccounts(
            institution = LinkedInstitution(itemId, "ins_$itemId", name),
            accounts = emptyList(),
        )
}
