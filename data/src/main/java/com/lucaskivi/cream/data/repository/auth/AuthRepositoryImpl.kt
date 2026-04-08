package com.lucaskivi.cream.data.repository.auth

import com.lucaskivi.cream.data.model.User
import com.lucaskivi.cream.auth.AuthDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Production implementation of [AuthRepository].
 *
 * @param authDataSource Underlying Firebase auth datasource.
 */
class AuthRepositoryImpl(private val authDataSource: AuthDataSource) : AuthRepository {

    override val currentUser: User?
        get() = authDataSource.currentUser?.toDomainUser()

    override val authStateFlow: Flow<User?> = authDataSource.authStateFlow.map { it?.toDomainUser() }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): Result<User> =
        runCatching { authDataSource.signInWithEmail(email, password).toDomainUser() }

    override suspend fun createUserWithEmail(
        email: String,
        password: String,
    ): Result<User> =
        runCatching { authDataSource.createUserWithEmail(email, password).toDomainUser() }

    override suspend fun signInWithGoogle(idToken: String): Result<User> =
        runCatching { authDataSource.signInWithGoogle(idToken).toDomainUser() }

    override fun signOut() = authDataSource.signOut()
}

private fun com.lucaskivi.cream.model.User.toDomainUser() = User(
    uid = uid,
    email = email,
    displayName = displayName
)
