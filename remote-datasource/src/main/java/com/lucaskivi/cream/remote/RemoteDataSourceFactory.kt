package com.lucaskivi.cream.remote

import com.lucaskivi.cream.remote.transaction.TransactionRemoteDataSource
import com.lucaskivi.cream.remote.transaction.TransactionRemoteDataSourceImpl

/**
 * Creates the production [TransactionRemoteDataSource] implementation.
 */
fun transactionRemoteDataSource(): TransactionRemoteDataSource = TransactionRemoteDataSourceImpl()
