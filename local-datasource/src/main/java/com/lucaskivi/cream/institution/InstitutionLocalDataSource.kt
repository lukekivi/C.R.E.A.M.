package com.lucaskivi.cream.institution

import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.core.institution.model.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Contract for local financial-institution data operations.
 */
interface InstitutionLocalDataSource {

    /**
     * Emits the full list of linked institutions, each paired with its financial accounts.
     * Re-emits whenever the underlying data changes.
     */
    fun observeInstitutionsWithAccounts(): Flow<List<LinkedInstitutionWithAccounts>>

    /**
     * Emits the financial account with the given [accountId], or null if none exists.
     * Re-emits whenever the account row changes.
     *
     * @param accountId The account to observe.
     */
    fun observeAccount(accountId: String): Flow<FinancialAccount?>

    /**
     * Emits all transactions for [accountId], ordered newest-first. Re-emits whenever any
     * transaction for that account changes.
     *
     * @param accountId The account whose transactions to observe.
     */
    fun observeTransactions(accountId: String): Flow<List<Transaction>>

    /**
     * Inserts or replaces each institution in [institutions].
     *
     * @param institutions Institutions to upsert.
     */
    suspend fun upsertInstitutions(institutions: List<LinkedInstitution>)

    /**
     * Inserts or replaces each account in [accounts].
     *
     * @param accounts Accounts to upsert.
     */
    suspend fun upsertAccounts(accounts: List<FinancialAccount>)

    /**
     * Inserts or replaces each transaction in [transactions].
     *
     * @param transactions Transactions to upsert.
     */
    suspend fun upsertTransactions(transactions: List<Transaction>)

    /**
     * Deletes the institution identified by [itemId] and, via cascade, all of its accounts and
     * their transactions.
     *
     * @param itemId The institution to delete.
     */
    suspend fun deleteInstitution(itemId: String)

    /**
     * Deletes every transaction whose ID appears in [transactionIds].
     *
     * @param transactionIds IDs of the transactions to delete.
     */
    suspend fun deleteTransactions(transactionIds: List<String>)
}
