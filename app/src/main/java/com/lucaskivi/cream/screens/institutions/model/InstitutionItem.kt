package com.lucaskivi.cream.screens.institutions.model

import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.core.institution.model.TransactionSyncState

/**
 * One linked institution as shown on the Institutions screen.
 *
 * @param institution The institution and its accounts.
 * @param syncState Transaction sync state of the institution.
 */
data class InstitutionItem(
    val institution: LinkedInstitutionWithAccounts,
    val syncState: TransactionSyncState,
)
