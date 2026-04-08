package com.lucaskivi.cream.data.di

import android.content.Context
import com.lucaskivi.cream.data.repository.auth.AuthRepository
import com.lucaskivi.cream.data.repository.auth.AuthRepositoryImpl
import com.lucaskivi.cream.data.repository.transaction.TransactionRepository
import com.lucaskivi.cream.data.repository.transaction.TransactionRepositoryImpl
import com.lucaskivi.cream.auth.authDataSource
import com.lucaskivi.cream.transaction.transactionLocalDataSource
import com.lucaskivi.cream.transaction.transactionRemoteDataSource

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
    val authRepository: AuthRepository =
        AuthRepositoryImpl(authDataSource())
}
