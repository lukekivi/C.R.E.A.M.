package com.lucaskivi.cream.screens.home

import androidx.navigation3.runtime.EntryProviderScope
import com.lucaskivi.cream.navigation.MainRoute

/**
 * Registers the [MainRoute.Home] entry.
 */
fun EntryProviderScope<MainRoute>.homeEntry() {
    entry<MainRoute.Home> {
        HomeScreen()
    }
}
