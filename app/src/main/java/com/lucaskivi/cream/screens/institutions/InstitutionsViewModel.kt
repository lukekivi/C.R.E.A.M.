package com.lucaskivi.cream.screens.institutions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.core.onFailure
import com.lucaskivi.cream.core.onSuccess
import com.lucaskivi.cream.data.di.AppContainer
import com.lucaskivi.cream.data.repository.institution.InstitutionRepository
import com.lucaskivi.cream.link.model.InstitutionLinkResult
import com.lucaskivi.cream.ui.format.CurrencyFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Institutions screen.
 *
 * Lists every linked institution with its accounts, and lets the user link a new institution,
 * refresh all of them, and disconnect one.
 *
 * @param institutionRepository Source of linked institutions and the actions on them.
 * @param currencyFormatter Formats account balances for display.
 */
class InstitutionsViewModel(
    private val institutionRepository: InstitutionRepository,
    private val currencyFormatter: CurrencyFormatter,
) : ViewModel() {

    /**
     * Mutable backing flow for [uiState].
     */
    private val mutableUiState = MutableStateFlow(InstitutionsUiState())

    /**
     * Observable UI state of the Institutions screen.
     */
    val uiState: StateFlow<InstitutionsUiState> = mutableUiState.asStateFlow()

    /**
     * Backing channel for [events].
     */
    private val eventChannel = Channel<InstitutionsEvent>(Channel.BUFFERED)

    /**
     * One-time events for the Institutions screen to act on.
     */
    val events: Flow<InstitutionsEvent> = eventChannel.receiveAsFlow()

    init {
        institutionRepository
            .observeInstitutionsWithAccounts()
            .onEach(::handleInstitutions)
            .launchIn(viewModelScope)
    }

    /**
     * Starts linking a new institution by requesting a link token, then opening the link flow
     * with it. Shows an error dialog if the token can't be fetched. Linking stays in progress
     * until [onLinkResult] handles the outcome.
     */
    fun linkInstitution() {
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isLinkEnabled = false,
                )
            }
            institutionRepository
                .createLinkToken()
                .onSuccess { linkToken -> handleLinkToken(linkToken) }
                .onFailure { _ -> stopLinking(START_LINK_FAILED_MESSAGE) }
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
            InstitutionLinkResult.Cancelled -> stopLinking(errorMessage = null)
            is InstitutionLinkResult.Error -> stopLinking(result.message)
        }
    }

    /**
     * Refreshes every institution's accounts and balances, then syncs every institution's
     * transactions. Stops with an error dialog if the refresh fails; otherwise shows one error
     * dialog if any sync failed.
     */
    fun refresh() {
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isRefreshing = true,
                )
            }
            val errorMessage = when (institutionRepository.refreshInstitutions()) {
                is NetworkResult.Success -> syncAllInstitutions()
                is NetworkResult.Failure -> REFRESH_FAILED_MESSAGE
            }
            mutableUiState.update {
                val dialog = errorMessage?.let(InstitutionsDialog::Error) ?: it.dialog
                it.copy(
                    isRefreshing = false,
                    dialog = dialog,
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
    ) {
        val dialog = InstitutionsDialog.ConfirmDisconnect(institution)
        mutableUiState.update {
            it.copy(
                dialog = dialog,
            )
        }
    }

    /**
     * Disconnects the institution awaiting confirmation, showing an error dialog if that fails.
     */
    fun confirmDisconnect() {
        val institution = (uiState.value.dialog as? InstitutionsDialog.ConfirmDisconnect)?.institution
        dismissDialog()
        if (institution != null) {
            viewModelScope.launch {
                institutionRepository
                    .disconnectInstitution(institution.itemId)
                    .onFailure { _ -> handleDisconnectFailure(institution) }
            }
        }
    }

    /**
     * Closes the dialog currently shown, without acting on it.
     */
    fun dismissDialog() =
        mutableUiState.update {
            it.copy(
                dialog = null,
            )
        }

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
            InstitutionsContent.Loaded(institutions.map(::toInstitutionItem))
        }
        mutableUiState.update {
            it.copy(
                content = content,
            )
        }
    }

    /**
     * Tells the screen to open the link flow with [linkToken].
     *
     * @param linkToken Link token the link flow runs with.
     */
    private suspend fun handleLinkToken(
        linkToken: String,
    ) {
        val event = InstitutionsEvent.OpenLink(linkToken)
        eventChannel.send(event)
    }

    /**
     * Ends linking, showing [errorMessage] in an error dialog if present.
     *
     * @param errorMessage Why linking failed, or null if it didn't.
     */
    private fun stopLinking(
        errorMessage: String?,
    ) =
        mutableUiState.update {
            val dialog = errorMessage?.let(InstitutionsDialog::Error) ?: it.dialog
            it.copy(
                isLinkEnabled = true,
                dialog = dialog,
            )
        }

    /**
     * Shows an error dialog saying [institution] couldn't be disconnected.
     *
     * @param institution The institution that failed to disconnect.
     */
    private fun handleDisconnectFailure(
        institution: LinkedInstitution,
    ) {
        val message = "Couldn't disconnect ${institution.institutionName}. Please try again."
        val dialog = InstitutionsDialog.Error(message)
        mutableUiState.update {
            it.copy(
                dialog = dialog,
            )
        }
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
            institutionRepository
                .linkInstitution(
                    publicToken = result.publicToken,
                    institutionId = result.institutionId,
                    institutionName = result.institutionName,
                )
                .onSuccess { stopLinking(errorMessage = null) }
                .onFailure { _ -> stopLinking(LINK_FAILED_MESSAGE) }
        }
    }

    /**
     * Returns [institutionWithAccounts] as it's shown on screen.
     *
     * @param institutionWithAccounts A linked institution with its accounts.
     */
    private fun toInstitutionItem(
        institutionWithAccounts: LinkedInstitutionWithAccounts,
    ): InstitutionItem {
        val accounts = institutionWithAccounts.accounts.map(::toAccountItem)
        return InstitutionItem(
            institution = institutionWithAccounts.institution,
            accounts = accounts,
        )
    }

    /**
     * Returns [account] as it's shown on screen.
     *
     * @param account The account to show.
     */
    private fun toAccountItem(
        account: FinancialAccount,
    ): AccountItem {
        val type = account.type.name
            .lowercase()
            .replaceFirstChar { it.titlecase() }
        val details = listOfNotNull(
            account.mask?.let { "•••• $it" },
            type,
        ).joinToString(" · ")
        val balance = currencyFormatter.format(
            account.balanceCurrent,
            account.isoCurrencyCode,
        )
        return AccountItem(
            accountId = account.accountId,
            name = account.displayName,
            details = details,
            balance = balance,
        )
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
            SYNC_FAILED_MESSAGE
        } else {
            null
        }
    }

    companion object {
        /**
         * Message shown when a link token can't be fetched.
         */
        private const val START_LINK_FAILED_MESSAGE = "Couldn't start linking. Please try again."

        /**
         * Message shown when the picked institution can't be linked.
         */
        private const val LINK_FAILED_MESSAGE = "Couldn't link the institution. Please try again."

        /**
         * Message shown when refreshing the institutions fails.
         */
        private const val REFRESH_FAILED_MESSAGE = "Couldn't refresh your institutions."

        /**
         * Message shown when syncing any institution's transactions fails.
         */
        private const val SYNC_FAILED_MESSAGE = "Couldn't sync transactions for some institutions."

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
                    InstitutionsViewModel(
                        institutionRepository = appContainer.institutionRepository,
                        currencyFormatter = CurrencyFormatter(),
                    ) as T
            }
    }
}

