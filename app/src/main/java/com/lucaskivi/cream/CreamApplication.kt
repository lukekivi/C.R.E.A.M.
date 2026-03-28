package com.lucaskivi.cream

import android.app.Application
import com.lucaskivi.cream.data.di.AppContainer
import com.lucaskivi.cream.data.di.AppContainerProvider

/**
 * Application subclass that acts as the app-scoped dependency container.
 *
 * Implements [AppContainerProvider] so that any composable can retrieve
 * repositories by casting [android.content.Context.getApplicationContext] to
 * [AppContainerProvider].
 */
class CreamApplication : Application(), AppContainerProvider {
    override lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
