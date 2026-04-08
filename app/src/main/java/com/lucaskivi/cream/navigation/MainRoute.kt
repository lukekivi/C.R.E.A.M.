package com.lucaskivi.cream.navigation

/**
 * Navigation destinations for the main back stack.
 *
 * [Loading] is the initial placeholder while auth state is being resolved.
 */
sealed class MainRoute {
    data object Loading : MainRoute()
    data object Login : MainRoute()
    data object Register : MainRoute()
    data object Home : MainRoute()
}
