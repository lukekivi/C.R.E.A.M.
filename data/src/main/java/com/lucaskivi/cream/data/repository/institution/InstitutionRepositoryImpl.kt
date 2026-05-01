package com.lucaskivi.cream.data.repository.institution

import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.flatMap
import com.lucaskivi.cream.core.map
import com.lucaskivi.cream.core.onSuccess
import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.core.institution.model.Transaction
import com.lucaskivi.cream.institution.InstitutionLocalDataSource
import com.lucaskivi.cream.institution.InstitutionRemoteDataSource
import kotlinx.coroutines.flow.Flow

/**
 * Production implementation of [InstitutionRepository].
 *
 * @param remoteDataSource Source for network-fetched institution data.
 * @param localDataSource Source for on-device cached institution data.
 */
class InstitutionRepositoryImpl(
    private val remoteDataSource: InstitutionRemoteDataSource,
    private val localDataSource: InstitutionLocalDataSource,
) : InstitutionRepository {

    override fun observeInstitutionsWithAccounts(): Flow<List<LinkedInstitutionWithAccounts>> =
        localDataSource.observeInstitutionsWithAccounts()

    override fun observeAccount(accountId: String): Flow<FinancialAccount?> =
        localDataSource.observeAccount(accountId)

    override fun observeTransactions(accountId: String): Flow<List<Transaction>> =
        localDataSource.observeTransactions(accountId)

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
            .flatMap { refreshInstitutions() }

    override suspend fun syncTransactions(itemId: String): NetworkResult<Unit> {
        val cursor = localDataSource.getTransactionCursor(itemId)
        return remoteDataSource
            .syncTransactions(itemId, cursor)
            .map { delta ->
                localDataSource.upsertTransactions(delta.added + delta.modified)
                localDataSource.deleteTransactions(delta.removedIds)
                localDataSource.saveTransactionCursor(itemId, delta.nextCursor)
            }
    }

    override suspend fun disconnectInstitution(itemId: String): NetworkResult<Unit> =
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
}
