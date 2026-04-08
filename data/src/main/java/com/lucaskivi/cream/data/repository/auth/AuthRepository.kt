package com.lucaskivi.cream.data.repository.auth

import com.lucaskivi.cream.data.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Business-logic contract for authentication operations.
 *
 * Wraps each mutating operation in [Result] so callers can handle failures without
 * catching exceptions directly.
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
     * Signs in with [email] and [password]. Returns [Result.failure] on invalid credentials.
     */
    suspend fun signInWithEmail(
        email: String,
        password: String,
    ): Result<User>

    /**
     * Creates a new account with [email] and [password]. Returns [Result.failure] if the
     * address is already in use or the password is too weak.
     */
    suspend fun createUserWithEmail(
        email: String,
        password: String,
    ): Result<User>

    /**
     * Signs in using a Google ID token obtained via Credential Manager.
     */
    suspend fun signInWithGoogle(idToken: String): Result<User>

    /**
     * Signs the current user out and clears the local session.
     */
    fun signOut()
}
