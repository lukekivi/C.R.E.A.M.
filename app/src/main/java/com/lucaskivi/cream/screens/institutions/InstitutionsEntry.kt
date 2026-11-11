package com.lucaskivi.cream.screens.institutions

import androidx.navigation3.runtime.EntryProviderScope
import com.lucaskivi.cream.navigation.MainRoute

/**
 * Registers the [MainRoute.Institutions] entry.
 *
 * @param onNavigateBack Forwarded to [InstitutionsScreen] for its back action.
 */
fun EntryProviderScope<MainRoute>.institutionsEntry(
    onNavigateBack: () -> Unit,
) {
    entry<MainRoute.Institutions> {
        InstitutionsScreen(onNavigateBack = onNavigateBack)
    }
}
