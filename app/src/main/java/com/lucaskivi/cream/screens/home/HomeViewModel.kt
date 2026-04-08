package com.lucaskivi.cream.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lucaskivi.cream.data.repository.auth.AuthRepository
import com.lucaskivi.cream.data.di.AppContainer

/**
 * ViewModel for the home screen.
 *
 * @param authRepository Used to sign the current user out.
 */
class HomeViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    fun signOut() = authRepository.signOut()

    companion object {
        /**
         * Returns a [ViewModelProvider.Factory] that constructs [HomeViewModel] from [appContainer].
         */
        fun factory(appContainer: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(appContainer.authRepository) as T
        }
    }
}
