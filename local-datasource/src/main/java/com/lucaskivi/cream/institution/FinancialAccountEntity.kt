package com.lucaskivi.cream.institution

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.FinancialAccountType

/**
 * Room entity representing a [FinancialAccount] stored in the `financial_account` table.
 *
 * Cascades deletes from the owning [LinkedInstitutionEntity] so that removing an institution
 * automatically removes all of its accounts.
 *
 * The [type] field stores the [FinancialAccountType] enum name (e.g. "DEPOSITORY"). Unknown
 * values encountered on read fall back to [FinancialAccountType.OTHER].
 *
 * @param accountId Primary key. Matches [FinancialAccount.accountId].
 * @param itemId Foreign key referencing [LinkedInstitutionEntity.itemId].
 * @param displayName Account name as provided by the institution.
 * @param mask Last four digits of the account number, or null if unavailable.
 * @param type [FinancialAccountType] enum name stored as a string.
 * @param subtype Provider sub-type string, or null if not returned.
 * @param balanceCurrent Current ledger balance, or null if unavailable.
 * @param balanceAvailable Available balance after pending transactions, or null if unavailable.
 * @param isoCurrencyCode ISO 4217 currency code, or null if not returned.
 */
@Entity(
    tableName = "financial_account",
    foreignKeys = [
        ForeignKey(
            entity = LinkedInstitutionEntity::class,
            parentColumns = ["itemId"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class FinancialAccountEntity(
    @PrimaryKey val accountId: String,
    val itemId: String,
    val displayName: String,
    val mask: String?,
    val type: String,
    val subtype: String?,
    val balanceCurrent: Double?,
    val balanceAvailable: Double?,
    val isoCurrencyCode: String?,
)

/**
 * Maps this entity to its [FinancialAccount] domain equivalent.
 *
 * Unknown [type] strings fall back to [FinancialAccountType.OTHER].
 */
internal fun FinancialAccountEntity.toDomain(): FinancialAccount =
    FinancialAccount(
        accountId = accountId,
        itemId = itemId,
        displayName = displayName,
        mask = mask,
        type = try {
            FinancialAccountType.valueOf(type)
        } catch (_: IllegalArgumentException) {
            FinancialAccountType.OTHER
        },
        subtype = subtype,
        balanceCurrent = balanceCurrent,
        balanceAvailable = balanceAvailable,
        isoCurrencyCode = isoCurrencyCode,
    )

/**
 * Maps this [FinancialAccount] to its [FinancialAccountEntity] database equivalent.
 *
 * [FinancialAccount.type] is stored as its enum name (e.g. "DEPOSITORY").
 */
internal fun FinancialAccount.toEntity(): FinancialAccountEntity =
    FinancialAccountEntity(
        accountId = accountId,
        itemId = itemId,
        displayName = displayName,
        mask = mask,
        type = type.name,
        subtype = subtype,
        balanceCurrent = balanceCurrent,
        balanceAvailable = balanceAvailable,
        isoCurrencyCode = isoCurrencyCode,
    )
