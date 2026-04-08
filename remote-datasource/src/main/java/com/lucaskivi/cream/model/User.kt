package com.lucaskivi.cream.model

/**
 * Domain representation of a signed-in user.
 *
 * @property uid Stable Firebase user ID.
 * @property email The user's email address, or null if not set.
 * @property displayName The user's display name, or null if not set.
 */
data class User(
    val uid: String,
    val email: String?,
    val displayName: String?
)
