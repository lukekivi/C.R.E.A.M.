package com.lucaskivi.cream.screens.login

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.lucaskivi.cream.R
import com.lucaskivi.cream.core.onFailure
import com.lucaskivi.cream.core.onSuccess
import com.lucaskivi.cream.data.repository.auth.AuthRepository
import com.lucaskivi.cream.data.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Handles sign-in via email/password and Google Credential Manager.
 *
 * @param authRepository Performs the underlying auth operations.
 */
class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    /**
     * Mutable backing flow for [uiState].
     */
    private val mutableUiState = MutableStateFlow(LoginUiState())

    /**
     * Observable UI state of the login screen.
     */
    val uiState: StateFlow<LoginUiState> = mutableUiState.asStateFlow()

    /**
     * Attempts to sign in with [email] and [password], updating [uiState] with the result.
     *
     * Sets `isLoading` while the request is in flight and surfaces any failure message
     * in `errorMessage` on completion.
     *
     * @param email Email address of an existing account.
     * @param password Password for that account.
     */
    fun signInWithEmail(
        email: String,
        password: String,
    ) {
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }
            authRepository
                .signInWithEmail(email, password)
                .onSuccess { handleSignedIn() }
                .onFailure(::handleFailure)
        }
    }

    /**
     * Initiates Google Sign-In, prompting the user to select a Google account and
     * exchanging the resulting credential for an authenticated session.
     *
     * [uiState] reflects the in-flight request and surfaces any failure message
     * from the credential picker or sign-in step.
     *
     * @param context Context used to launch the credential picker.
     */
    fun signInWithGoogle(
        context: Context,
    ) {
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }
            try {
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(context.getString(R.string.google_web_client_id))
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val result = credentialManager.getCredential(context, request)
                val idToken = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
                authRepository
                    .signInWithGoogle(idToken)
                    .onSuccess { handleSignedIn() }
                    .onFailure(::handleFailure)
            } catch (e: GetCredentialException) {
                handleFailure(e)
            }
        }
    }

    /**
     * Ends the sign-in request after it succeeds.
     */
    private fun handleSignedIn() {
        mutableUiState.update {
            it.copy(
                isLoading = false,
            )
        }
    }

    /**
     * Ends the sign-in request and shows why it failed.
     *
     * @param cause Why the request failed.
     */
    private fun handleFailure(
        cause: Throwable,
    ) {
        val errorMessage = cause.message ?: SIGN_IN_FAILED_MESSAGE
        mutableUiState.update {
            it.copy(
                isLoading = false,
                errorMessage = errorMessage,
            )
        }
    }

    companion object {
        /**
         * Message shown when a sign-in failure carries no message of its own.
         */
        private const val SIGN_IN_FAILED_MESSAGE = "Couldn't sign in. Please try again."

        /**
         * Returns a [ViewModelProvider.Factory] that constructs [LoginViewModel] from [appContainer].
         *
         * @param appContainer Source of the dependencies the ViewModel requires.
         */
        fun factory(
            appContainer: AppContainer,
        ) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                ): T =
                    LoginViewModel(appContainer.authRepository) as T
            }
    }
}

/**
 * UI state for the login screen.
 *
 * @property isLoading Whether a sign-in request is in flight.
 * @property errorMessage Why the last attempt failed, or null.
 */
data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
