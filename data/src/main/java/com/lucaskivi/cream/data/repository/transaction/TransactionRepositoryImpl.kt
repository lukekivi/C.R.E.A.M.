package com.lucaskivi.cream.data.repository.transaction

import com.lucaskivi.cream.transaction.TransactionLocalDataSource
import com.lucaskivi.cream.transaction.TransactionRemoteDataSource

/**
 * Production implementation of [TransactionRepository].
 *
 * @param remoteDataSource Source for network-fetched transaction data.
 * @param localDataSource Source for on-device cached transaction data.
 */
class TransactionRepositoryImpl(
    private val remoteDataSource: TransactionRemoteDataSource,
    private val localDataSource: TransactionLocalDataSource,
) : TransactionRepository
