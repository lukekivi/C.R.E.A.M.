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
import com.lucaskivi.cream.ui.format.CurrencyFormatter
import com.lucaskivi.cream.ui.format.DateFormatter
import com.lucaskivi.cream.ui.format.formattedDetails
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * ViewModel for the Account detail screen.
 *
 * Shows one account's balance and its cached transactions grouped by date, reflects its
 * institution's transaction sync state, and refreshes the balance and transactions on demand.
 * Tells the screen to close if the account stops existing.
 *
 * @param accountId The account to show.
 * @param institutionRepository Source of the account, its transactions, and their refresh.
 * @param currencyFormatter Formats the balance and transaction amounts for display.
 * @param dateFormatter Formats transaction dates for display.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailViewModel(
    private val accountId: String,
    private val institutionRepository: InstitutionRepository,
    private val currencyFormatter: CurrencyFormatter,
    private val dateFormatter: DateFormatter,
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
     * Backing channel for [events].
     */
    private val eventChannel = Channel<AccountDetailEvent>(Channel.BUFFERED)

    /**
     * One-time events for the Account detail screen to act on.
     */
    val events: Flow<AccountDetailEvent> = eventChannel.receiveAsFlow()

    /**
     * Identifier of the account's institution, or null until the account loads or once it no
     * longer exists.
     */
    private var itemId: String? = null

    /**
     * Whether the current sync failure is already accounted for, so the inline error stays
     * hidden. Pull to refresh sets it: starting one dismisses the inline error, and a failure
     * of its own sync is reported by its error dialog instead. A refresh that succeeds, or a
     * sync that starts outside a refresh, clears it, so a later background failure shows inline
     * again.
     */
    private val isSyncFailureAcknowledged = MutableStateFlow(false)

    init {
        val accountUpdates = institutionRepository.observeAccount(accountId)
        val syncStateUpdates = accountUpdates
            .map { account -> account?.itemId }
            .distinctUntilChanged()
            .flatMapLatest(::observeSyncState)
            .onEach(::handleSyncState)
        combine(
            accountUpdates,
            institutionRepository.observeTransactions(accountId),
            syncStateUpdates,
            isSyncFailureAcknowledged,
            ::handleAccount,
        ).launchIn(viewModelScope)
    }

    /**
     * Refreshes the account's balance, then syncs its institution's transactions, showing an
     * error dialog if either fails. Skips the sync when the balance refresh fails. Does nothing
     * until the account has loaded.
     */
    fun refresh() {
        val itemId = this.itemId
        if (itemId != null) {
            mutableUiState.update {
                it.copy(
                    isRefreshing = true,
                )
            }
            isSyncFailureAcknowledged.value = true
            viewModelScope.launch {
                val errorMessage = when (institutionRepository.refreshInstitutions()) {
                    is NetworkResult.Success -> syncTransactions(itemId)
                    is NetworkResult.Failure -> REFRESH_FAILED_MESSAGE
                }
                // A clean refresh leaves nothing to acknowledge, so the next background failure
                // shows inline even if its Syncing emission is never seen.
                if (errorMessage == null) {
                    isSyncFailureAcknowledged.value = false
                }
                mutableUiState.update {
                    val shownErrorMessage = errorMessage ?: it.errorMessage
                    it.copy(
                        isRefreshing = false,
                        errorMessage = shownErrorMessage,
                    )
                }
            }
        }
    }

    /**
     * Closes the error dialog.
     */
    fun dismissError() =
        mutableUiState.update {
            it.copy(
                errorMessage = null,
            )
        }

    /**
     * Returns the transaction sync state of the institution identified by [itemId], or a lone
     * [TransactionSyncState.Idle] when there is none.
     *
     * @param itemId Identifier of the account's institution, or null if the account is gone.
     */
    private fun observeSyncState(
        itemId: String?,
    ): Flow<TransactionSyncState> =
        if (itemId == null) {
            flowOf(TransactionSyncState.Idle)
        } else {
            institutionRepository.observeTransactionSyncState(itemId)
        }

    /**
     * Clears the sync failure acknowledgement when [syncState] shows a sync running or done
     * outside a refresh. A refresh's sync may join one that was already running, so its states
     * can't be told apart from a background sync's.
     *
     * @param syncState The institution's latest transaction sync state.
     */
    private fun handleSyncState(
        syncState: TransactionSyncState,
    ) {
        if (syncState !is TransactionSyncState.Failed && !uiState.value.isRefreshing) {
            isSyncFailureAcknowledged.value = false
        }
    }

    /**
     * Shows [account] and its [transactions], or tells the screen to close if [account] is null.
     *
     * @param account The account, or null if it no longer exists.
     * @param transactions The account's transactions, newest first.
     * @param syncState The transaction sync state of the account's institution.
     * @param isSyncFailureAcknowledged Whether a sync failure is already accounted for.
     */
    private suspend fun handleAccount(
        account: FinancialAccount?,
        transactions: List<Transaction>,
        syncState: TransactionSyncState,
        isSyncFailureAcknowledged: Boolean,
    ) {
        itemId = account?.itemId
        if (account == null) {
            eventChannel.send(AccountDetailEvent.Close)
        } else {
            val transactionGroups = groupByDate(
                transactions,
                account.isoCurrencyCode,
            )
            val notice = when {
                syncState is TransactionSyncState.Syncing -> TransactionsNotice.Syncing
                syncState is TransactionSyncState.Failed && !isSyncFailureAcknowledged -> {
                    TransactionsNotice.SyncFailed
                }
                transactionGroups.isEmpty() -> TransactionsNotice.Empty
                else -> null
            }
            val content = AccountDetailContent.Loaded(
                account = toAccountSummary(account),
                transactionGroups = transactionGroups,
                notice = notice,
            )
            mutableUiState.update {
                it.copy(
                    content = content,
                )
            }
        }
    }

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
            is NetworkResult.Failure -> SYNC_FAILED_MESSAGE
        }

    /**
     * Returns [account] as the screen's header shows it.
     *
     * @param account The account to show.
     */
    private fun toAccountSummary(
        account: FinancialAccount,
    ): AccountSummary {
        val balance = currencyFormatter.format(
            account.balanceCurrent,
            account.isoCurrencyCode,
        )
        return AccountSummary(
            name = account.displayName,
            details = account.formattedDetails(),
            balance = balance,
        )
    }

    /**
     * Groups [transactions] by date, keeping their order, so newest-first transactions give
     * newest-first groups.
     *
     * @param transactions The account's transactions, newest first.
     * @param accountCurrencyCode Currency used for a transaction that doesn't name one.
     */
    private fun groupByDate(
        transactions: List<Transaction>,
        accountCurrencyCode: String?,
    ): List<TransactionDateGroup> =
        transactions
            .groupBy { transaction -> transaction.date }
            .map { (date, transactionsOnDate) ->
                val items = transactionsOnDate.map { transaction ->
                    toTransactionItem(
                        transaction,
                        accountCurrencyCode,
                    )
                }
                TransactionDateGroup(
                    key = date.toString(),
                    date = dateFormatter.format(date),
                    transactions = items,
                )
            }

    /**
     * Returns [transaction] as it's shown on screen, with its amount signed from the account
     * holder's view: "-$x" for money out and "+$x" for money in.
     *
     * @param transaction The transaction to show.
     * @param accountCurrencyCode Currency used when the transaction doesn't name one.
     */
    private fun toTransactionItem(
        transaction: Transaction,
        accountCurrencyCode: String?,
    ): TransactionItem {
        // A negative amount is money in.
        val isInflow = transaction.amount < 0
        val sign = if (isInflow) "+" else "-"
        // The magnitude is formatted on its own, since the formatter would add its own minus sign.
        val magnitude = currencyFormatter.format(
            abs(transaction.amount),
            transaction.isoCurrencyCode ?: accountCurrencyCode,
        )
        return TransactionItem(
            transactionId = transaction.transactionId,
            name = transaction.merchantName ?: transaction.name,
            amount = sign + magnitude,
            isInflow = isInflow,
            isPending = transaction.pending,
        )
    }

    companion object {
        /**
         * Message shown when refreshing the balance fails.
         */
        private const val REFRESH_FAILED_MESSAGE = "Couldn't refresh your balance."

        /**
         * Message shown when syncing the transactions fails.
         */
        private const val SYNC_FAILED_MESSAGE = "Couldn't sync transactions."

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
        ) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                ): T =
                    AccountDetailViewModel(
                        accountId = accountId,
                        institutionRepository = appContainer.institutionRepository,
                        currencyFormatter = CurrencyFormatter(),
                        dateFormatter = DateFormatter(),
                    ) as T
            }
    }
}

