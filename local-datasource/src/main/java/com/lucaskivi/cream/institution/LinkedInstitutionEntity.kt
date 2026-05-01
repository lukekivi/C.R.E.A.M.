package com.lucaskivi.cream.institution

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lucaskivi.cream.core.institution.model.LinkedInstitution

/**
 * Room entity representing a [LinkedInstitution] stored in the `linked_institution` table.
 *
 * @param itemId Primary key. Matches [LinkedInstitution.itemId].
 * @param institutionId Provider's identifier for the institution.
 * @param institutionName Human-readable name of the institution.
 */
@Entity(tableName = "linked_institution")
data class LinkedInstitutionEntity(
    @PrimaryKey val itemId: String,
    val institutionId: String,
    val institutionName: String,
)
