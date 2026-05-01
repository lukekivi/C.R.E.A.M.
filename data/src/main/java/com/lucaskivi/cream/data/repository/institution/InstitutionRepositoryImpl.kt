package com.lucaskivi.cream.data.repository.institution

import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.catching
import com.lucaskivi.cream.core.getOrThrow
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
    ): NetworkResult<Unit> {
        when (
            val result = remoteDataSource.exchangePublicToken(
                publicToken = publicToken,
                institutionId = institutionId,
                institutionName = institutionName,
            )
        ) {
            is NetworkResult.Failure -> return result
            is NetworkResult.Success -> Unit
        }
        return refreshInstitutions()
    }

    override suspend fun syncTransactions(itemId: String): NetworkResult<Unit> =
        catching {
            val cursor = localDataSource.getTransactionCursor(itemId)
            val delta = remoteDataSource.syncTransactions(itemId, cursor).getOrThrow()
            localDataSource.upsertTransactions(delta.added + delta.modified)
            localDataSource.deleteTransactions(delta.removedIds)
            localDataSource.saveTransactionCursor(itemId, delta.nextCursor)
        }

    override suspend fun disconnectInstitution(itemId: String): NetworkResult<Unit> {
        when (val result = remoteDataSource.disconnectInstitution(itemId)) {
            is NetworkResult.Failure -> return result
            is NetworkResult.Success -> Unit
        }
        localDataSource.deleteInstitution(itemId)
        return NetworkResult.Success(Unit)
    }

    override suspend fun refreshInstitutions(): NetworkResult<Unit> =
        catching {
            val institutions = remoteDataSource.listInstitutions().getOrThrow()
            localDataSource.upsertInstitutions(institutions.map { it.institution })
            localDataSource.upsertAccounts(institutions.flatMap { it.accounts })
        }
}
