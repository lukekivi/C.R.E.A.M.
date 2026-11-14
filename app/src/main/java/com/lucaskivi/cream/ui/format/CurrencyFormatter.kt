package com.lucaskivi.cream.ui.format

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Formats amounts of money for display.
 */
object CurrencyFormatter {

    /**
     * Shown in place of an amount that isn't available.
     */
    const val MISSING_AMOUNT_PLACEHOLDER = "—"

    /**
     * Currency used when an amount's currency is unknown.
     */
    private const val FALLBACK_CURRENCY_CODE = "USD"

    /**
     * Formats [amount] as money in the currency named by [isoCurrencyCode], laid out for [locale]
     * (e.g. "$1,234.56" or "-€5.00"), with the currency's usual number of decimals. A null or
     * unrecognized code falls back to US dollars.
     *
     * @param amount Amount to format; negative amounts keep their sign.
     * @param isoCurrencyCode ISO 4217 code of the amount's currency, or null if unknown.
     * @param locale Locale that decides symbol placement, separators and digits.
     */
    fun format(
        amount: Double,
        isoCurrencyCode: String?,
        locale: Locale = Locale.getDefault(),
    ): String {
        val currency = currencyOrFallback(isoCurrencyCode)
        val formatter = NumberFormat.getCurrencyInstance(locale)
        formatter.currency = currency
        // Setting the currency doesn't reliably update the fraction digits on every platform, so
        // pin them to the currency's own (0 for yen, 2 for dollars). -1 means it has none.
        if (currency.defaultFractionDigits >= 0) {
            formatter.minimumFractionDigits = currency.defaultFractionDigits
            formatter.maximumFractionDigits = currency.defaultFractionDigits
        }
        return formatter.format(amount)
    }

    /**
     * Returns the currency named by [isoCurrencyCode], or US dollars when it is null or not a
     * recognized ISO 4217 code.
     *
     * @param isoCurrencyCode ISO 4217 currency code, or null if unknown.
     */
    private fun currencyOrFallback(
        isoCurrencyCode: String?,
    ): Currency {
        val recognized = isoCurrencyCode?.let { code ->
            try {
                Currency.getInstance(code)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        return recognized ?: Currency.getInstance(FALLBACK_CURRENCY_CODE)
    }
}
