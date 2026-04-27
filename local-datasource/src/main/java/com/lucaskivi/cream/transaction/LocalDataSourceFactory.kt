package com.lucaskivi.cream.transaction

import android.content.Context

/**
 * Creates the production [TransactionLocalDataSource] implementation.
 *
 * @param context Application context used to access local storage.
 */
fun transactionLocalDataSource(context: Context): TransactionLocalDataSource =
    TransactionLocalDataSourceImpl(context)
