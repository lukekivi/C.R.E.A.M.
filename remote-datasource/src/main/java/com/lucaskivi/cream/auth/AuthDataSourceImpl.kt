package com.lucaskivi.cream.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.catching
import com.lucaskivi.cream.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase Auth implementation of [AuthDataSource].
 *
 * Instantiate via [authDataSource]; the constructor is internal to prevent use outside
 * this module.
 *
 * @param firebaseAuth Underlying Firebase Auth instance.
 */
class AuthDataSourceImpl internal constructor(
    private val firebaseAuth: FirebaseAuth,
) : AuthDataSource {

    override val currentUser: User?
        get() = firebaseAuth.currentUser?.toDomainUser()

    override val authStateFlow: Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toDomainUser())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): NetworkResult<User> =
        catching {
            firebaseAuth
                .signInWithEmailAndPassword(email, password)
                .await()
                .user!!
                .toDomainUser()
        }

    override suspend fun createUserWithEmail(
        email: String,
        password: String,
    ): NetworkResult<User> =
        catching {
            firebaseAuth
                .createUserWithEmailAndPassword(email, password)
                .await()
                .user!!
                .toDomainUser()
        }

    override suspend fun signInWithGoogle(idToken: String): NetworkResult<User> =
        catching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth
                .signInWithCredential(credential)
                .await()
                .user!!
                .toDomainUser()
        }

    override fun signOut() =
        firebaseAuth.signOut()

    /**
     * Maps a [FirebaseUser] into the cross-module [User] domain type.
     */
    private fun FirebaseUser.toDomainUser() =
        User(
            uid = uid,
            email = email,
            displayName = displayName,
        )
}

