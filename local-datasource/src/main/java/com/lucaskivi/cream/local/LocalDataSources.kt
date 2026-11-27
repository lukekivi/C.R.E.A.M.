package com.lucaskivi.cream.local

import android.content.Context
import com.lucaskivi.cream.institution.InstitutionLocalDataSource

/**
 * The on-device datasources for the app. Build one per process with [localDataSources] and share
 * its datasources with every consumer.
 */
interface LocalDataSources {
    /**
     * Cached institutions, accounts, and transactions.
     */
    val institution: InstitutionLocalDataSource
}

/**
 * Creates the [LocalDataSources] backed by on-device storage.
 *
 * @param context Application context used to open storage.
 */
fun localDataSources(
    context: Context,
): LocalDataSources =
    LocalDataSourcesImpl(context)
