package com.lucaskivi.cream.local

import android.content.Context
import com.lucaskivi.cream.institution.CreamDatabase
import com.lucaskivi.cream.institution.InstitutionEntityTransformer
import com.lucaskivi.cream.institution.InstitutionLocalDataSource
import com.lucaskivi.cream.institution.InstitutionLocalDataSourceImpl

/**
 * Builds each local datasource once and shares the database they read from.
 *
 * @param context Application context used to open the database.
 */
internal class LocalDataSourcesImpl(
    context: Context,
) : LocalDataSources {

    /**
     * The process-wide database. [CreamDatabase.getInstance] opens it at most once per process,
     * however many [LocalDataSourcesImpl]s are built.
     */
    private val database: CreamDatabase = CreamDatabase.getInstance(context)

    override val institution: InstitutionLocalDataSource =
        InstitutionLocalDataSourceImpl(
            dao = database.institutionDao(),
            transformer = InstitutionEntityTransformer(),
        )
}
