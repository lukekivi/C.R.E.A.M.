package com.lucaskivi.cream.screens.loading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.entry
import com.lucaskivi.cream.navigation.MainRoute

/**
 * Registers the [MainRoute.Loading] entry — a full-screen spinner shown while auth
 * state is being resolved.
 */
fun EntryProviderBuilder<MainRoute>.loadingEntry() {
    entry<MainRoute.Loading> {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
