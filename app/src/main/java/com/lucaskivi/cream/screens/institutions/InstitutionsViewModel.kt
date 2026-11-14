package com.lucaskivi.cream.screens.institutions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.core.onFailure
import com.lucaskivi.cream.core.onSuccess
import com.lucaskivi.cream.data.di.AppContainer
import com.lucaskivi.cream.data.repository.institution.InstitutionRepository
import com.lucaskivi.cream.link.model.InstitutionLinkResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Institutions screen.
 *
 * Lists every linked institution with its accounts, and lets the user link a new institution,
 * refresh all of them, and disconnect one.
 *
 * @param institutionRepository Source of linked institutions and the actions on them.
 */
class InstitutionsViewModel(
    private val institutionRepository: InstitutionRepository,
) : ViewModel() {

    /**
     * Mutable backing flow for [uiState].
     */
    private val mutableUiState = MutableStateFlow(InstitutionsUiState())

    /**
     * Observable UI state of the Institutions screen.
     */
    val uiState: StateFlow<InstitutionsUiState> = mutableUiState.asStateFlow()

    init {
        institutionRepository
            .observeInstitutionsWithAccounts()
            .onEach(::handleInstitutions)
            .launchIn(viewModelScope)
    }

    /**
     * Starts linking a new institution by requesting a link token. On success the link flow is
     * [LinkFlowState.Open] with that token; on failure an error dialog is shown. Linking stays in
     * progress until [onLinkResult] handles the outcome.
     */
    fun linkInstitution() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(linkFlow = LinkFlowState.Preparing) }
            institutionRepository
                .createLinkToken()
                .onSuccess { linkToken -> mutableUiState.update { it.copy(linkFlow = LinkFlowState.Open(linkToken)) } }
                .onFailure { _ ->
                    mutableUiState.update {
                        it.copy(
                            linkFlow = LinkFlowState.Idle,
                            dialog = InstitutionsDialog.Error("Couldn't start linking. Please try again."),
                        )
                    }
                }
        }
    }

    /**
     * Handles the outcome of the link flow. A success links the institution; a cancellation
     * ends linking quietly; an error is shown as-is.
     *
     * @param result Outcome reported by the link flow.
     */
    fun onLinkResult(
        result: InstitutionLinkResult,
    ) {
        when (result) {
            is InstitutionLinkResult.Success -> completeLink(result)
            InstitutionLinkResult.Cancelled -> mutableUiState.update { it.copy(linkFlow = LinkFlowState.Idle) }
            is InstitutionLinkResult.Error -> {
                mutableUiState.update {
                    it.copy(
                        linkFlow = LinkFlowState.Idle,
                        dialog = InstitutionsDialog.Error(result.message),
                    )
                }
            }
        }
    }

    /**
     * Refreshes every institution's accounts and balances, then syncs every institution's
     * transactions. Stops with an error dialog if the refresh fails; otherwise shows one error
     * dialog if any sync failed.
     */
    fun refresh() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isRefreshing = true) }
            val error = when (institutionRepository.refreshInstitutions()) {
                is NetworkResult.Success -> syncAllInstitutions()
                is NetworkResult.Failure -> "Couldn't refresh your institutions."
            }
            mutableUiState.update {
                it.copy(
                    isRefreshing = false,
                    dialog = error?.let(InstitutionsDialog::Error) ?: it.dialog,
                )
            }
        }
    }

    /**
     * Asks the user to confirm disconnecting [institution].
     *
     * @param institution The institution to disconnect once confirmed.
     */
    fun requestDisconnect(
        institution: LinkedInstitution,
    ) =
        mutableUiState.update { it.copy(dialog = InstitutionsDialog.ConfirmDisconnect(institution)) }

    /**
     * Disconnects the institution awaiting confirmation, showing an error dialog if that fails.
     */
    fun confirmDisconnect() {
        val institution = (uiState.value.dialog as? InstitutionsDialog.ConfirmDisconnect)?.institution
        mutableUiState.update { it.copy(dialog = null) }
        if (institution != null) {
            viewModelScope.launch {
                institutionRepository
                    .disconnectInstitution(institution.itemId)
                    .onFailure { _ ->
                        val message = "Couldn't disconnect ${institution.institutionName}. Please try again."
                        mutableUiState.update { it.copy(dialog = InstitutionsDialog.Error(message)) }
                    }
            }
        }
    }

    /**
     * Closes the dialog currently shown, without acting on it.
     */
    fun dismissDialog() =
        mutableUiState.update { it.copy(dialog = null) }

    /**
     * Shows [institutions] as the screen's content: [InstitutionsContent.Empty] when there are
     * none, otherwise [InstitutionsContent.Loaded].
     *
     * @param institutions Every linked institution with its accounts.
     */
    private fun handleInstitutions(
        institutions: List<LinkedInstitutionWithAccounts>,
    ) {
        val content = if (institutions.isEmpty()) {
            InstitutionsContent.Empty
        } else {
            InstitutionsContent.Loaded(institutions)
        }
        mutableUiState.update { it.copy(content = content) }
    }

    /**
     * Links the institution the user picked in the link flow, ending linking either way and
     * showing an error dialog if it fails.
     *
     * @param result The successful outcome of the link flow.
     */
    private fun completeLink(
        result: InstitutionLinkResult.Success,
    ) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(linkFlow = LinkFlowState.Completing) }
            institutionRepository
                .linkInstitution(
                    publicToken = result.publicToken,
                    institutionId = result.institutionId,
                    institutionName = result.institutionName,
                )
                .onSuccess { mutableUiState.update { it.copy(linkFlow = LinkFlowState.Idle) } }
                .onFailure { _ ->
                    mutableUiState.update {
                        it.copy(
                            linkFlow = LinkFlowState.Idle,
                            dialog = InstitutionsDialog.Error("Couldn't link the institution. Please try again."),
                        )
                    }
                }
        }
    }

    /**
     * Syncs every linked institution's transactions in parallel.
     *
     * @return An error message if any sync failed, or null if all succeeded.
     */
    private suspend fun syncAllInstitutions(): String? {
        // Read after the refresh, so newly listed institutions are synced too.
        val itemIds = institutionRepository
            .observeInstitutionsWithAccounts()
            .first()
            .map { it.institution.itemId }
        val results = coroutineScope {
            itemIds
                .map { itemId -> async { institutionRepository.syncTransactions(itemId) } }
                .awaitAll()
        }
        return if (results.any { it is NetworkResult.Failure }) {
            "Couldn't sync transactions for some institutions."
        } else {
            null
        }
    }

    /**
     * Factory entry points for [InstitutionsViewModel].
     */
    companion object {
        /**
         * Returns a [ViewModelProvider.Factory] that constructs [InstitutionsViewModel] from
         * [appContainer].
         *
         * @param appContainer Source of the dependencies the ViewModel requires.
         */
        fun factory(
            appContainer: AppContainer,
        ) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                ): T =
                    InstitutionsViewModel(appContainer.institutionRepository) as T
            }
    }
}

