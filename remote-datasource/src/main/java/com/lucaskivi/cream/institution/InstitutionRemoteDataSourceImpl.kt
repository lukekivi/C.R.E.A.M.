package com.lucaskivi.cream.institution

/**
 * Production implementation of [InstitutionRemoteDataSource].
 *
 * Instantiate via [create]; the constructor is private to enforce factory usage.
 */
class InstitutionRemoteDataSourceImpl private constructor() : InstitutionRemoteDataSource {

    /**
     * Factory entry points for [InstitutionRemoteDataSourceImpl].
     */
    companion object {
        /**
         * Creates an [InstitutionRemoteDataSourceImpl].
         */
        fun create(): InstitutionRemoteDataSourceImpl =
            InstitutionRemoteDataSourceImpl()
    }
}
