package com.lucaskivi.cream.screens.institutions

import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.ui.format.CurrencyFormatter

/**
 * Converts institution domain models into what the Institutions screen shows.
 *
 * @param currencyFormatter Formats account balances for display.
 */
class InstitutionsUiTransformer(
    private val currencyFormatter: CurrencyFormatter,
) {

    /**
     * Returns what [institutionItem] shows for [institutionWithAccounts].
     *
     * @param institutionWithAccounts A linked institution with its accounts.
     */
    fun toInstitutionItemData(
        institutionWithAccounts: LinkedInstitutionWithAccounts,
    ): InstitutionItemData {
        val institution = institutionWithAccounts.institution
        val accounts = institutionWithAccounts.accounts.map(::toAccountItemData)
        return InstitutionItemData(
            itemId = institution.itemId,
            name = institution.institutionName,
            accounts = accounts,
            institution = institution,
        )
    }

    /**
     * Returns what [AccountItem] shows for [account].
     *
     * @param account The account to show.
     */
    fun toAccountItemData(
        account: FinancialAccount,
    ): AccountItemData {
        val type = account.type.name
            .lowercase()
            .replaceFirstChar { it.titlecase() }
        val details = listOfNotNull(
            account.mask?.let { "•••• $it" },
            type,
        ).joinToString(" · ")
        val balance = currencyFormatter.format(
            account.balanceCurrent,
            account.isoCurrencyCode,
        )
        return AccountItemData(
            accountId = account.accountId,
            name = account.displayName,
            details = details,
            balance = balance,
        )
    }

    /**
     * Returns the dialog asking the user to confirm disconnecting [institution].
     *
     * @param institution The institution to disconnect once confirmed.
     */
    fun toConfirmDisconnect(
        institution: LinkedInstitution,
    ): InstitutionsDialog.ConfirmDisconnect =
        InstitutionsDialog.ConfirmDisconnect(
            institutionName = institution.institutionName,
            institution = institution,
        )

    /**
     * Returns the message saying [institution] couldn't be disconnected.
     *
     * @param institution The institution that failed to disconnect.
     */
    fun toDisconnectFailedMessage(
        institution: LinkedInstitution,
    ): String =
        "Couldn't disconnect ${institution.institutionName}. Please try again."
}
