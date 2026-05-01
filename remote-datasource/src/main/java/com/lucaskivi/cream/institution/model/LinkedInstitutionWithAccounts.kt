package com.lucaskivi.cream.institution.model

import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.LinkedInstitution

/**
 * A linked institution together with all the accounts that belong to it.
 *
 * @param institution The linked institution.
 * @param accounts All accounts belonging to [institution].
 */
data class LinkedInstitutionWithAccounts(
    val institution: LinkedInstitution,
    val accounts: List<FinancialAccount>,
)
