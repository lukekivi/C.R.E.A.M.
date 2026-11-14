package com.lucaskivi.cream.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lucaskivi.cream.data.di.AppContainer
import com.lucaskivi.cream.data.model.User
import com.lucaskivi.cream.data.repository.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

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
    private val mutableUiState = MutableStateFlow<MainUiState>(MainUiState.Loading)

    /**
     * Observable auth state for the root navigation graph.
     */
    val uiState: StateFlow<MainUiState> = mutableUiState.asStateFlow()

    init {
        authRepository
            .authStateFlow
            .onEach(::handleUser)
            .launchIn(viewModelScope)
    }

    /**
     * Moves [uiState] to [MainUiState.SignedIn] when [user] is present, otherwise
     * [MainUiState.SignedOut].
     *
     * @param user The signed-in user, or null when signed out.
     */
    private fun handleUser(
        user: User?,
    ) {
        val nextState = if (user == null) MainUiState.SignedOut else MainUiState.SignedIn
        mutableUiState.update { nextState }
    }

    companion object {
        /**
         * Returns a [ViewModelProvider.Factory] that constructs [MainViewModel] from [appContainer].
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
                    MainViewModel(appContainer.authRepository) as T
            }
    }
}

/**
 * UI state for the root auth observer.
 */
sealed interface MainUiState {

    /**
     * The initial auth state hasn't been received yet.
     */
    data object Loading : MainUiState

    /**
     * No user is signed in.
     */
    data object SignedOut : MainUiState

    /**
     * A user is signed in.
     */
    data object SignedIn : MainUiState
}
