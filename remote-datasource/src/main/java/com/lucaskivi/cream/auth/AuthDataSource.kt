package com.lucaskivi.cream.auth

import com.lucaskivi.cream.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Contract for remote authentication operations backed by Firebase Auth.
 *
 * Throws on failure — callers are expected to wrap invocations in [runCatching].
 */
interface AuthDataSource {
    /**
     * The currently signed-in [User], or null if no session is active.
     */
    val currentUser: User?

    /**
     * Emits the signed-in [User] whenever auth state changes, or null when signed out.
     */
    val authStateFlow: Flow<User?>

    /**
     * Signs in with [email] and [password]. Throws on invalid credentials.
     */
    suspend fun signInWithEmail(
        email: String,
        password: String,
    ): User

    /**
     * Creates a new account with [email] and [password]. Throws if the address is already
     * in use or the password does not meet Firebase's strength requirements.
     */
    suspend fun createUserWithEmail(
        email: String,
        password: String,
    ): User

    /**
     * Signs in using a Google [idToken] obtained via Credential Manager.
     */
    suspend fun signInWithGoogle(idToken: String): User

    /**
     * Signs the current user out and clears the local Firebase session.
     */
    fun signOut()
}
