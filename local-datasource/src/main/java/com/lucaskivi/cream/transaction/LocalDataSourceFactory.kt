package com.lucaskivi.cream.transaction

import android.content.Context

/**
 * Creates the production [TransactionLocalDataSource] implementation.
 */
fun transactionLocalDataSource(context: Context): TransactionLocalDataSource =
    TransactionLocalDataSourceImpl(context)