/**
 * UI state for the Institutions screen.
 *
 * @property content What the screen's body shows.
 * @property isRefreshing Whether a pull to refresh is in progress.
 * @property linkFlow Where linking a new institution stands.
 * @property dialog Dialog shown over the screen, or null if none.
 */
data class InstitutionsUiState(
    val content: InstitutionsContent = InstitutionsContent.Loading,
    val isRefreshing: Boolean = false,
    val linkFlow: LinkFlowState = LinkFlowState.Idle,
    val dialog: InstitutionsDialog? = null,
)

/**
 * What the Institutions screen's body shows.
 */
sealed interface InstitutionsContent {

    /**
     * The first list of institutions hasn't loaded yet.
     */
    data object Loading : InstitutionsContent

    /**
     * No institutions are linked.
     */
    data object Empty : InstitutionsContent

    /**
     * At least one institution is linked.
     *
     * @property institutions Every linked institution with its accounts.
     */
    data class Loaded(
        val institutions: List<LinkedInstitutionWithAccounts>,
    ) : InstitutionsContent
}

/**
 * Where linking a new institution stands. Linking can start only from [Idle].
 */
sealed interface LinkFlowState {

    /**
     * Not linking; the user can start linking.
     */
    data object Idle : LinkFlowState

    /**
     * Requesting a link token.
     */
    data object Preparing : LinkFlowState

    /**
     * The link flow is open.
     *
     * @property linkToken Link token the link flow runs with.
     */
    data class Open(
        val linkToken: String,
    ) : LinkFlowState

    /**
     * The user picked an institution and it is being linked.
     */
    data object Completing : LinkFlowState
}

/**
 * Dialog shown over the Institutions screen.
 */
sealed interface InstitutionsDialog {

    /**
     * Asks the user to confirm disconnecting [institution].
     *
     * @property institution The institution to disconnect once confirmed.
     */
    data class ConfirmDisconnect(
        val institution: LinkedInstitution,
    ) : InstitutionsDialog

    /**
     * Reports a failure until the user dismisses it.
     *
     * @property message Human-readable error message.
     */
    data class Error(
        val message: String,
    ) : InstitutionsDialog
}
