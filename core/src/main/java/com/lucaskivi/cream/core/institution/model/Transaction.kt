package com.lucaskivi.cream.core.institution.model

import java.time.LocalDate

/**
 * A single financial transaction on an [Account].
 *
 * Amounts follow the sign convention where positive values represent debits (money leaving the
 * account) and negative values represent credits (money entering). For example, a $10 purchase
 * has [amount] = 10.0 and a $25 refund has [amount] = -25.0.
 *
 * @param transactionId Stable identifier for this transaction. Used as the primary key in local
 *   storage and for deduplication during delta sync.
 * @param accountId Identifier of the [Account] this transaction belongs to.
 * @param amount Transaction amount in the account's currency. Positive = debit; negative = credit.
 * @param isoCurrencyCode ISO 4217 currency code (e.g. "USD"), or null if not returned.
 * @param date Date the transaction posted, or the expected post date if still pending.
 * @param name Raw transaction name as provided by the institution.
 * @param merchantName Cleaned merchant name, or null if unavailable.
 * @param pending True if the transaction has not yet settled.
 * @param category Primary category label (e.g. "Food and Drink"), or null if not returned.
 */
data class Transaction(
    val transactionId: String,
    val accountId: String,
    val amount: Double,
    val isoCurrencyCode: String?,
    val date: LocalDate,
    val name: String,
    val merchantName: String?,
    val pending: Boolean,
    val category: String?,
)
