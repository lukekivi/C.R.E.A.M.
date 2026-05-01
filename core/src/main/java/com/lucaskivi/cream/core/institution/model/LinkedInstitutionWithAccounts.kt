package com.lucaskivi.cream.core.institution.model

/**
 * A linked institution together with all the financial accounts that belong to it.
 *
 * @param institution The linked institution.
 * @param accounts All financial accounts belonging to [institution].
 */
data class LinkedInstitutionWithAccounts(
    val institution: LinkedInstitution,
    val accounts: List<FinancialAccount>,
)
