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
            .onEach { institutions -> mutableUiState.update { it.copy(isLoading = false, institutions = institutions) } }
            .launchIn(viewModelScope)
    }

    /**
     * Starts linking a new institution by requesting a link token. On success the token is
     * exposed as `pendingLinkToken` for the screen to launch the link flow with; on failure an
     * error is shown. Linking stays in progress until [onLinkResult] handles the outcome.
     */
    fun linkInstitution() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLinking = true) }
            institutionRepository
                .createLinkToken()
                .onSuccess { linkToken -> mutableUiState.update { it.copy(pendingLinkToken = linkToken) } }
                .onFailure { _ ->
                    mutableUiState.update {
                        it.copy(isLinking = false, error = "Couldn't start linking. Please try again.")
                    }
                }
        }
    }

    /**
     * Acknowledges that the screen launched the link flow with `pendingLinkToken`, so it isn't
     * launched again.
     */
    fun onLinkLaunched() =
        mutableUiState.update { it.copy(pendingLinkToken = null) }

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
            InstitutionLinkResult.Cancelled -> mutableUiState.update { it.copy(isLinking = false) }
            is InstitutionLinkResult.Error -> mutableUiState.update {
                it.copy(isLinking = false, error = result.message)
            }
        }
    }

    /**
     * Refreshes every institution's accounts and balances, then syncs every institution's
     * transactions. Stops with an error if the refresh fails; otherwise shows one error if any
     * sync failed.
     */
    fun refresh() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isRefreshing = true) }
            val error = when (institutionRepository.refreshInstitutions()) {
                is NetworkResult.Success -> syncAllInstitutions()
                is NetworkResult.Failure -> "Couldn't refresh your institutions."
            }
            mutableUiState.update { it.copy(isRefreshing = false, error = error ?: it.error) }
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
        mutableUiState.update { it.copy(pendingDisconnect = institution) }

    /**
     * Cancels the pending disconnect without disconnecting anything.
     */
    fun dismissDisconnect() =
        mutableUiState.update { it.copy(pendingDisconnect = null) }

    /**
     * Disconnects the institution awaiting confirmation, showing an error if that fails.
     */
    fun confirmDisconnect() {
        val institution = uiState.value.pendingDisconnect
        mutableUiState.update { it.copy(pendingDisconnect = null) }
        if (institution != null) {
            viewModelScope.launch {
                institutionRepository
                    .disconnectInstitution(institution.itemId)
                    .onFailure { _ ->
                        mutableUiState.update {
                            it.copy(error = "Couldn't disconnect ${institution.institutionName}. Please try again.")
                        }
                    }
            }
        }
    }

    /**
     * Clears any error currently displayed in [uiState].
     */
    fun clearError() =
        mutableUiState.update { it.copy(error = null) }

    /**
     * Links the institution the user picked in the link flow, ending linking either way and
     * showing an error if it fails.
     *
     * @param result The successful outcome of the link flow.
     */
    private fun completeLink(
        result: InstitutionLinkResult.Success,
    ) {
        viewModelScope.launch {
            institutionRepository
                .linkInstitution(
                    publicToken = result.publicToken,
                    institutionId = result.institutionId,
                    institutionName = result.institutionName,
                )
                .onSuccess { mutableUiState.update { it.copy(isLinking = false) } }
                .onFailure { _ ->
                    mutableUiState.update {
                        it.copy(isLinking = false, error = "Couldn't link the institution. Please try again.")
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
        ) = object : ViewModelProvider.Factory {
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
 * @property isLoading Whether the first list of institutions hasn't loaded yet.
 * @property institutions Every linked institution with its accounts.
 * @property isRefreshing Whether a pull to refresh is in progress.
 * @property isLinking Whether linking is in progress, from requesting a link token until the
 *   link flow's outcome is handled.
 * @property pendingLinkToken Link token the screen should launch the link flow with, or null.
 * @property pendingDisconnect Institution awaiting disconnect confirmation, or null.
 * @property error Human-readable error message to show once, or null.
 */
data class InstitutionsUiState(
    val isLoading: Boolean = true,
    val institutions: List<LinkedInstitutionWithAccounts> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLinking: Boolean = false,
    val pendingLinkToken: String? = null,
    val pendingDisconnect: LinkedInstitution? = null,
    val error: String? = null,
)
