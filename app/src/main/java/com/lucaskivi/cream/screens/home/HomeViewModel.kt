package com.lucaskivi.cream.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lucaskivi.cream.data.di.AppContainer
import com.lucaskivi.cream.data.repository.auth.AuthRepository
import com.lucaskivi.cream.data.repository.institution.InstitutionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the home screen.
 *
 * Tracks the currently signed-in user so the UI can render a welcome/profile
 * section, and exposes [signOut] to terminate the session and clear the user's cached data.
 *
 * @param authRepository Source of the signed-in user and the sign-out action.
 * @param institutionRepository Owner of the cached institution data cleared on sign-out.
 * @param uiTransformer Converts the signed-in user into what the screen shows.
 */
class HomeViewModel(
    private val authRepository: AuthRepository,
    private val institutionRepository: InstitutionRepository,
    private val uiTransformer: HomeUiTransformer,
) : ViewModel() {

    /**
     * Mutable backing flow for [uiState].
     */
    private val mutableUiState = MutableStateFlow(HomeUiState(uiTransformer.toProfileSectionData(null)))

    /**
     * Observable UI state of the home screen.
     */
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.authStateFlow.collect { user ->
                val profile = uiTransformer.toProfileSectionData(user)
                mutableUiState.update {
                    it.copy(
                        profile = profile,
                    )
                }
            }
        }
    }

    /**
     * Clears the cached institution data, then signs the current user out so the next user
     * never sees it. Sign-out proceeds even if clearing fails.
     */
    fun signOut() {
        viewModelScope.launch {
            institutionRepository.clearLocalCache()
            authRepository.signOut()
        }
    }

    companion object {
        /**
         * Returns a [ViewModelProvider.Factory] that constructs [HomeViewModel] from [appContainer].
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
                    HomeViewModel(
                        authRepository = appContainer.authRepository,
                        institutionRepository = appContainer.institutionRepository,
                        uiTransformer = HomeUiTransformer(),
                    ) as T
            }
    }
}

/**
 * UI state for the home screen.
 *
 * @property profile What the profile section shows.
 */
data class HomeUiState(
    val profile: ProfileSectionData,
)
