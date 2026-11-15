package com.lucaskivi.cream.screens.accountdetail

import androidx.navigation3.runtime.EntryProviderScope
import com.lucaskivi.cream.navigation.MainRoute

/**
 * Registers the [MainRoute.AccountDetail] entry.
 *
 * @param onNavigateBack Called with the entry's own route when its screen is left, so the
 *   caller removes that entry rather than whatever is on top of the back stack.
 */
fun EntryProviderScope<MainRoute>.accountDetailEntry(
    onNavigateBack: (MainRoute.AccountDetail) -> Unit,
) {
    entry<MainRoute.AccountDetail> { route ->
        AccountDetailScreen(
            accountId = route.accountId,
            onNavigateBack = { onNavigateBack(route) },
        )
    }
}
