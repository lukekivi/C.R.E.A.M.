package com.lucaskivi.cream.institution

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for linked institutions and the data they own: each institution's financial
 * accounts and each account's transactions.
 */
@Dao
interface InstitutionDao {

    /**
     * Emits the full list of linked institutions, each paired with its financial accounts.
     */
    @Transaction
    @Query("SELECT * FROM linked_institution")
    fun observeInstitutionsWithAccounts(): Flow<List<InstitutionWithAccountsEntity>>

    /**
     * Emits the financial account with the given [accountId], or null if none exists.
     *
     * @param accountId The account to observe.
     */
    @Query("SELECT * FROM financial_account WHERE accountId = :accountId")
    fun observeAccount(accountId: String): Flow<FinancialAccountEntity?>

    /**
     * Emits all transactions for [accountId], ordered newest-first by date.
     *
     * @param accountId The account whose transactions to observe.
     */
    @Query("SELECT * FROM `transaction` WHERE accountId = :accountId ORDER BY date DESC")
    fun observeTransactions(accountId: String): Flow<List<TransactionEntity>>

    /**
     * Inserts or replaces each institution in [institutions].
     *
     * @param institutions Institutions to upsert.
     */
    @Upsert
    suspend fun upsertInstitutions(institutions: List<LinkedInstitutionEntity>)

    /**
     * Inserts or replaces each account in [accounts].
     *
     * @param accounts Accounts to upsert.
     */
    @Upsert
    suspend fun upsertAccounts(accounts: List<FinancialAccountEntity>)

    /**
     * Inserts or replaces each transaction in [transactions].
     *
     * @param transactions Transactions to upsert.
     */
    @Upsert
    suspend fun upsertTransactions(transactions: List<TransactionEntity>)

    /**
     * Deletes the institution identified by [itemId] and, via cascade, all of its accounts and
     * their transactions.
     *
     * @param itemId The institution to delete.
     */
    @Query("DELETE FROM linked_institution WHERE itemId = :itemId")
    suspend fun deleteInstitution(itemId: String)

    /**
     * Deletes every transaction whose ID appears in [transactionIds].
     *
     * @param transactionIds IDs of the transactions to delete.
     */
    @Query("DELETE FROM `transaction` WHERE transactionId IN (:transactionIds)")
    suspend fun deleteTransactions(transactionIds: List<String>)
}
