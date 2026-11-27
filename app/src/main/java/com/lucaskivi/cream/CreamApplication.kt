package com.lucaskivi.cream

import android.app.Application
import com.lucaskivi.cream.data.di.AppContainer
import com.lucaskivi.cream.data.di.AppContainerProvider

/**
 * Application subclass that acts as the app-scoped dependency container.
 *
 * Implements [AppContainerProvider] so that [MainActivity] can retrieve the container and
 * provide it to the composition through [com.lucaskivi.cream.ui.LocalAppContainer].
 */
class CreamApplication : Application(), AppContainerProvider {
    override lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
