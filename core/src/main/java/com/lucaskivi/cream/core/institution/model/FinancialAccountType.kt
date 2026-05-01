package com.lucaskivi.cream.core.institution.model

/**
 * High-level classification of a financial account.
 *
 * Sub-types (e.g. "checking" vs "savings" within [DEPOSITORY]) are captured separately in
 * [FinancialAccount.subtype].
 */
enum class FinancialAccountType {
    /**
     * Revolving credit account such as a credit card.
     */
    CREDIT,

    /**
     * Cash-deposit account such as checking or savings.
     */
    DEPOSITORY,

    /**
     * Brokerage, retirement, or other investment account.
     */
    INVESTMENT,

    /**
     * Installment or revolving debt account such as a mortgage or student loan.
     */
    LOAN,

    /**
     * Any account type not covered by the above variants.
     */
    OTHER,
}
