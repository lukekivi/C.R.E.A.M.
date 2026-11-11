package com.lucaskivi.cream.ui.format

import com.lucaskivi.cream.core.institution.model.Transaction
import java.util.Locale
import kotlin.math.abs

/**
 * Whether the transaction brings money into the account (a refund, deposit or payment received).
 */
internal val Transaction.isInflow: Boolean
    get() = this.amount < 0

/**
 * Returns the amount signed from the account holder's view: "-$x" for money out and "+$x" for
 * money in.
 *
 * @param fallbackCurrencyCode Currency used when the transaction doesn't name one.
 * @param locale Locale that decides symbol placement, separators and digits.
 */
internal fun Transaction.formattedSignedAmount(
    fallbackCurrencyCode: String?,
    locale: Locale = Locale.getDefault(),
): String {
    val sign = if (this.isInflow) "+" else "-"
    // The magnitude is formatted on its own, since the formatter would add its own minus sign.
    return sign + formatCurrency(abs(this.amount), this.isoCurrencyCode ?: fallbackCurrencyCode, locale)
}
