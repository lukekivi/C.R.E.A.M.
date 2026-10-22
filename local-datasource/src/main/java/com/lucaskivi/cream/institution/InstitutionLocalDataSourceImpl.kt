package com.lucaskivi.cream.institution

import android.content.Context

/**
 * Production implementation of [InstitutionLocalDataSource].
 *
 * @param context Application context used to access local storage.
 */
class InstitutionLocalDataSourceImpl internal constructor(
    private val context: Context,
) : InstitutionLocalDataSource
