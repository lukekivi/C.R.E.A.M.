package com.lucaskivi.cream.screens.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.lucaskivi.cream.navigation.MainRoute
import com.lucaskivi.cream.screens.home.homeEntry
import com.lucaskivi.cream.screens.loading.loadingEntry
import com.lucaskivi.cream.screens.login.loginEntry
import com.lucaskivi.cream.screens.register.registerEntry
import com.lucaskivi.cream.ui.LocalAppContainer

/**
 * Root navigation graph for the app.
 *
 * Observes auth state via [MainViewModel] and redirects to [MainRoute.Login] or
 * [MainRoute.Home] once initialization is complete.
 */
@Composable
fun MainNavGraph() {
    val appContainer = LocalAppContainer.current
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModel.factory(appContainer)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backStack = remember { mutableStateListOf<MainRoute>(MainRoute.Loading) }

    LaunchedEffect(uiState.isInitialized, uiState.isAuthenticated) {
        if (!uiState.isInitialized) return@LaunchedEffect
        val destination = if (uiState.isAuthenticated) MainRoute.Home else MainRoute.Login
        backStack.clear()
        backStack.add(destination)
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            loadingEntry()
            loginEntry(onNavigateToRegister = { backStack.add(MainRoute.Register) })
            registerEntry(onNavigateBack = { backStack.removeLastOrNull() })
            homeEntry()
        }
    )
}
