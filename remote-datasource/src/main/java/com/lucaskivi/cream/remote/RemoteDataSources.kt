package com.lucaskivi.cream.remote

import com.lucaskivi.cream.auth.AuthDataSource
import com.lucaskivi.cream.institution.InstitutionRemoteDataSource

/**
 * The remote datasources for the app. Build one per process with [remoteDataSources] and share
 * its datasources with every consumer.
 */
interface RemoteDataSources {
    /**
     * Remote authentication operations.
     */
    val auth: AuthDataSource

    /**
     * Remote institution, account, and transaction operations.
     */
    val institution: InstitutionRemoteDataSource
}

/**
 * Creates the [RemoteDataSources] for the current build type.
 */
fun remoteDataSources(): RemoteDataSources =
    RemoteDataSourcesImpl()
