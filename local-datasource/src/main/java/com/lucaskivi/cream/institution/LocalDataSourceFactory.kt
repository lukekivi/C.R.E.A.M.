package com.lucaskivi.cream.institution

import android.content.Context

/**
 * Creates the production [InstitutionLocalDataSource] implementation.
 *
 * @param context Application context used to access local storage.
 */
fun institutionLocalDataSource(context: Context): InstitutionLocalDataSource =
    InstitutionLocalDataSourceImpl.create(context)
