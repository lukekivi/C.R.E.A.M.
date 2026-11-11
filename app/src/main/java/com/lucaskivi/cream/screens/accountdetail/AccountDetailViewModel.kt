package com.lucaskivi.cream.screens.accountdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.Transaction
import com.lucaskivi.cream.core.institution.model.TransactionSyncState
import com.lucaskivi.cream.data.di.AppContainer
import com.lucaskivi.cream.data.repository.institution.InstitutionRepository
import com.lucaskivi.cream.screens.accountdetail.model.TransactionDateGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Account detail screen.
 *
 * Shows one account's balance and its cached transactions grouped by date, reflects its
 * institution's transaction sync state, and refreshes the balance and transactions on demand.
 *
 * @param accountId The account to show.
 * @param institutionRepository Source of the account, its transactions, and their refresh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailViewModel(
    private val accountId: String,
    private val institutionRepository: InstitutionRepository,
) : ViewModel() {

    /**
     * Mutable backing flow for [uiState].
     */
    private val mutableUiState = MutableStateFlow(AccountDetailUiState())

    /**
     * Observable UI state of the Account detail screen.
     */
    val uiState: StateFlow<AccountDetailUiState> = mutableUiState.asStateFlow()

    /**
     * Whether the current sync failure is already accounted for, so the inline error stays
     * hidden. Pull to refresh sets it: starting one dismisses the inline error, and a failure
     * of its own sync is reported by its error message instead. A refresh that succeeds, or a
     * sync that starts outside a refresh, clears it, so a later background failure shows inline
     * again.
     */
    private val isSyncFailureAcknowledged = MutableStateFlow(false)

    init {
        val accountUpdates = institutionRepository.observeAccount(accountId)
        // A refresh's sync may join a sync that was already running, so its Syncing and
        // Failed emissions can't be told apart from a background run's. Only emissions
        // outside a refresh clear the acknowledgement.
        val syncStateUpdates = accountUpdates
            .map { account -> account?.itemId }
            .distinctUntilChanged()
            .flatMapLatest { itemId ->
                itemId?.let(institutionRepository::observeTransactionSyncState)
                    ?: flowOf(TransactionSyncState.Idle)
            }
            .onEach { state ->
                if (state !is TransactionSyncState.Failed && !mutableUiState.value.isRefreshing) {
                    isSyncFailureAcknowledged.value = false
                }
            }
        val transactionGroupUpdates = institutionRepository
            .observeTransactions(accountId)
            .map { transactions -> transactions.groupByDate() }
        val uiStateUpdates = combine(
            accountUpdates,
            transactionGroupUpdates,
            syncStateUpdates,
            isSyncFailureAcknowledged,
        ) { account, transactionGroups, state, isAcknowledged ->
            mutableUiState.update {
                it.copy(
                    isLoading = false,
                    account = account,
                    isAccountMissing = account == null,
                    transactionGroups = transactionGroups,
                    isSyncingTransactions = state is TransactionSyncState.Syncing,
                    isTransactionSyncFailed = state is TransactionSyncState.Failed && !isAcknowledged,
                )
            }
        }
        uiStateUpdates.launchIn(viewModelScope)
    }

    /**
     * Refreshes the account's balance, then syncs its institution's transactions, showing an
     * error if either fails. Skips the sync when the balance refresh fails. Does nothing until
     * the account has loaded.
     */
    fun refresh() {
        val itemId = uiState.value.account?.itemId
        if (itemId != null) {
            mutableUiState.update { it.copy(isRefreshing = true) }
            isSyncFailureAcknowledged.value = true
            viewModelScope.launch {
                val error = when (institutionRepository.refreshInstitutions()) {
                    is NetworkResult.Success -> syncTransactions(itemId)
                    is NetworkResult.Failure -> "Couldn't refresh your balance."
                }
                // A clean refresh leaves nothing to acknowledge, so the next background failure
                // shows inline even if its Syncing emission is never seen.
                if (error == null) {
                    isSyncFailureAcknowledged.value = false
                }
                mutableUiState.update { it.copy(isRefreshing = false, error = error ?: it.error) }
            }
        }
    }

    /**
     * Clears any error currently displayed in [uiState].
     */
    fun clearError() =
        mutableUiState.update { it.copy(error = null) }

    /**
     * Syncs the transactions of the institution identified by [itemId].
     *
     * @param itemId Identifier of the institution to sync.
     * @return An error message if the sync failed, or null if it succeeded.
     */
    private suspend fun syncTransactions(
        itemId: String,
    ): String? =
        when (institutionRepository.syncTransactions(itemId)) {
            is NetworkResult.Success -> null
            is NetworkResult.Failure -> "Couldn't sync transactions."
        }

    /**
     * Groups the transactions by date, keeping their order, so newest-first transactions give
     * newest-first groups.
     */
    private fun List<Transaction>.groupByDate(): List<TransactionDateGroup> =
        this
            .groupBy { transaction -> transaction.date }
            .map { (date, transactions) -> TransactionDateGroup(date, transactions) }

    /**
     * Factory entry points for [AccountDetailViewModel].
     */
    companion object {
        /**
         * Returns a [ViewModelProvider.Factory] that constructs [AccountDetailViewModel] for
         * [accountId] from [appContainer].
         *
         * @param appContainer Source of the dependencies the ViewModel requires.
         * @param accountId The account the ViewModel shows.
         */
        fun factory(
            appContainer: AppContainer,
            accountId: String,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
            ): T =
                AccountDetailViewModel(
                    accountId = accountId,
                    institutionRepository = appContainer.institutionRepository,
                ) as T
        }
    }
}

/**
 * UI state for the Account detail screen.
 *
 * @property isLoading Whether the account hasn't loaded yet.
 * @property account The account, or null while loading or once it no longer exists.
 * @property isAccountMissing Whether the account no longer exists, so the screen should close.
 * @property transactionGroups Every cached transaction of the account, grouped by date, newest
 *   first.
 * @property isSyncingTransactions Whether the account's transactions are being synced.
 * @property isTransactionSyncFailed Whether a background transaction sync failed and hasn't
 *   been dismissed by a pull to refresh.
 * @property isRefreshing Whether a pull to refresh is in progress.
 * @property error Human-readable error message to show once, or null.
 */
data class AccountDetailUiState(
    val isLoading: Boolean = true,
    val account: FinancialAccount? = null,
    val isAccountMissing: Boolean = false,
    val transactionGroups: List<TransactionDateGroup> = emptyList(),
    val isSyncingTransactions: Boolean = false,
    val isTransactionSyncFailed: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)
