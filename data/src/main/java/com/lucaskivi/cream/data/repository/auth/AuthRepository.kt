package com.lucaskivi.cream.data.repository.auth

import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.data.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Business-logic contract for authentication operations.
 *
 * Mutating operations return a [NetworkResult] so callers can react to success or failure
 * without catching exceptions directly.
 */
interface AuthRepository {
    /**
     * The currently signed-in user, or null if no session is active.
     */
    val currentUser: User?

    /**
     * Emits the signed-in [User] whenever auth state changes, or null when signed out.
     */
    val authStateFlow: Flow<User?>

    /**
     * Signs in with [email] and [password].
     *
     * @param email Email address of an existing account.
     * @param password Password for that account.
     * @return [NetworkResult.Success] with the signed-in [User], or [NetworkResult.Failure] on any error.
     */
    suspend fun signInWithEmail(
        email: String,
        password: String,
    ): NetworkResult<User>

    /**
     * Creates a new account with [email] and [password].
     *
     * @param email Email address to register.
     * @param password Password for the new account.
     * @return [NetworkResult.Success] with the new [User], or [NetworkResult.Failure] on any error.
     */
    suspend fun createUserWithEmail(
        email: String,
        password: String,
    ): NetworkResult<User>

    /**
     * Signs in using a Google ID token obtained via Credential Manager.
     *
     * @param idToken Google ID token to exchange with Firebase.
     * @return [NetworkResult.Success] with the signed-in [User], or [NetworkResult.Failure] on any error.
     */
    suspend fun signInWithGoogle(idToken: String): NetworkResult<User>

    /**
     * Signs the current user out and clears the local session.
     */
    fun signOut()
}
