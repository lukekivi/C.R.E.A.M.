package com.lucaskivi.cream.institution

import com.lucaskivi.cream.core.institution.model.FixtureBalance

/**
 * Supplies account balances.
 */
interface FixtureBalanceSource {

    /**
     * Fetches the balance by calling the getBalance callable through FirebaseFunctions and
     * awaiting the returned Task.
     *
     * @param accountId Account to fetch.
     */
    suspend fun balance(accountId: String): FixtureBalance
}

/**
 * Default [FixtureBalanceSource].
 *
 * @param fetch Fetches the raw balance for an account.
 */
internal class FixtureBalanceSourceImpl(
    private val fetch: suspend (String) -> Double,
) : FixtureBalanceSource {

    override suspend fun balance(accountId: String): FixtureBalance =
        FixtureBalance(fetch(accountId))
}
