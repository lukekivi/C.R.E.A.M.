package com.lucaskivi.cream.institution

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for institution, financial account, and transaction operations.
 *
 * All queries that return [Flow] are observed — Room re-emits a new value whenever the
 * underlying table changes.
 */
@Dao
interface InstitutionDao {

    /**
     * Emits the full list of linked institutions, each paired with its financial accounts.
     *
     * The [Transaction] annotation ensures the institution row and its account rows are read
     * atomically, preventing partial results during concurrent writes.
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
     * Inserts each institution that does not already exist. Existing rows — and crucially their
     * [LinkedInstitutionEntity.transactionCursor] values — are left untouched.
     *
     * @param institutions Institutions to insert.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInstitutionsIfAbsent(institutions: List<LinkedInstitutionEntity>)

    /**
     * Updates the [institutionId] and [institutionName] of the row identified by [itemId],
     * leaving all other columns (including [LinkedInstitutionEntity.transactionCursor]) unchanged.
     *
     * @param itemId Primary key of the institution to update.
     * @param institutionId New provider institution identifier.
     * @param institutionName New human-readable institution name.
     */
    @Query(
        "UPDATE linked_institution " +
            "SET institutionId = :institutionId, institutionName = :institutionName " +
            "WHERE itemId = :itemId"
    )
    suspend fun updateInstitutionInfo(
        itemId: String,
        institutionId: String,
        institutionName: String,
    )

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
     * Returns the stored transaction sync cursor for [itemId], or null if transactions have
     * never been synced for that institution.
     *
     * @param itemId The institution whose cursor to retrieve.
     */
    @Query("SELECT transactionCursor FROM linked_institution WHERE itemId = :itemId")
    suspend fun getTransactionCursor(itemId: String): String?

    /**
     * Persists [cursor] as the transaction sync cursor for the institution identified by [itemId].
     *
     * @param itemId The institution whose cursor to update.
     * @param cursor Opaque cursor string from the last successful sync.
     */
    @Query(
        "UPDATE linked_institution SET transactionCursor = :cursor WHERE itemId = :itemId"
    )
    suspend fun saveTransactionCursor(
        itemId: String,
        cursor: String,
    )

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
