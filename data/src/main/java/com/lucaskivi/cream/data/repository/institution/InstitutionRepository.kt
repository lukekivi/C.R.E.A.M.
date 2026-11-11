package com.lucaskivi.cream.data.repository.institution

import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.core.institution.model.Transaction
import com.lucaskivi.cream.core.institution.model.TransactionSyncState
import kotlinx.coroutines.flow.Flow

/**
 * Contract for financial-institution data operations exposed to the UI layer.
 *
 * Observe operations read from the local cache and emit updates in real time. Mutating
 * operations talk to the remote backend and write results into the local cache.
 */
interface InstitutionRepository {

    /**
     * Emits the full list of linked institutions, each paired with its financial accounts.
     * Re-emits whenever any institution or account row changes locally.
     */
    fun observeInstitutionsWithAccounts(): Flow<List<LinkedInstitutionWithAccounts>>

    /**
     * Emits the financial account identified by [accountId], or null if none exists.
     * Re-emits whenever that account's row changes locally.
     *
     * @param accountId The account to observe.
     */
    fun observeAccount(
        accountId: String,
    ): Flow<FinancialAccount?>

    /**
     * Emits all transactions for [accountId], ordered newest-first. Re-emits whenever any
     * transaction for that account changes locally.
     *
     * @param accountId The account whose transactions to observe.
     */
    fun observeTransactions(
        accountId: String,
    ): Flow<List<Transaction>>

    /**
     * Emits the transaction sync state of the institution identified by [itemId]: its current
     * state on collection, then every change.
     *
     * The state is [TransactionSyncState.Syncing] while a sync of [itemId] is in flight, whoever
     * started it, including the background sync started by [linkInstitution]. When it ends the
     * state becomes [TransactionSyncState.Idle] on success or cancellation, or
     * [TransactionSyncState.Failed] on failure. An institution that hasn't
     * synced since launch is [TransactionSyncState.Idle], and [clearLocalCache] resets every
     * institution to [TransactionSyncState.Idle].
     *
     * @param itemId Identifier of the institution to observe.
     */
    fun observeTransactionSyncState(
        itemId: String,
    ): Flow<TransactionSyncState>

    /**
     * Creates a short-lived token that starts the institution-linking UI flow.
     *
     * @return [NetworkResult.Success] with the link token, or [NetworkResult.Failure] if the
     *   remote call fails.
     */
    suspend fun createLinkToken(): NetworkResult<String>

    /**
     * Completes the institution-linking flow and refreshes the local cache. Exchanges
     * [publicToken] with the backend, then fetches the updated institution and account list.
     *
     * Once both succeed, the new institution's transactions are synced in the background, outliving
     * the caller. That sync's progress and outcome are reported through
     * [observeTransactionSyncState] only; its failure doesn't fail the link.
     *
     * @param publicToken One-time token produced at the end of the link UI flow.
     * @param institutionId Provider's identifier for the institution the user selected.
     * @param institutionName Human-readable name of the institution the user selected.
     * @return [NetworkResult.Success] on success, or [NetworkResult.Failure] if either the
     *   token exchange or the subsequent institution fetch fails.
     */
    suspend fun linkInstitution(
        publicToken: String,
        institutionId: String,
        institutionName: String,
    ): NetworkResult<Unit>

    /**
     * Fetches incremental transaction changes for [itemId] from the backend and applies them
     * to the local cache. Uses the stored cursor from the previous sync; passes null on first sync.
     *
     * At most one sync runs per institution: a call made while one is in flight joins it and
     * returns its result instead of starting another. The sync belongs to the app, not the
     * caller, so cancelling a caller doesn't stop it. A call made after it ends starts a fresh
     * one. Progress and outcome are also reported through [observeTransactionSyncState].
     *
     * @param itemId Identifier of the institution to sync.
     * @return [NetworkResult.Success] on success, or [NetworkResult.Failure] if the remote
     *   call or a local write fails, or if the sync is cancelled by [clearLocalCache]. The local
     *   cache is only mutated when the remote call succeeds.
     */
    suspend fun syncTransactions(
        itemId: String,
    ): NetworkResult<Unit>

    /**
     * Unlinks the institution identified by [itemId] on the backend, then removes it — along
     * with all its accounts and transactions — from the local cache.
     *
     * @param itemId Identifier of the institution to disconnect.
     * @return [NetworkResult.Success] on success, or [NetworkResult.Failure] if the remote
     *   call fails. The local cache is only mutated on success.
     */
    suspend fun disconnectInstitution(
        itemId: String,
    ): NetworkResult<Unit>

    /**
     * Fetches the full institution and account list from the backend and writes it to the
     * local cache. Does not affect stored transaction cursors or transactions.
     *
     * @return [NetworkResult.Success] on success, or [NetworkResult.Failure] if the remote
     *   call fails.
     */
    suspend fun refreshInstitutions(): NetworkResult<Unit>

    /**
     * Clears all locally cached institution data. The backend is untouched: linked
     * institutions stay linked and repopulate on the next refresh.
     *
     * Every in-flight [syncTransactions] run, including the background sync started by
     * [linkInstitution], is cancelled first, so none can write data back afterwards. Then every
     * institution's sync state resets to [TransactionSyncState.Idle].
     *
     * @return [NetworkResult.Success] on success, or [NetworkResult.Failure] if the local cache
     *   could not be cleared.
     */
    suspend fun clearLocalCache(): NetworkResult<Unit>
}
