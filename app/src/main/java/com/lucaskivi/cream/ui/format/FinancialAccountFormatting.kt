package com.lucaskivi.cream.ui.format

import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.FinancialAccountType

/**
 * Returns the account's type label, preceded by its masked number when known
 * (e.g. "•••• 1234 · Depository").
 */
internal fun FinancialAccount.formattedDetails(): String {
    val maskedNumber = this.mask?.let { "•••• $it" }
    return listOfNotNull(
        maskedNumber,
        this.type.toLabel(),
    ).joinToString(" · ")
}

/**
 * Returns a human-readable label for the account type (e.g. "Depository").
 */
private fun FinancialAccountType.toLabel(): String =
    this.name
        .lowercase()
        .replaceFirstChar { it.titlecase() }
