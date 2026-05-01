package com.lucaskivi.cream.institution

import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.core.institution.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation of [InstitutionLocalDataSource].
 *
 * Instantiate via [institutionLocalDataSource]; the constructor is internal to prevent use
 * outside this module.
 *
 * @param dao DAO used to read from and write to the local database.
 */
class InstitutionLocalDataSourceImpl internal constructor(
    private val dao: InstitutionDao,
) : InstitutionLocalDataSource {

    override fun observeInstitutionsWithAccounts(): Flow<List<LinkedInstitutionWithAccounts>> =
        dao.observeInstitutionsWithAccounts()
            .map { list -> list.map { it.toDomain() } }

    override fun observeAccount(accountId: String): Flow<FinancialAccount?> =
        dao.observeAccount(accountId)
            .map { it?.toDomain() }

    override fun observeTransactions(accountId: String): Flow<List<Transaction>> =
        dao.observeTransactions(accountId)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun upsertInstitutions(institutions: List<LinkedInstitution>) {
        dao.upsertInstitutions(institutions.map { it.toEntity() })
    }

    override suspend fun upsertAccounts(accounts: List<FinancialAccount>) {
        dao.upsertAccounts(accounts.map { it.toEntity() })
    }

    override suspend fun upsertTransactions(transactions: List<Transaction>) {
        dao.upsertTransactions(transactions.map { it.toEntity() })
    }

    override suspend fun deleteInstitution(itemId: String) {
        dao.deleteInstitution(itemId)
    }

    override suspend fun deleteTransactions(transactionIds: List<String>) {
        dao.deleteTransactions(transactionIds)
    }
}
