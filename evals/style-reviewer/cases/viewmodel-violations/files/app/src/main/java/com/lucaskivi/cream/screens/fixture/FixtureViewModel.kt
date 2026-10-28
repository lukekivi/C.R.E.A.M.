package com.lucaskivi.cream.screens.fixture

import androidx.lifecycle.ViewModel
import com.lucaskivi.cream.auth.AuthDataSourceImpl
import com.lucaskivi.cream.data.repository.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI state for the fixture screen.
 *
 * @param isLoading Whether a request is in flight.
 */
data class FixtureUiState(val isLoading: Boolean = false)

/**
 * Drives the fixture screen.
 *
 * @param authRepository Source of the signed-in user.
 */
class FixtureViewModel(private val authRepository: AuthRepository) : ViewModel() {

    companion object {
        /**
         * Name used in logs.
         */
        const val TAG = "FixtureViewModel"
    }

    /** Backing state. */
    private val _uiState = MutableStateFlow(FixtureUiState())

    /**
     * Current UI state.
     */
    val uiState: StateFlow<FixtureUiState> = _uiState.asStateFlow()

    /**
     * Joins two labels.
     */
    fun join(first: String, second: String): String = first + second

    /**
     * Trimmed, lowercased version of [label].
     *
     * @param label Raw label.
     */
    fun normalize(label: String): String =
        label.trim().lowercase()

    fun hasUser(): Boolean =
        authRepository.currentUser != null
}
