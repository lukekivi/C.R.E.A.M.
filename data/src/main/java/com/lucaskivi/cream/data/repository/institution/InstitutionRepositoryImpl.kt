package com.lucaskivi.cream.data.repository.institution

import com.lucaskivi.cream.institution.InstitutionLocalDataSource
import com.lucaskivi.cream.institution.InstitutionRemoteDataSource

/**
 * Production implementation of [InstitutionRepository].
 *
 * @param remoteDataSource Source for network-fetched institution data.
 * @param localDataSource Source for on-device cached institution data.
 */
class InstitutionRepositoryImpl(
    private val remoteDataSource: InstitutionRemoteDataSource,
    private val localDataSource: InstitutionLocalDataSource,
) : InstitutionRepository
