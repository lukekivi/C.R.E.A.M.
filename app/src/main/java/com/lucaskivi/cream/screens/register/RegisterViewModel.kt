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

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun createAccount(
        email: String,
        password: String,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.createUserWithEmail(email, password)
                .onSuccess { _uiState.update { it.copy(isLoading = false) } }
                .onFailure { cause -> _uiState.update { it.copy(isLoading = false, error = cause.message) } }
        }
    }

    companion object {
        /**
         * Returns a [ViewModelProvider.Factory] that constructs [RegisterViewModel] from [appContainer].
         */
        fun factory(appContainer: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                RegisterViewModel(appContainer.authRepository) as T
        }
    }
}

/**
 * UI state for the registration screen.
 *
 * @property isLoading Whether an account-creation request is in flight.
 * @property error Human-readable error message from the last failed attempt, or null.
 */
data class RegisterUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)
