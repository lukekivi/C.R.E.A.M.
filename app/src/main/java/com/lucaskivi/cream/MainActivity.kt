package com.lucaskivi.cream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.lucaskivi.cream.data.di.AppContainerProvider
import com.lucaskivi.cream.screens.main.MainNavGraph
import com.lucaskivi.cream.ui.LocalAppContainer
import com.lucaskivi.cream.ui.theme.CREAMTheme

/**
 * Entry point activity. Hosts the root Compose content.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (applicationContext as AppContainerProvider).appContainer
        setContent {
            CREAMTheme {
                CompositionLocalProvider(LocalAppContainer provides appContainer) {
                    MainNavGraph()
                }
            }
        }
    }
}
