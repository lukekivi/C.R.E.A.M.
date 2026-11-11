package com.lucaskivi.cream.data.repository.institution

import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.catching
import com.lucaskivi.cream.core.flatMap
import com.lucaskivi.cream.core.map
import com.lucaskivi.cream.core.onSuccess
import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.core.institution.model.Transaction
import com.lucaskivi.cream.core.institution.model.TransactionSyncState
import com.lucaskivi.cream.institution.InstitutionLocalDataSource
import com.lucaskivi.cream.institution.InstitutionRemoteDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Production implementation of [InstitutionRepository].
 *
 * @param remoteDataSource Source for network-fetched institution data.
 * @param localDataSource Source for on-device cached institution data.
 * @param externalScope App-lifetime scope that runs every transaction sync, so a sync outlives
 *   the screen that started it and can be shared by every caller syncing the same item.
 */
class InstitutionRepositoryImpl(
    private val remoteDataSource: InstitutionRemoteDataSource,
    private val localDataSource: InstitutionLocalDataSource,
    private val externalScope: CoroutineScope,
) : InstitutionRepository {

    /**
     * The sync currently running for each item id. At most one runs per item, so two
     * read-cursor → apply → save-cursor sequences never interleave. Guarded by its own lock.
     */
    private val inFlightSyncs = HashMap<String, Deferred<NetworkResult<Unit>>>()

    /**
     * Sync state per item id. An absent item is [TransactionSyncState.Idle], so idle items are
     * removed rather than stored.
     */
    private val syncStates = MutableStateFlow<Map<String, TransactionSyncState>>(emptyMap())

    override fun observeInstitutionsWithAccounts(): Flow<List<LinkedInstitutionWithAccounts>> =
        localDataSource.observeInstitutionsWithAccounts()

    override fun observeAccount(
        accountId: String,
    ): Flow<FinancialAccount?> =
        localDataSource.observeAccount(accountId)

    override fun observeTransactions(
        accountId: String,
    ): Flow<List<Transaction>> =
        localDataSource.observeTransactions(accountId)

    override fun observeTransactionSyncState(
        itemId: String,
    ): Flow<TransactionSyncState> =
        syncStates
            .map { states -> states[itemId] ?: TransactionSyncState.Idle }
            .distinctUntilChanged()

    override suspend fun createLinkToken(): NetworkResult<String> =
        remoteDataSource.createLinkToken()

    override suspend fun linkInstitution(
        publicToken: String,
        institutionId: String,
        institutionName: String,
    ): NetworkResult<Unit> =
        remoteDataSource
            .exchangePublicToken(
                publicToken = publicToken,
                institutionId = institutionId,
                institutionName = institutionName,
            )
            .flatMap { linked ->
                // Transactions reference accounts, so the sync can only start once the refresh
                // has written the new item's accounts. The sync isn't awaited; its outcome is
                // reported through observeTransactionSyncState.
                refreshInstitutions().onSuccess { joinOrStartSync(linked.itemId) }
            }

    override suspend fun syncTransactions(
        itemId: String,
    ): NetworkResult<Unit> {
        val sync = joinOrStartSync(itemId)
        // The caller doesn't own the shared sync: cancelling the caller only stops its await.
        // If the sync itself was cancelled (by clearLocalCache) while the caller is still
        // active, the caller gets a Failure rather than an exception that would silently end
        // its coroutine.
        return try {
            sync.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            NetworkResult.Failure(e)
        }
    }

    override suspend fun disconnectInstitution(
        itemId: String,
    ): NetworkResult<Unit> =
        remoteDataSource
            .disconnectInstitution(itemId)
            .onSuccess { localDataSource.deleteInstitution(itemId) }

    override suspend fun refreshInstitutions(): NetworkResult<Unit> =
        remoteDataSource
            .listInstitutions()
            .map { institutions ->
                localDataSource.upsertInstitutions(institutions.map { it.institution })
                localDataSource.upsertAccounts(institutions.flatMap { it.accounts })
            }

    override suspend fun clearLocalCache(): NetworkResult<Unit> {
        // Cancel before deleting, so a sync finishing late can't write the previous user's
        // transactions back.
        // Snapshot under the lock, but cancel outside it: a cancelled sync's completion handler
        // takes the lock too.
        val syncs = synchronized(inFlightSyncs) { inFlightSyncs.values.toList() }
        syncs.forEach { it.cancelAndJoin() }
        // A sync started after the snapshot is still running, so it stays Syncing.
        synchronized(inFlightSyncs) {
            syncStates.value = inFlightSyncs.keys.associateWith { TransactionSyncState.Syncing }
        }
        return catching { localDataSource.deleteAllInstitutions() }
    }

    /**
     * Returns the sync running for [itemId], starting one if none is. A new sync marks the item
     * [TransactionSyncState.Syncing] before this returns.
     *
     * @param itemId Identifier of the institution to sync.
     */
    private fun joinOrStartSync(
        itemId: String,
    ): Deferred<NetworkResult<Unit>> {
        val sync = synchronized(inFlightSyncs) {
            inFlightSyncs.getOrPut(itemId) {
                // Lazy, so it's registered before it can run, and its completion handler always
                // finds it.
                val started = externalScope.async(start = CoroutineStart.LAZY) { fetchAndApplyTransactions(itemId) }
                started.invokeOnCompletion { cause -> finishSync(itemId, started, cause) }
                syncStates.update { states -> states + (itemId to TransactionSyncState.Syncing) }
                started
            }
        }
        sync.start()
        return sync
    }

    /**
     * Fetches the transaction delta since the stored cursor and applies it locally. Never throws
     * except on cancellation: syncs also run in the background, where an uncaught throw would
     * crash the app.
     *
     * @param itemId Identifier of the institution to sync.
     */
    private suspend fun fetchAndApplyTransactions(
        itemId: String,
    ): NetworkResult<Unit> =
        catching { localDataSource.getTransactionCursor(itemId) }
            .flatMap { cursor -> remoteDataSource.syncTransactions(itemId, cursor) }
            .flatMap { delta ->
                catching {
                    localDataSource.upsertTransactions(delta.added + delta.modified)
                    localDataSource.deleteTransactions(delta.removedIds)
                    localDataSource.saveTransactionCursor(itemId, delta.nextCursor)
                }
            }

    /**
     * Records [sync] of [itemId] ending: the next call starts a fresh sync, and the item becomes
     * [TransactionSyncState.Idle] on success or cancellation, or [TransactionSyncState.Failed]
     * on a failed result or an unexpected throw.
     *
     * @param itemId Identifier of the institution whose sync ended.
     * @param sync The sync that ended.
     * @param cause Why the sync was cancelled or threw, or null if it completed normally.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun finishSync(
        itemId: String,
        sync: Deferred<NetworkResult<Unit>>,
        cause: Throwable?,
    ) {
        synchronized(inFlightSyncs) {
            inFlightSyncs.remove(itemId)
            val outcome = when (cause) {
                null -> {
                    sync
                        .getCompleted()
                        .toSyncOutcome()
                }
                is CancellationException -> TransactionSyncState.Idle
                else -> TransactionSyncState.Failed(cause)
            }
            syncStates.update { states ->
                if (outcome == TransactionSyncState.Idle) states - itemId else states + (itemId to outcome)
            }
        }
    }

    /**
     * Returns the sync state this sync result leaves an item in.
     */
    private fun NetworkResult<Unit>.toSyncOutcome(): TransactionSyncState =
        when (this) {
            is NetworkResult.Success -> TransactionSyncState.Idle
            is NetworkResult.Failure -> TransactionSyncState.Failed(this.cause)
        }
}
