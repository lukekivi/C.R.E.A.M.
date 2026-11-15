package com.lucaskivi.cream.screens.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
 * Handles new account creation via email and password.
 *
 * @param authRepository Performs the underlying auth operations.
 */
class RegisterViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    /**
     * Mutable backing flow for [uiState].
     */
    private val mutableUiState = MutableStateFlow(RegisterUiState())

    /**
     * Observable UI state of the registration screen.
     */
    val uiState: StateFlow<RegisterUiState> = mutableUiState.asStateFlow()

    /**
     * Attempts to create a new account with [email] and [password], updating [uiState] with the result.
     *
     * Sets `isLoading` while the request is in flight and surfaces any failure message
     * in `errorMessage` on completion.
     *
     * @param email Email address to register.
     * @param password Password for the new account.
     */
    fun createAccount(
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
                .createUserWithEmail(
                    email,
                    password,
                )
                .onSuccess { handleAccountCreated() }
                .onFailure(::handleFailure)
        }
    }

    /**
     * Ends the account-creation request after it succeeds.
     */
    private fun handleAccountCreated() {
        mutableUiState.update {
            it.copy(
                isLoading = false,
            )
        }
    }

    /**
     * Ends the account-creation request and shows why it failed.
     *
     * @param cause Why the request failed.
     */
    private fun handleFailure(
        cause: Throwable,
    ) {
        val errorMessage = cause.message ?: CREATE_ACCOUNT_FAILED_MESSAGE
        mutableUiState.update {
            it.copy(
                isLoading = false,
                errorMessage = errorMessage,
            )
        }
    }

    companion object {
        /**
         * Message shown when an account-creation failure carries no message of its own.
         */
        private const val CREATE_ACCOUNT_FAILED_MESSAGE = "Couldn't create your account. Please try again."

        /**
         * Returns a [ViewModelProvider.Factory] that constructs [RegisterViewModel] from [appContainer].
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
                    RegisterViewModel(appContainer.authRepository) as T
            }
    }
}

/**
 * UI state for the registration screen.
 *
 * @property isLoading Whether an account-creation request is in flight.
 * @property errorMessage Why the last attempt failed, or null.
 */
data class RegisterUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
