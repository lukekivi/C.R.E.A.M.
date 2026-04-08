package com.lucaskivi.cream.transaction

/**
 * Creates the production [TransactionRemoteDataSource] implementation.
 */
fun transactionRemoteDataSource(): TransactionRemoteDataSource = TransactionRemoteDataSourceImpl()
