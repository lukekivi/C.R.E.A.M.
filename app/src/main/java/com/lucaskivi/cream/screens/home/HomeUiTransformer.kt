package com.lucaskivi.cream.screens.home

import com.lucaskivi.cream.data.model.User

/**
 * Converts domain models into what the home screen shows.
 */
class HomeUiTransformer {

    /**
     * Returns what [ProfileSection] shows for [user]. The initial and name come from the display
     * name, falling back to the email, and then to placeholders when neither is set.
     *
     * @param user The signed-in user, or null while auth state is initializing or after sign-out.
     */
    fun toProfileSectionData(
        user: User?,
    ): ProfileSectionData {
        val initial = (user?.displayName?.firstOrNull() ?: user?.email?.firstOrNull())
            ?.uppercaseChar()
            ?.toString()
            ?: MISSING_INITIAL
        val name = user?.displayName ?: user?.email ?: MISSING_NAME
        return ProfileSectionData(
            initial = initial,
            name = name,
        )
    }

    companion object {
        /**
         * Avatar initial shown when the user has neither a display name nor an email.
         */
        private const val MISSING_INITIAL = "?"

        /**
         * Welcome name shown when the user has neither a display name nor an email.
         */
        private const val MISSING_NAME = "there"
    }
}
