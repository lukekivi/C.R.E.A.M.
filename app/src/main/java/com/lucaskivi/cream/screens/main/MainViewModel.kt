package com.lucaskivi.cream.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lucaskivi.cream.data.repository.auth.AuthRepository
import com.lucaskivi.cream.data.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Observes Firebase auth state and exposes it as [uiState].
 *
 * Held at the nav-graph level so the entire app can react to sign-in/sign-out
 * without each screen subscribing independently.
 *
 * @param authRepository Source of auth state.
 */
class MainViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    /**
     * Mutable backing flow for [uiState].
     */
    private val mutableUiState = MutableStateFlow(MainUiState())

    /**
     * Observable auth state for the root navigation graph.
     */
    val uiState: StateFlow<MainUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.authStateFlow.collect { user ->
                mutableUiState.update {
                    it.copy(
                        isInitialized = true,
                        isAuthenticated = user != null,
                    )
                }
            }
        }
    }

    /**
     * Factory entry points for [MainViewModel].
     */
    companion object {
        /**
         * Returns a [ViewModelProvider.Factory] that constructs [MainViewModel] from [appContainer].
         *
         * @param appContainer Source of the dependencies the ViewModel requires.
         */
        fun factory(appContainer: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(appContainer.authRepository) as T
        }
    }
}

/**
 * UI state for the root auth observer.
 *
 * @property isInitialized Whether the initial auth state has been received.
 * @property isAuthenticated Whether a user is currently signed in.
 */
data class MainUiState(
    val isInitialized: Boolean = false,
    val isAuthenticated: Boolean = false,
)
