package com.lucaskivi.cream.ui.format

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Formats dates for display.
 */
class DateFormatter {

    /**
     * Formats [date] in the medium style of [locale] (e.g. "Nov 14, 2026").
     *
     * @param date Date to format.
     * @param locale Locale that decides the order, month names and separators.
     */
    fun format(
        date: LocalDate,
        locale: Locale = Locale.getDefault(),
    ): String =
        DateTimeFormatter
            .ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .format(date)
}
