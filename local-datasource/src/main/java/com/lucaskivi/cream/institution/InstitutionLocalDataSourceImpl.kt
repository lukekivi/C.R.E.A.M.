package com.lucaskivi.cream.institution

import android.content.Context

/**
 * Production implementation of [InstitutionLocalDataSource].
 *
 * Instantiate via [create]; the constructor is private to enforce factory usage.
 *
 * @param context Application context used to access local storage.
 */
class InstitutionLocalDataSourceImpl private constructor(
    private val context: Context,
) : InstitutionLocalDataSource {

    /**
     * Factory entry points for [InstitutionLocalDataSourceImpl].
     */
    companion object {
        /**
         * Creates an [InstitutionLocalDataSourceImpl] backed by the given [context].
         *
         * @param context Application context used to access local storage.
         */
        fun create(context: Context): InstitutionLocalDataSourceImpl =
            InstitutionLocalDataSourceImpl(context)
    }
}
