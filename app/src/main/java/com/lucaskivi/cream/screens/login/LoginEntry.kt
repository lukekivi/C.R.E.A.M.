package com.lucaskivi.cream.screens.login

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.entry
import com.lucaskivi.cream.navigation.MainRoute

/**
 * Registers the [MainRoute.Login] entry.
 *
 * @param onNavigateToRegister Forwarded to [LoginScreen] for the "Register" action.
 */
fun EntryProviderBuilder<MainRoute>.loginEntry(onNavigateToRegister: () -> Unit) {
    entry<MainRoute.Login> {
        LoginScreen(onNavigateToRegister = onNavigateToRegister)
    }
}
