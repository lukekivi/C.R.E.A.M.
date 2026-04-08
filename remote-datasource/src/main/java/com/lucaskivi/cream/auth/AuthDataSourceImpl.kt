package com.lucaskivi.cream.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.lucaskivi.cream.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase Auth implementation of [AuthDataSource].
 *
 * Instantiate via [create]; the constructor is private to enforce factory usage.
 *
 * @param firebaseAuth Underlying Firebase Auth instance.
 */
class AuthDataSourceImpl private constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthDataSource {

    companion object {
        /**
         * Creates an [AuthDataSourceImpl] backed by the default [FirebaseAuth] instance.
         */
        fun create(): AuthDataSourceImpl = AuthDataSourceImpl(FirebaseAuth.getInstance())
    }

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
    ): User {
        val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        return result.user!!.toDomainUser()
    }

    override suspend fun createUserWithEmail(
        email: String,
        password: String,
    ): User {
        val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        return result.user!!.toDomainUser()
    }

    override suspend fun signInWithGoogle(idToken: String): User {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = firebaseAuth.signInWithCredential(credential).await()
        return result.user!!.toDomainUser()
    }

    override fun signOut() = firebaseAuth.signOut()

    private fun FirebaseUser.toDomainUser() = User(
        uid = uid,
        email = email,
        displayName = displayName
    )
}
