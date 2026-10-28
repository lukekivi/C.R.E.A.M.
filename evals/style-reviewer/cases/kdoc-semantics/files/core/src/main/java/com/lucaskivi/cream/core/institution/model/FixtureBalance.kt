package com.lucaskivi.cream.core.institution.model

/**
 * An account balance as returned by Plaid's /accounts/balance/get endpoint.
 *
 * @param current Current balance in the account's currency.
 */
data class FixtureBalance(
    val current: Double,
)
