package com.lucaskivi.cream.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.model.User
import kotlinx.coroutines.CancellationException
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
    private val firebaseAuth: FirebaseAuth,
) : AuthDataSource {

    /**
     * Factory entry points for [AuthDataSourceImpl].
     */
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
    ): NetworkResult<User> = catching {
        firebaseAuth.signInWithEmailAndPassword(email, password).await().user!!.toDomainUser()
    }

    override suspend fun createUserWithEmail(
        email: String,
        password: String,
    ): NetworkResult<User> = catching {
        firebaseAuth.createUserWithEmailAndPassword(email, password).await().user!!.toDomainUser()
    }

    override suspend fun signInWithGoogle(idToken: String): NetworkResult<User> = catching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential).await().user!!.toDomainUser()
    }

    override fun signOut() = firebaseAuth.signOut()

    /**
     * Maps a [FirebaseUser] into the cross-module [User] domain type.
     */
    private fun FirebaseUser.toDomainUser() = User(
        uid = uid,
        email = email,
        displayName = displayName,
    )
}

/**
 * Runs [block] and converts any thrown exception into [NetworkResult.Failure].
 *
 * [CancellationException] is rethrown so structured-concurrency cancellation is preserved.
 *
 * @param block Suspending operation whose result becomes [NetworkResult.Success].
 * @return [NetworkResult.Success] with the block's value, or [NetworkResult.Failure] on any other throwable.
 */
private inline fun <T> catching(block: () -> T): NetworkResult<T> = try {
    NetworkResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    NetworkResult.Failure(e)
}
