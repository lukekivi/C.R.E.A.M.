package com.lucaskivi.cream.institution

import com.google.firebase.functions.FirebaseFunctions

/**
 * Creates the production [InstitutionRemoteDataSource] implementation.
 */
fun institutionRemoteDataSource(): InstitutionRemoteDataSource =
    InstitutionRemoteDataSourceImpl(
        functions = FirebaseFunctions.getInstance(),
        transformer = InstitutionTransformer(),
    )
