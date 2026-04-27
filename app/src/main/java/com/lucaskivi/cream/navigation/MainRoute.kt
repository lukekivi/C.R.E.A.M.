package com.lucaskivi.cream.navigation

/**
 * Navigation destinations for the main back stack.
 *
 * [Loading] is the initial placeholder while auth state is being resolved.
 */
sealed class MainRoute {
    /**
     * Initial destination shown while the app determines whether a user is signed in.
     */
    data object Loading : MainRoute()

    /**
     * Sign-in destination for existing users.
     */
    data object Login : MainRoute()

    /**
     * Account-creation destination for new users.
     */
    data object Register : MainRoute()

    /**
     * Primary destination shown to a signed-in user.
     */
    data object Home : MainRoute()
}
