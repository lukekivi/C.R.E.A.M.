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

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun signInWithEmail(
        email: String,
        password: String,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.signInWithEmail(email, password)
                .onSuccess { _uiState.update { it.copy(isLoading = false) } }
                .onFailure { cause -> _uiState.update { it.copy(isLoading = false, error = cause.message) } }
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
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
                authRepository.signInWithGoogle(idToken)
                    .onSuccess { _uiState.update { it.copy(isLoading = false) } }
                    .onFailure { cause -> _uiState.update { it.copy(isLoading = false, error = cause.message) } }
            } catch (e: GetCredentialException) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    companion object {
        /**
         * Returns a [ViewModelProvider.Factory] that constructs [LoginViewModel] from [appContainer].
         */
        fun factory(appContainer: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LoginViewModel(appContainer.authRepository) as T
        }
    }
}

/**
 * UI state for the login screen.
 *
 * @property isLoading Whether a sign-in request is in flight.
 * @property error Human-readable error message from the last failed attempt, or null.
 */
data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)
