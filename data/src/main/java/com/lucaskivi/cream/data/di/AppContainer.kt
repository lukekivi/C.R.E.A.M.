package com.lucaskivi.cream.data.di

import android.content.Context
import com.lucaskivi.cream.data.repository.auth.AuthRepository
import com.lucaskivi.cream.data.repository.auth.AuthRepositoryImpl
import com.lucaskivi.cream.data.repository.institution.InstitutionRepository
import com.lucaskivi.cream.data.repository.institution.InstitutionRepositoryImpl
import com.lucaskivi.cream.auth.authDataSource
import com.lucaskivi.cream.institution.institutionLocalDataSource
import com.lucaskivi.cream.institution.institutionRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency injection container for the app.
 *
 * Wires datasource implementations into repositories via each module's factory.
 * Held as a singleton on [AppContainerProvider] (i.e. the Application class).
 *
 * @param context Application context passed to datasources that require it.
 */
class AppContainer(
    context: Context,
) {
    /**
     * Scope for work that must outlive any one screen, such as background syncs. Lives as long as
     * the app process. A supervisor, so one failed job doesn't cancel the others.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Singleton [InstitutionRepository] for the app.
     */
    val institutionRepository: InstitutionRepository =
        InstitutionRepositoryImpl(
            remoteDataSource = institutionRemoteDataSource(),
            localDataSource = institutionLocalDataSource(context),
            externalScope = applicationScope,
        )

    /**
     * Singleton [AuthRepository] for the app.
     */
    val authRepository: AuthRepository =
        AuthRepositoryImpl(authDataSource())
}
