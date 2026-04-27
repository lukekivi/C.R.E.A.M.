package com.lucaskivi.cream.data.repository.auth

import com.lucaskivi.cream.auth.AuthDataSource
import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.map
import com.lucaskivi.cream.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Production implementation of [AuthRepository].
 *
 * Delegates to [authDataSource] and maps datasource [User]s into the data-layer domain type.
 *
 * @param authDataSource Underlying Firebase auth datasource.
 */
class AuthRepositoryImpl(
    private val authDataSource: AuthDataSource,
) : AuthRepository {

    override val currentUser: User?
        get() = authDataSource.currentUser?.toDomainUser()

    override val authStateFlow: Flow<User?> = authDataSource.authStateFlow.map { it?.toDomainUser() }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): NetworkResult<User> = authDataSource.signInWithEmail(email, password).map { it.toDomainUser() }

    override suspend fun createUserWithEmail(
        email: String,
        password: String,
    ): NetworkResult<User> = authDataSource.createUserWithEmail(email, password).map { it.toDomainUser() }

    override suspend fun signInWithGoogle(idToken: String): NetworkResult<User> =
        authDataSource.signInWithGoogle(idToken).map { it.toDomainUser() }

    override fun signOut() = authDataSource.signOut()
}

/**
 * Maps a datasource-layer [User][com.lucaskivi.cream.model.User] into the data-layer domain [User].
 */
private fun com.lucaskivi.cream.model.User.toDomainUser() = User(
    uid = uid,
    email = email,
    displayName = displayName,
)
