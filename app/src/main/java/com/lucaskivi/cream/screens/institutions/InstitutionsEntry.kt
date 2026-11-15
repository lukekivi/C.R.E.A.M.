package com.lucaskivi.cream.screens.institutions

import androidx.navigation3.runtime.EntryProviderScope
import com.lucaskivi.cream.navigation.MainRoute

/**
 * Registers the [MainRoute.Institutions] entry.
 *
 * @param onNavigateBack Forwarded to [InstitutionsScreen] for its back action.
 * @param onAccountClick Forwarded to [InstitutionsScreen]; called with the tapped account's ID.
 */
fun EntryProviderScope<MainRoute>.institutionsEntry(
    onNavigateBack: () -> Unit,
    onAccountClick: (String) -> Unit,
) {
    entry<MainRoute.Institutions> {
        InstitutionsScreen(
            onNavigateBack = onNavigateBack,
            onAccountClick = onAccountClick,
        )
    }
}