/**
 * UI state for the Account detail screen.
 *
 * @property content What the screen's body shows.
 * @property isRefreshing Whether a pull to refresh is in progress.
 * @property errorMessage Error shown in a dialog until the user dismisses it, or null if none.
 */
data class AccountDetailUiState(
    val content: AccountDetailContent = AccountDetailContent.Loading,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * What the Account detail screen's body shows.
 */
sealed interface AccountDetailContent {

    /**
     * The account hasn't loaded yet.
     */
    data object Loading : AccountDetailContent

    /**
     * The account and its transactions.
     *
     * @property account The account's balance, name and details.
     * @property transactionGroups Every cached transaction of the account, grouped by date,
     *   newest first. Empty if the account has none.
     * @property notice Shown above the transactions, or null if none.
     */
    data class Loaded(
        val account: AccountSummary,
        val transactionGroups: List<TransactionDateGroup>,
        val notice: TransactionsNotice?,
    ) : AccountDetailContent
}

/**
 * A line shown above the account's transactions.
 */
enum class TransactionsNotice {

    /**
     * The account's transactions are syncing.
     */
    Syncing,

    /**
     * A background sync of the account's transactions failed.
     */
    SyncFailed,

    /**
     * The account has no transactions, and none are syncing.
     */
    Empty,
}

/**
 * The account as the screen's header shows it.
 *
 * @property name Account name (e.g. "Total Checking").
 * @property details Masked number and type (e.g. "•••• 1234 · Depository").
 * @property balance Current balance in the account's currency, or "—" if unknown.
 */
data class AccountSummary(
    val name: String,
    val details: String,
    val balance: String,
)

/**
 * The transactions that fall on one date.
 *
 * @property key Stable identifier of the date.
 * @property date The date, formatted for display.
 * @property transactions The transactions on the date, newest first.
 */
data class TransactionDateGroup(
    val key: String,
    val date: String,
    val transactions: List<TransactionItem>,
)

/**
 * A transaction as the screen shows it.
 *
 * @property transactionId Stable identifier of the transaction.
 * @property name Merchant name, or the transaction's name when the merchant is unknown.
 * @property amount Amount signed from the account holder's view (e.g. "-$4.50" or "+$20.00").
 * @property isInflow Whether the transaction brings money into the account.
 * @property isPending Whether the transaction hasn't posted yet.
 */
data class TransactionItem(
    val transactionId: String,
    val name: String,
    val amount: String,
    val isInflow: Boolean,
    val isPending: Boolean,
)

/**
 * One-time events the Account detail screen acts on.
 */
sealed interface AccountDetailEvent {

    /**
     * Close the screen; the account no longer exists.
     */
    data object Close : AccountDetailEvent
}
