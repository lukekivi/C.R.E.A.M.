package com.lucaskivi.cream.data.repository.institution

import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.core.institution.model.Transaction
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
    fun observeAccount(accountId: String): Flow<FinancialAccount?>

    /**
     * Emits all transactions for [accountId], ordered newest-first. Re-emits whenever any
     * transaction for that account changes locally.
     *
     * @param accountId The account whose transactions to observe.
     */
    fun observeTransactions(accountId: String): Flow<List<Transaction>>

    /**
     * Completes the institution-linking flow and refreshes the local cache. Exchanges
     * [publicToken] with the backend, then fetches the updated institution and account list.
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
     * @param itemId Identifier of the institution to sync.
     * @return [NetworkResult.Success] on success, or [NetworkResult.Failure] if the remote
     *   call fails. The local cache is only mutated on success.
     */
    suspend fun syncTransactions(itemId: String): NetworkResult<Unit>

    /**
     * Unlinks the institution identified by [itemId] on the backend, then removes it — along
     * with all its accounts and transactions — from the local cache.
     *
     * @param itemId Identifier of the institution to disconnect.
     * @return [NetworkResult.Success] on success, or [NetworkResult.Failure] if the remote
     *   call fails. The local cache is only mutated on success.
     */
    suspend fun disconnectInstitution(itemId: String): NetworkResult<Unit>

    /**
     * Fetches the full institution and account list from the backend and writes it to the
     * local cache. Does not affect stored transaction cursors or transactions.
     *
     * @return [NetworkResult.Success] on success, or [NetworkResult.Failure] if the remote
     *   call fails.
     */
    suspend fun refreshInstitutions(): NetworkResult<Unit>
}
