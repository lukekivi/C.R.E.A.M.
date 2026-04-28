package com.lucaskivi.cream.institution

/**
 * Creates the production [InstitutionRemoteDataSource] implementation.
 */
fun institutionRemoteDataSource(): InstitutionRemoteDataSource =
    InstitutionRemoteDataSourceImpl.create()
