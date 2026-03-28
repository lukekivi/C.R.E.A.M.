package com.lucaskivi.cream.local

import android.content.Context
import com.lucaskivi.cream.local.transaction.TransactionLocalDataSource
import com.lucaskivi.cream.local.transaction.TransactionLocalDataSourceImpl

/**
 * Creates the production [TransactionLocalDataSource] implementation.
 */
fun transactionLocalDataSource(context: Context): TransactionLocalDataSource =
    TransactionLocalDataSourceImpl(context)
