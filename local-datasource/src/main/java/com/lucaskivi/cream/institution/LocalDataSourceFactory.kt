package com.lucaskivi.cream.institution

import android.content.Context

/**
 * Creates the production [InstitutionLocalDataSource] implementation backed by the local Room
 * database.
 *
 * @param context Application context used to open the database file.
 */
fun institutionLocalDataSource(context: Context): InstitutionLocalDataSource {
    val dao = CreamDatabase
        .getInstance(context)
        .institutionDao()
    return InstitutionLocalDataSourceImpl(
        dao = dao,
        transformer = InstitutionEntityTransformer(),
    )
}
