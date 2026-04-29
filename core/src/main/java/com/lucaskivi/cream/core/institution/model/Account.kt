package com.lucaskivi.cream.core.institution.model

/**
 * A single financial account (checking, savings, credit card, etc.) belonging to a linked
 * institution. One [LinkedInstitution] may have several accounts.
 *
 * Balance fields are nullable because not all account types report them.
 *
 * @param accountId Stable identifier for this account. Used as the primary key in local
 *   storage and to associate transactions.
 * @param itemId Identifier of the [LinkedInstitution] that owns this account.
 * @param displayName Account name as provided by the institution (e.g. "Total Checking").
 * @param mask Last four digits of the account number, or null if unavailable.
 * @param type High-level account classification.
 * @param subtype Provider sub-type string (e.g. "checking", "savings", "credit card"), or null
 *   if not returned.
 * @param balanceCurrent Current ledger balance, or null if unavailable.
 * @param balanceAvailable Available balance after pending transactions, or null if unavailable.
 * @param isoCurrencyCode ISO 4217 currency code for the balance values (e.g. "USD"), or null if
 *   not returned.
 */
data class Account(
    val accountId: String,
    val itemId: String,
    val displayName: String,
    val mask: String?,
    val type: AccountType,
    val subtype: String?,
    val balanceCurrent: Double?,
    val balanceAvailable: Double?,
    val isoCurrencyCode: String?,
)
