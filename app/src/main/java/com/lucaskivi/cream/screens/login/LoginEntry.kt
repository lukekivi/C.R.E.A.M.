package com.lucaskivi.cream.screens.login

import androidx.navigation3.runtime.EntryProviderScope
import com.lucaskivi.cream.navigation.MainRoute

/**
 * Registers the [MainRoute.Login] entry.
 *
 * @param onNavigateToRegister Forwarded to [LoginScreen] for the "Register" action.
 */
fun EntryProviderScope<MainRoute>.loginEntry(onNavigateToRegister: () -> Unit) {
    entry<MainRoute.Login> {
        LoginScreen(onNavigateToRegister = onNavigateToRegister)
    }
}
