package com.lucaskivi.cream.link.model

/**
 * Outcome of linking an institution, free of link-provider SDK types so ViewModels can
 * consume it without depending on the SDK.
 */
sealed interface InstitutionLinkResult {

    /**
     * The user linked an institution.
     *
     * @param publicToken Short-lived token to exchange for a long-lived access token.
     * @param institutionId Plaid's identifier for the linked institution.
     * @param institutionName Display name of the linked institution.
     */
    data class Success(
        val publicToken: String,
        val institutionId: String,
        val institutionName: String,
    ) : InstitutionLinkResult

    /**
     * The user left Link without linking an institution and without an error.
     */
    data object Cancelled : InstitutionLinkResult

    /**
     * Link ended without a usable result.
     *
     * @param message Human-readable description of what went wrong.
     */
    data class Error(
        val message: String,
    ) : InstitutionLinkResult
}
