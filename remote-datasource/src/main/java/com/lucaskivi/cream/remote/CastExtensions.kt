package com.lucaskivi.cream.remote

/**
 * Casts [this] to [Map]<[String], [Any]?>.
 *
 * Throws [ClassCastException] if the value is not a map.
 */
@Suppress("UNCHECKED_CAST")
internal fun Any?.asMap(): Map<String, Any?> =
    this as Map<String, Any?>

/**
 * Casts [this] to [List]<[Map]<[String], [Any]?>>.
 *
 * Throws [ClassCastException] if the value is not a list. Element types are not checked until
 * each element is read.
 */
@Suppress("UNCHECKED_CAST")
internal fun Any?.asMapList(): List<Map<String, Any?>> =
    this as List<Map<String, Any?>>
