package com.lucaskivi.cream.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lucaskivi.cream.data.di.AppContainer
import com.lucaskivi.cream.data.model.User
import com.lucaskivi.cream.data.repository.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the home screen.
 *
 * Tracks the currently signed-in user so the UI can render a welcome/profile
 * section, and exposes [signOut] to terminate the session.
 *
 * @param authRepository Source of the signed-in user and the sign-out action.
 */
class HomeViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    /**
     * Mutable backing flow for [uiState].
     */
    private val mutableUiState = MutableStateFlow(HomeUiState())

    /**
     * Observable UI state of the home screen.
     */
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.authStateFlow.collect { user ->
                mutableUiState.update { it.copy(currentUser = user) }
            }
        }
    }

    /**
     * Signs the current user out, clearing the active session.
     */
    fun signOut() =
        authRepository.signOut()

    /**
     * Factory entry points for [HomeViewModel].
     */
    companion object {
        /**
         * Returns a [ViewModelProvider.Factory] that constructs [HomeViewModel] from [appContainer].
         *
         * @param appContainer Source of the dependencies the ViewModel requires.
         */
        fun factory(appContainer: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(appContainer.authRepository) as T
        }
    }
}

/**
 * UI state for the home screen.
 *
 * @property currentUser The signed-in user, or null while auth state is initializing or after sign-out.
 */
data class HomeUiState(
    val currentUser: User? = null,
)
