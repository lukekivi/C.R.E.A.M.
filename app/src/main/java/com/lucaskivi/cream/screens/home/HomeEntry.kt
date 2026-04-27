package com.lucaskivi.cream.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import com.lucaskivi.cream.navigation.MainRoute

/**
 * Registers the [MainRoute.Home] entry.
 */
fun EntryProviderScope<MainRoute>.homeEntry() {
    entry<MainRoute.Home> {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Home — coming soon")
        }
    }
}
