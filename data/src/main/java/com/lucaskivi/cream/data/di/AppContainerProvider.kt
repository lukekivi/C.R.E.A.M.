package com.lucaskivi.cream.data.di

/**
 * Implemented by the [android.app.Application] class to expose [AppContainer] app-wide.
 */
interface AppContainerProvider {
    val appContainer: AppContainer
}
