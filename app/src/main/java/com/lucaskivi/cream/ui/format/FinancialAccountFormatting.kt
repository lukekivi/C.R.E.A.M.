package com.lucaskivi.cream.ui.format

import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.FinancialAccountType

/**
 * Returns a human-readable label for the account type (e.g. "Depository").
 */
internal fun FinancialAccountType.toLabel(): String =
    this.name
        .lowercase()
        .replaceFirstChar { it.titlecase() }

/**
 * Returns the account's type label, preceded by its masked number when known
 * (e.g. "•••• 1234 · Depository").
 */
internal fun FinancialAccount.formattedDetails(): String =
    listOfNotNull(this.mask?.let { "•••• $it" }, this.type.toLabel()).joinToString(" · ")

/**
 * Returns the current balance formatted in the account's currency, or
 * [MISSING_AMOUNT_PLACEHOLDER] when the balance is unknown.
 */
internal fun FinancialAccount.formattedBalance(): String =
    this.balanceCurrent
        ?.let { balance -> formatCurrency(balance, this.isoCurrencyCode) }
        ?: MISSING_AMOUNT_PLACEHOLDER
