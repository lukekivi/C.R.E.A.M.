package com.lucaskivi.cream.institution.model

import com.lucaskivi.cream.core.institution.model.Transaction

/**
 * The set of transaction changes since the last sync. Contains new, updated, and removed
 * transactions along with an opaque cursor for the next sync call.
 *
 * @param added Transactions that are new since the last sync.
 * @param modified Transactions whose fields changed since the last sync.
 * @param removedIds IDs of transactions that were removed or voided since the last sync.
 * @param nextCursor Opaque cursor to pass on the next sync call to fetch only subsequent changes.
 */
data class TransactionDelta(
    val added: List<Transaction>,
    val modified: List<Transaction>,
    val removedIds: List<String>,
    val nextCursor: String,
)
