package com.lucaskivi.cream.institution

import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.FinancialAccountType
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.core.institution.model.Transaction
import com.lucaskivi.cream.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.institution.model.TransactionDelta
import com.lucaskivi.cream.remote.asMap
import com.lucaskivi.cream.remote.asMapList
import java.time.LocalDate

/**
 * Converts institution responses into domain models.
 *
 * Every method throws ([ClassCastException] or [NullPointerException]) when a response doesn't
 * match the expected shape; callers are expected to turn that into a failure result.
 */
internal class InstitutionTransformer {

    /**
     * Extracts the link token from a create-link-token response.
     *
     * @param response Response payload of the create-link-token request.
     */
    fun toLinkToken(response: Map<String, Any?>): String =
        response["linkToken"] as String

    /**
     * Builds the newly linked institution from an exchange-public-token response.
     *
     * @param response Response payload of the exchange-public-token request.
     * @param institutionId Identifier of the institution the user selected; the response
     *   doesn't echo it back.
     */
    fun toLinkedInstitution(
        response: Map<String, Any?>,
        institutionId: String,
    ): LinkedInstitution =
        LinkedInstitution(
            itemId = response["itemId"] as String,
            institutionId = institutionId,
            institutionName = response["institutionName"] as String,
        )

    /**
     * Extracts every linked institution, with its accounts, from a list-institutions response.
     *
     * @param response Response payload of the list-institutions request.
     */
    fun toLinkedInstitutionsWithAccounts(
        response: Map<String, Any?>,
    ): List<LinkedInstitutionWithAccounts> =
        response["institutions"].asMapList()
            .map { toLinkedInstitutionWithAccounts(it) }

    /**
     * Converts one raw institution entry into a [LinkedInstitutionWithAccounts].
     *
     * @param raw A single entry of the list-institutions `institutions` array.
     */
    fun toLinkedInstitutionWithAccounts(raw: Map<String, Any?>): LinkedInstitutionWithAccounts {
        val itemId = raw["itemId"] as String
        val institution = LinkedInstitution(
            itemId = itemId,
            institutionId = raw["institutionId"] as String,
            institutionName = raw["institutionName"] as String,
        )
        val accounts = raw["accounts"].asMapList()
            .map { toFinancialAccount(it, itemId) }
        return LinkedInstitutionWithAccounts(
            institution = institution,
            accounts = accounts,
        )
    }

    /**
     * Converts one raw account entry into an [FinancialAccount].
     *
     * @param raw A single entry of an institution's `accounts` array.
     * @param itemId Identifier of the institution that owns this account.
     */
    fun toFinancialAccount(
        raw: Map<String, Any?>,
        itemId: String,
    ): FinancialAccount {
        val balance = raw["balance"].asMap()
        return FinancialAccount(
            accountId = raw["accountId"] as String,
            itemId = itemId,
            displayName = raw["name"] as String,
            mask = raw["mask"] as? String,
            type = toFinancialAccountType(raw["type"] as? String),
            subtype = raw["subtype"] as? String,
            balanceCurrent = (balance["current"] as? Number)?.toDouble(),
            balanceAvailable = (balance["available"] as? Number)?.toDouble(),
            isoCurrencyCode = balance["isoCurrencyCode"] as? String,
        )
    }

    /**
     * Maps a raw account-type string to [FinancialAccountType], case-insensitively. Unrecognised or
     * missing values map to [FinancialAccountType.OTHER].
     *
     * @param raw FinancialAccount type string (e.g. "depository", "credit"), or null if absent.
     */
    fun toFinancialAccountType(raw: String?): FinancialAccountType =
        when (raw?.lowercase()) {
            "credit" -> FinancialAccountType.CREDIT
            "depository" -> FinancialAccountType.DEPOSITORY
            "investment" -> FinancialAccountType.INVESTMENT
            "loan" -> FinancialAccountType.LOAN
            else -> FinancialAccountType.OTHER
        }

    /**
     * Converts a sync-transactions response into a [TransactionDelta].
     *
     * @param response Response payload of the sync-transactions request.
     */
    fun toTransactionDelta(response: Map<String, Any?>): TransactionDelta {
        val added = response["added"].asMapList()
            .map { toTransaction(it) }
        val modified = response["modified"].asMapList()
            .map { toTransaction(it) }
        val removedIds = response["removed"].asMapList()
            .map { it["transactionId"] as String }
        return TransactionDelta(
            added = added,
            modified = modified,
            removedIds = removedIds,
            nextCursor = response["cursor"] as String,
        )
    }

    /**
     * Converts one raw transaction entry into a [Transaction].
     *
     * @param raw A single entry of the `added` or `modified` array of a sync response.
     */
    fun toTransaction(raw: Map<String, Any?>): Transaction =
        Transaction(
            transactionId = raw["transactionId"] as String,
            accountId = raw["accountId"] as String,
            amount = (raw["amount"] as Number).toDouble(),
            isoCurrencyCode = raw["isoCurrencyCode"] as? String,
            date = LocalDate.parse(raw["date"] as String),
            name = raw["name"] as String,
            merchantName = raw["merchantName"] as? String,
            pending = raw["pending"] as Boolean,
            category = raw["category"] as? String,
        )
}
