package com.lucaskivi.cream.core.institution.model

/**
 * Transaction sync status of a single [LinkedInstitution].
 */
sealed interface TransactionSyncState {

    /**
     * No sync is running, and the last one (if any) succeeded.
     */
    data object Idle : TransactionSyncState

    /**
     * At least one sync is in flight.
     */
    data object Syncing : TransactionSyncState

    /**
     * No sync is running, and the last one failed.
     *
     * @param cause Why the last sync failed.
     */
    data class Failed(
        val cause: Throwable,
    ) : TransactionSyncState
}
