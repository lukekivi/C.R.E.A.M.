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
 * @param transformer Converts between domain models and database entities.
 */
class InstitutionLocalDataSourceImpl internal constructor(
    private val dao: InstitutionDao,
    private val transformer: InstitutionEntityTransformer,
) : InstitutionLocalDataSource {

    override fun observeInstitutionsWithAccounts(): Flow<List<LinkedInstitutionWithAccounts>> =
        dao
            .observeInstitutionsWithAccounts()
            .map { list -> list.map { transformer.toLinkedInstitutionWithAccounts(it) } }

    override fun observeAccount(accountId: String): Flow<FinancialAccount?> =
        dao
            .observeAccount(accountId)
            .map { it?.let(transformer::toFinancialAccount) }

    override fun observeTransactions(accountId: String): Flow<List<Transaction>> =
        dao
            .observeTransactions(accountId)
            .map { list -> list.map { transformer.toTransaction(it) } }

    override suspend fun upsertInstitutions(institutions: List<LinkedInstitution>) {
        dao.upsertInstitutions(institutions.map { transformer.toLinkedInstitutionEntity(it) })
    }

    override suspend fun upsertAccounts(accounts: List<FinancialAccount>) {
        dao.upsertAccounts(accounts.map { transformer.toFinancialAccountEntity(it) })
    }

    override suspend fun upsertTransactions(transactions: List<Transaction>) {
        dao.upsertTransactions(transactions.map { transformer.toTransactionEntity(it) })
    }

    override suspend fun deleteInstitution(itemId: String) {
        dao.deleteInstitution(itemId)
    }

    override suspend fun deleteTransactions(transactionIds: List<String>) {
        dao.deleteTransactions(transactionIds)
    }
}