/**
 * UI state for the Institutions screen.
 *
 * @property content What the screen's body shows.
 * @property isRefreshing Whether a pull to refresh is in progress.
 * @property isLinkEnabled Whether the user can start linking a new institution; false while one
 * is being linked.
 * @property dialog Dialog shown over the screen, or null if none.
 */
data class InstitutionsUiState(
    val content: InstitutionsContent = InstitutionsContent.Loading,
    val isRefreshing: Boolean = false,
    val isLinkEnabled: Boolean = true,
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
        val institutions: List<InstitutionItem>,
    ) : InstitutionsContent
}

/**
 * A linked institution as the Institutions screen shows it.
 *
 * @property institution The linked institution.
 * @property accounts The institution's accounts.
 */
data class InstitutionItem(
    val institution: LinkedInstitution,
    val accounts: List<AccountItem>,
)

/**
 * An account as the Institutions screen shows it.
 *
 * @property accountId Stable identifier of the account.
 * @property name Account name (e.g. "Total Checking").
 * @property details Masked number and type (e.g. "•••• 1234 · Depository").
 * @property balance Current balance in the account's currency, or "—" if unknown.
 */
data class AccountItem(
    val accountId: String,
    val name: String,
    val details: String,
    val balance: String,
)

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

/**
 * One-time events the Institutions screen acts on.
 */
sealed interface InstitutionsEvent {

    /**
     * Open the link flow.
     *
     * @property linkToken Link token the link flow runs with.
     */
    data class OpenLink(
        val linkToken: String,
    ) : InstitutionsEvent
}
