package com.lucaskivi.cream.ui

import androidx.compose.runtime.compositionLocalOf
import com.lucaskivi.cream.data.di.AppContainer

/**
 * [androidx.compose.runtime.CompositionLocal] that provides [AppContainer] to the composition.
 *
 * Must be provided at the root of the composition tree via [androidx.compose.runtime.CompositionLocalProvider].
 */
val LocalAppContainer = compositionLocalOf<AppContainer> {
    error("No AppContainer provided")
}
