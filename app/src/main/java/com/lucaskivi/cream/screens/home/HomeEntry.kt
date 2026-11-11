package com.lucaskivi.cream.screens.home

import androidx.navigation3.runtime.EntryProviderScope
import com.lucaskivi.cream.navigation.MainRoute

/**
 * Registers the [MainRoute.Home] entry.
 *
 * @param onNavigateToInstitutions Forwarded to [HomeScreen] for the "Institutions" action.
 */
fun EntryProviderScope<MainRoute>.homeEntry(
    onNavigateToInstitutions: () -> Unit,
) {
    entry<MainRoute.Home> {
        HomeScreen(onNavigateToInstitutions = onNavigateToInstitutions)
    }
}
