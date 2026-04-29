package com.lucaskivi.cream.institution

import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.institution.model.TransactionDelta

/**
 * Contract for remote financial-institution data operations.
 */
interface InstitutionRemoteDataSource {

    /**
     * Creates a short-lived token that starts the institution-linking flow.
     *
     * @return The link token string on success.
     */
    suspend fun createLinkToken(): NetworkResult<String>

    /**
     * Completes institution linking by exchanging the one-time [publicToken] produced when the
     * user finishes the linking flow. Returns the newly linked institution.
     *
     * @param publicToken One-time token produced when linking completes.
     * @param institutionId Identifier of the institution the user selected.
     * @param institutionName Human-readable name of the institution the user selected.
     */
    suspend fun exchangePublicToken(
        publicToken: String,
        institutionId: String,
        institutionName: String,
    ): NetworkResult<LinkedInstitution>

    /**
     * Returns all institutions the current user has linked, each paired with its accounts and
     * current balances.
     */
    suspend fun listInstitutions(): NetworkResult<List<LinkedInstitutionWithAccounts>>

    /**
     * Returns transaction changes for the institution identified by [itemId] since [cursor].
     * Pass null for [cursor] to fetch every transaction.
     *
     * @param itemId Identifier of the institution to sync.
     * @param cursor Cursor returned by the previous sync, or null for the first sync.
     */
    suspend fun syncTransactions(
        itemId: String,
        cursor: String?,
    ): NetworkResult<TransactionDelta>

    /**
     * Unlinks the institution identified by [itemId]. Its accounts and transactions are no
     * longer returned afterwards.
     *
     * @param itemId Identifier of the institution to disconnect.
     */
    suspend fun disconnectInstitution(itemId: String): NetworkResult<Unit>
}
