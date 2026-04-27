package com.lucaskivi.cream.data.di

/**
 * Implemented by the [android.app.Application] class to expose [AppContainer] app-wide.
 */
interface AppContainerProvider {
    /**
     * The app-wide [AppContainer] singleton.
     */
    val appContainer: AppContainer
}
