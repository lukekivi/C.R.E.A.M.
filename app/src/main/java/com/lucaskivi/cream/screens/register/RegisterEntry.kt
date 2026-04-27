package com.lucaskivi.cream.screens.register

import androidx.navigation3.runtime.EntryProviderScope
import com.lucaskivi.cream.navigation.MainRoute

/**
 * Registers the [MainRoute.Register] entry.
 *
 * @param onNavigateBack Forwarded to [RegisterScreen] for the "Sign In" action.
 */
fun EntryProviderScope<MainRoute>.registerEntry(onNavigateBack: () -> Unit) {
    entry<MainRoute.Register> {
        RegisterScreen(onNavigateBack = onNavigateBack)
    }
}
