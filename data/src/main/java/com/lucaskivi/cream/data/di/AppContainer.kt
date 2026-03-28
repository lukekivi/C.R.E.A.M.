package com.lucaskivi.cream.data.di

import android.content.Context
import com.lucaskivi.cream.data.transaction.TransactionRepository
import com.lucaskivi.cream.data.transaction.TransactionRepositoryImpl
import com.lucaskivi.cream.local.transactionLocalDataSource
import com.lucaskivi.cream.remote.transactionRemoteDataSource

/**
 * Manual dependency injection container for the app.
 *
 * Wires datasource implementations into repositories via each module's factory.
 * Held as a singleton on [AppContainerProvider] (i.e. the Application class).
 *
 * @param context Application context passed to datasources that require it.
 */
class AppContainer(context: Context) {
    val transactionRepository: TransactionRepository =
        TransactionRepositoryImpl(transactionRemoteDataSource(), transactionLocalDataSource(context))
}
