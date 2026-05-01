package com.lucaskivi.cream.institution

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * Room [TypeConverter] that converts between [LocalDate] and its ISO-8601 string representation.
 *
 * Registered on [CreamDatabase] so it applies to all entities in the database.
 */
class LocalDateConverter {

    /**
     * Converts [date] to an ISO-8601 string (e.g. "2024-03-15"), or null if [date] is null.
     *
     * @param date The date to convert, or null.
     */
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? =
        date?.toString()

    /**
     * Parses [value] as an ISO-8601 date string and returns a [LocalDate], or null if [value]
     * is null.
     *
     * @param value ISO-8601 date string, or null.
     */
    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? =
        value?.let { LocalDate.parse(it) }
}
