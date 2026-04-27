package com.lucaskivi.cream.transaction

import android.content.Context

/**
 * Production implementation of [TransactionLocalDataSource].
 *
 * @param context Application context used to access local storage.
 */
class TransactionLocalDataSourceImpl(private val context: Context) : TransactionLocalDataSource
