package com.lucaskivi.cream.screens.fixture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lucaskivi.cream.data.di.AppContainer
import com.lucaskivi.cream.data.repository.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives the fixture screen.
 *
 * @param authRepository Source of the signed-in user.
 */
class FixtureViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    /**
     * Mutable holder behind [uiState].
     */
    private val mutableUiState = MutableStateFlow(FixtureUiState())

    /**
     * Current UI state.
     */
    val uiState: StateFlow<FixtureUiState> = mutableUiState.asStateFlow()

    /**
     * Joins [first] and [second] into one label.
     *
     * @param first Label that comes first.
     * @param second Label appended after [first].
     */
    fun join(
        first: String,
        second: String,
    ): String =
        first + second

    /**
     * Whether a user is currently signed in.
     */
    fun hasUser(): Boolean =
        authRepository.currentUser != null

    /**
     * Builds [FixtureViewModel] instances.
     */
    companion object {
        /**
         * Returns a [ViewModelProvider.Factory] that constructs [FixtureViewModel] from [appContainer].
         *
         * @param appContainer Source of the dependencies the ViewModel requires.
         */
        fun factory(appContainer: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FixtureViewModel(appContainer.authRepository) as T
            }
    }
}

/**
 * UI state for the fixture screen.
 *
 * @param isLoading Whether a request is in flight.
 */
data class FixtureUiState(val isLoading: Boolean = false)
