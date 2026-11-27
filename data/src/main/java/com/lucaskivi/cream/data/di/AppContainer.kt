package com.lucaskivi.cream.data.di

import android.content.Context
import com.lucaskivi.cream.data.repository.auth.AuthRepository
import com.lucaskivi.cream.data.repository.auth.AuthRepositoryImpl
import com.lucaskivi.cream.data.repository.institution.InstitutionRepository
import com.lucaskivi.cream.data.repository.institution.InstitutionRepositoryImpl
import com.lucaskivi.cream.local.LocalDataSources
import com.lucaskivi.cream.local.localDataSources
import com.lucaskivi.cream.remote.RemoteDataSources
import com.lucaskivi.cream.remote.remoteDataSources

/**
 * Manual dependency injection container for the app.
 *
 * Builds each datasource module's container once and wires its datasources into repositories.
 * Held as a singleton on [AppContainerProvider] (i.e. the Application class).
 *
 * @param context Application context passed to datasources that require it.
 */
class AppContainer(
    context: Context,
) {
    /**
     * The app's remote datasources, shared by every repository that needs one.
     */
    private val remoteDataSources: RemoteDataSources = remoteDataSources()

    /**
     * The app's on-device datasources, shared by every repository that needs one.
     */
    private val localDataSources: LocalDataSources = localDataSources(context)

    /**
     * Singleton [InstitutionRepository] for the app.
     */
    val institutionRepository: InstitutionRepository =
        InstitutionRepositoryImpl(
            remoteDataSources.institution,
            localDataSources.institution,
        )

    /**
     * Singleton [AuthRepository] for the app.
     */
    val authRepository: AuthRepository =
        AuthRepositoryImpl(remoteDataSources.auth)
}
