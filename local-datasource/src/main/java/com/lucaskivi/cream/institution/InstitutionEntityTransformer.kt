package com.lucaskivi.cream.institution

import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.FinancialAccountType
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.core.institution.model.Transaction

/**
 * Converts between institution domain models and their database entities.
 */
internal class InstitutionEntityTransformer {

    /**
     * Converts an institution row into a [LinkedInstitution].
     *
     * @param entity The institution row.
     */
    fun toLinkedInstitution(entity: LinkedInstitutionEntity): LinkedInstitution =
        LinkedInstitution(
            itemId = entity.itemId,
            institutionId = entity.institutionId,
            institutionName = entity.institutionName,
        )

    /**
     * Converts an institution row and its account rows into a [LinkedInstitutionWithAccounts].
     *
     * @param entity The institution row paired with its account rows.
     */
    fun toLinkedInstitutionWithAccounts(
        entity: InstitutionWithAccountsEntity,
    ): LinkedInstitutionWithAccounts =
        LinkedInstitutionWithAccounts(
            institution = toLinkedInstitution(entity.institution),
            accounts = entity.accounts.map { toFinancialAccount(it) },
        )

    /**
     * Converts an account row into a [FinancialAccount]. Unknown [FinancialAccountEntity.type]
     * values fall back to [FinancialAccountType.OTHER].
     *
     * @param entity The account row.
     */
    fun toFinancialAccount(entity: FinancialAccountEntity): FinancialAccount {
        val accountType = try {
            FinancialAccountType.valueOf(entity.type)
        } catch (_: IllegalArgumentException) {
            FinancialAccountType.OTHER
        }
        return FinancialAccount(
            accountId = entity.accountId,
            itemId = entity.itemId,
            displayName = entity.displayName,
            mask = entity.mask,
            type = accountType,
            subtype = entity.subtype,
            balanceCurrent = entity.balanceCurrent,
            balanceAvailable = entity.balanceAvailable,
            isoCurrencyCode = entity.isoCurrencyCode,
        )
    }

    /**
     * Converts a transaction row into a [Transaction].
     *
     * @param entity The transaction row.
     */
    fun toTransaction(entity: TransactionEntity): Transaction =
        Transaction(
            transactionId = entity.transactionId,
            accountId = entity.accountId,
            amount = entity.amount,
            isoCurrencyCode = entity.isoCurrencyCode,
            date = entity.date,
            name = entity.name,
            merchantName = entity.merchantName,
            pending = entity.pending,
            category = entity.category,
        )

    /**
     * Converts a [LinkedInstitution] into its database row.
     *
     * @param institution The institution to convert.
     */
    fun toLinkedInstitutionEntity(institution: LinkedInstitution): LinkedInstitutionEntity =
        LinkedInstitutionEntity(
            itemId = institution.itemId,
            institutionId = institution.institutionId,
            institutionName = institution.institutionName,
        )

    /**
     * Converts a [FinancialAccount] into its database row, storing [FinancialAccount.type] as its
     * enum name.
     *
     * @param account The account to convert.
     */
    fun toFinancialAccountEntity(account: FinancialAccount): FinancialAccountEntity =
        FinancialAccountEntity(
            accountId = account.accountId,
            itemId = account.itemId,
            displayName = account.displayName,
            mask = account.mask,
            type = account.type.name,
            subtype = account.subtype,
            balanceCurrent = account.balanceCurrent,
            balanceAvailable = account.balanceAvailable,
            isoCurrencyCode = account.isoCurrencyCode,
        )

    /**
     * Converts a [Transaction] into its database row.
     *
     * @param transaction The transaction to convert.
     */
    fun toTransactionEntity(transaction: Transaction): TransactionEntity =
        TransactionEntity(
            transactionId = transaction.transactionId,
            accountId = transaction.accountId,
            amount = transaction.amount,
            isoCurrencyCode = transaction.isoCurrencyCode,
            date = transaction.date,
            name = transaction.name,
            merchantName = transaction.merchantName,
            pending = transaction.pending,
            category = transaction.category,
        )
}
