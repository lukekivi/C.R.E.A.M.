package com.lucaskivi.cream.institution

import androidx.room.Embedded
import androidx.room.Relation
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts

/**
 * Intermediate Room result type that bundles a [LinkedInstitutionEntity] with its associated
 * [FinancialAccountEntity] list via a [Relation] query.
 *
 * Used only within this module to back [InstitutionDao.observeInstitutionsWithAccounts].
 * Callers receive the mapped [LinkedInstitutionWithAccounts] domain type.
 *
 * @param institution The linked institution row.
 * @param accounts All financial account rows belonging to [institution].
 */
data class InstitutionWithAccountsEntity(
    @Embedded val institution: LinkedInstitutionEntity,
    @Relation(
        parentColumn = "itemId",
        entityColumn = "itemId",
    )
    val accounts: List<FinancialAccountEntity>,
)

/**
 * Maps this relation result to its [LinkedInstitutionWithAccounts] domain equivalent.
 */
internal fun InstitutionWithAccountsEntity.toDomain(): LinkedInstitutionWithAccounts =
    LinkedInstitutionWithAccounts(
        institution = institution.toDomain(),
        accounts = accounts.map { it.toDomain() },
    )
