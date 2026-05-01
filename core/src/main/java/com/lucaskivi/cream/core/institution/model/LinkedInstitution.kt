package com.lucaskivi.cream.core.institution.model

/**
 * A financial institution (bank, credit union, credit-card issuer, etc.) that the user has
 * linked to their account. One linked institution represents a single login at a single
 * institution and may hold multiple [FinancialAccount]s.
 *
 * @param itemId Opaque identifier assigned by the backend when the user completes the link
 *   flow. Used as the stable handle for all subsequent operations on this institution.
 * @param institutionId Provider's identifier for the institution. Stable across users.
 * @param institutionName Human-readable name of the institution (e.g. "Chase", "Capital One").
 */
data class LinkedInstitution(
    val itemId: String,
    val institutionId: String,
    val institutionName: String,
)
