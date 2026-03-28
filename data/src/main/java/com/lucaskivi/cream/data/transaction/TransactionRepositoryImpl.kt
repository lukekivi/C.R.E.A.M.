package com.lucaskivi.cream.data.transaction

import com.lucaskivi.cream.local.transaction.TransactionLocalDataSource
import com.lucaskivi.cream.remote.transaction.TransactionRemoteDataSource

/**
 * Production implementation of [TransactionRepository].
 *
 * @param remoteDataSource Source for network-fetched transaction data.
 * @param localDataSource Source for on-device cached transaction data.
 */
class TransactionRepositoryImpl(
    private val remoteDataSource: TransactionRemoteDataSource,
    private val localDataSource: TransactionLocalDataSource
) : TransactionRepository
